package com.yantrago.gateway.tcp.concox;

import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.gateway.tcp.ProtocolHandler;
import com.yantrago.gateway.model.GpsIngestRequest;
import com.yantrago.gateway.queue.DeviceEventProducer;
import com.yantrago.gateway.service.DeviceHeartbeatService;
import com.yantrago.gateway.service.DeviceMappingCacheService;
import com.yantrago.gateway.service.GpsIngestService;
import com.yantrago.gateway.service.VehicleCommandService;
import com.yantrago.shared.queue.DeviceEventMessage;
import com.yantrago.shared.queue.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConcoxV5ProtocolHandler implements ProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(ConcoxV5ProtocolHandler.class);

    private final Map<String, String> clientImeiMap = new ConcurrentHashMap<>();
    private final DeviceConnectionRegistry connectionRegistry;
    private final VehicleCommandService vehicleCommandService;
    private final GpsIngestService gpsIngestService;
    private final DeviceMappingCacheService deviceMappingCacheService;
    private final DeviceEventProducer deviceEventProducer;
    private final DeviceHeartbeatService deviceHeartbeatService;
    private final AtomicInteger serialCounter = new AtomicInteger(1);

    public ConcoxV5ProtocolHandler(
            DeviceConnectionRegistry connectionRegistry,
            @Lazy VehicleCommandService vehicleCommandService,
            @Lazy GpsIngestService gpsIngestService,
            DeviceMappingCacheService deviceMappingCacheService,
            DeviceEventProducer deviceEventProducer,
            DeviceHeartbeatService deviceHeartbeatService
    ) {
        this.connectionRegistry = connectionRegistry;
        this.vehicleCommandService = vehicleCommandService;
        this.gpsIngestService = gpsIngestService;
        this.deviceMappingCacheService = deviceMappingCacheService;
        this.deviceEventProducer = deviceEventProducer;
        this.deviceHeartbeatService = deviceHeartbeatService;
        log.info("[V5] Protocol handler initialized with DB-backed device mapping cache");
    }

    @Override
    public String getProtocolName() {
        return "CONCOX_V5";
    }

    @Override
    public boolean canHandle(byte[] firstBytes) {
        if (firstBytes == null || firstBytes.length < 2) {
            return false;
        }
        return (firstBytes[0] == 0x78 && firstBytes[1] == 0x78) ||
               (firstBytes[0] == 0x79 && firstBytes[1] == 0x79);
    }

    @Override
    public String getImeiForClient(String clientId) {
        return clientImeiMap.get(clientId);
    }

    @Override
    public void removeClient(String clientId) {
        String imei = clientImeiMap.remove(clientId);
        if (imei != null) {
            deviceHeartbeatService.markOffline(imei);
            publishDeviceEvent(imei, DeviceEventMessage.EVENT_DISCONNECT);
        }
    }

    @Override
    public byte[] handlePacket(byte[] packet, String clientId) {
        if (packet.length < 5) return null;

        boolean extended = packet[0] == 0x79;
        int protocolOffset = extended ? 4 : 3;
        int protocolNumber = packet[protocolOffset] & 0xFF;

        log.debug("[V5] Received packet from {}, Protocol: 0x{}", clientId, String.format("%02X", protocolNumber));

        switch (protocolNumber) {
            case 0x01: // Login packet
                return handleLoginPacket(packet, clientId);
            case 0x13: // Heartbeat packet
                return handleHeartbeatPacket(packet, clientId);
            case 0x21: // Command reply
                return handleCommandReplyPacket(packet, clientId);
            case 0x22: // GPS Location packet (UTC)
                return handleLocationPacket(packet, clientId);
            case 0x26: // Alarm packet
                return handleAlarmPacket(packet, clientId);
            case 0x94: // Information transmission
                return handleInfoPacket(packet, clientId);
            default:
                log.warn("[V5] Unknown protocol: 0x{}", Integer.toHexString(protocolNumber));
                return null;
        }
    }

    private byte[] handleLoginPacket(byte[] packet, String clientId) {
        // Extract IMEI (8 bytes starting at offset 4 for standard packet)
        boolean extended = packet[0] == 0x79;
        int imeiOffset = extended ? 5 : 4;

        StringBuilder imei = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int b = packet[imeiOffset + i] & 0xFF;
            imei.append(String.format("%02X", b));
        }
        // Format: 0x08 0x67 0x01 0x00 0x70 0x11 0x34 0x52 -> 867010070113452
        String imeiNumber = convertBcdToImei(imei.toString());
        log.info("[V5] Login from IMEI: {}", imeiNumber);
        clientImeiMap.put(clientId, imeiNumber);

        // Record heartbeat and publish LOGIN event to backend
        deviceHeartbeatService.recordHeartbeat(imeiNumber);
        publishDeviceEvent(imeiNumber, DeviceEventMessage.EVENT_LOGIN);

        // Send login response (required!)
        return buildResponse(packet, (byte) 0x01);
    }

    private byte[] handleHeartbeatPacket(byte[] packet, String clientId) {
        log.info("[V5] Heartbeat from: {}", clientId);
        // Extract terminal info byte
        boolean extended = packet[0] == 0x79;
        int infoOffset = extended ? 5 : 4;

        if (packet.length > infoOffset) {
            int terminalInfo = packet[infoOffset] & 0xFF;
            boolean accOn = (terminalInfo & 0x02) != 0;
            boolean charging = (terminalInfo & 0x04) != 0;
            boolean gpsTracking = (terminalInfo & 0x40) != 0;
            boolean fuelCutOff = (terminalInfo & 0x80) != 0;

            // Parse Voltage Level byte (infoOffset + 1) — internal battery 7-level enum
            Integer batteryPct = null;
            if (packet.length > infoOffset + 1) {
                int voltageLevel = packet[infoOffset + 1] & 0xFF;
                batteryPct = BatteryLevelMapper.toPercentage(voltageLevel);
            }

            // Parse GSM Signal Level byte (infoOffset + 2)
            Integer gsmSignal = null;
            if (packet.length > infoOffset + 2) {
                int gsmLevel = packet[infoOffset + 2] & 0xFF;
                gsmSignal = BatteryLevelMapper.toGsmSignal(gsmLevel);
            }

            log.debug("[V5] Heartbeat: ACC={}, Charging={}, GPS={}, FuelCut={}, Battery={}%, GSM={}",
                accOn ? "ON" : "OFF", charging ? "Yes" : "No",
                gpsTracking ? "ON" : "OFF", fuelCutOff ? "YES" : "NO",
                batteryPct, gsmSignal);

            // Sync lock state from heartbeat Bit7
            String imei = clientImeiMap.get(clientId);
            if (imei != null) {
                try {
                    vehicleCommandService.updateLockStateFromHeartbeat(imei, fuelCutOff);
                    deviceHeartbeatService.recordHeartbeat(imei);
                    publishDeviceEvent(imei, DeviceEventMessage.EVENT_HEARTBEAT);

                    // Forward battery/GSM telemetry from heartbeat — heartbeats are the
                    // primary source of battery level data since 0x22 GPS packets do not
                    // include voltage level or terminal info bytes.
                    forwardTelemetryFromHeartbeat(imei, batteryPct, gsmSignal, charging);
                } catch (Exception e) {
                    log.error("[V5] Failed to sync lock state from heartbeat: {}", e.getMessage());
                }
            }
        }

        return buildResponse(packet, (byte) 0x13);
    }

    private byte[] handleCommandReplyPacket(byte[] packet, String clientId) {
        log.info("[V5] Command reply from: {}", clientId);
        try {
            boolean extended = packet[0] == 0x79;
            int dataOffset = extended ? 5 : 4;

            // BR05 0x21 reply format:
            //   Server flags: 4 bytes
            //   Encoding: 1 byte (0x01=ASCII, 0x02=UTF16-BE)
            //   Content: M bytes (reply text)
            //   Serial number: 2 bytes
            //   CRC: 2 bytes
            //   Stop: 0x0D 0x0A
            int serverFlags = ((packet[dataOffset] & 0xFF) << 24) |
                              ((packet[dataOffset + 1] & 0xFF) << 16) |
                              ((packet[dataOffset + 2] & 0xFF) << 8) |
                              (packet[dataOffset + 3] & 0xFF);
            int encoding = packet[dataOffset + 4] & 0xFF;

            // Content is between (dataOffset+5) and (serialOffset - 2)
            int serialOffset = packet.length - 4; // Before CRC(2) + Stop(2)
            int contentEnd = serialOffset - 2; // Before serial number(2)
            StringBuilder resultText = new StringBuilder();
            if (encoding == 0x01) {
                // ASCII encoding
                for (int i = dataOffset + 5; i < contentEnd; i++) {
                    resultText.append((char) (packet[i] & 0xFF));
                }
            } else {
                // UTF16-BE or unknown — extract raw bytes as hex
                for (int i = dataOffset + 5; i < contentEnd; i++) {
                    resultText.append(String.format("%02X", packet[i] & 0xFF));
                }
            }

            // The presence of a 0x21 reply itself indicates the device processed the command.
            // A non-empty reply with status text means success.
            boolean success = true;
            boolean fuelCutOff = false;
            // Try to detect fuel/relay state from the reply text
            String text = resultText.toString();
            if (text.contains("Oil and electricity disconnected") || text.contains("DYD=01") || text.contains("oil disconnect")) {
                fuelCutOff = true;
            }

            log.info("[V5] Command reply: serverFlags=0x{} encoding=0x{} success={} fuelCutOff={} resultText={}",
                    String.format("%08X", serverFlags), String.format("%02X", encoding),
                    success, fuelCutOff, text.length() > 100 ? text.substring(0, 100) + "..." : text);

            // Process the reply in the command service
            String imei = clientImeiMap.get(clientId);
            if (imei != null) {
                try {
                    vehicleCommandService.processCommandReply(imei, success, text, fuelCutOff);
                } catch (Exception e) {
                    log.error("[V5] Failed to process command reply: {}", e.getMessage());
                }
            }

            return buildResponse(packet, (byte) 0x21);
        } catch (Exception e) {
            log.error("[V5] Error parsing command reply", e);
            return null;
        }
    }

    /**
     * Builds a 0x80 Online Instruction packet per the BR05 protocol.
     *
     * Packet structure (matching protocol doc example for "sos#"):
     *   78 78 0E 80 08 00 00 00 00 73 6F 73 23 00 01 6D 6A 0D 0A
     *
     *   Start: 0x78 0x78
     *   Length: 1 byte = protocol(1) + instruction_len(1) + server_flags(4) + content(N) + serial(2) + crc(2)
     *   Protocol: 0x80
     *   Instruction length: 1 byte = server_flags(4) + content(N)
     *   Server flags: 4 bytes (binary, returned by terminal in reply)
     *   Command content: N bytes (ASCII, compatible with SMS commands)
     *   Serial number: 2 bytes
     *   CRC: 2 bytes
     *   Stop: 0x0D 0x0A
     *
     * @param commandContent the ASCII command string (e.g. "RELAY,0#" for relay ON, "RELAY,1#" for relay OFF)
     * @return the complete packet bytes ready to send over TCP
     */
    public byte[] buildCommandPacket(String commandContent) {
        byte[] contentBytes = commandContent.getBytes(StandardCharsets.US_ASCII);
        int serial = serialCounter.getAndIncrement() & 0xFFFF;

        // Instruction length = server_flags(4) + content(N)
        int instructionLen = 4 + contentBytes.length;

        // Length = protocol(1) + instruction_len(1) + server_flags(4) + content(N) + serial(2) + crc(2)
        int length = 1 + 1 + instructionLen + 2 + 2;

        // Total packet = start(2) + length_byte(1) + length + stop(2)
        byte[] packet = new byte[2 + 1 + length + 2];

        packet[0] = 0x78;
        packet[1] = 0x78;
        packet[2] = (byte) length;
        packet[3] = (byte) 0x80; // Protocol number
        packet[4] = (byte) instructionLen; // Instruction length (server_flags + content)

        int pos = 5;
        // Server flags: 4 bytes (all zeros — terminal returns these in reply)
        packet[pos++] = 0x00;
        packet[pos++] = 0x00;
        packet[pos++] = 0x00;
        packet[pos++] = 0x00;
        // Command content (ASCII)
        System.arraycopy(contentBytes, 0, packet, pos, contentBytes.length);
        pos += contentBytes.length;
        // Serial number
        packet[pos++] = (byte) ((serial >> 8) & 0xFF);
        packet[pos++] = (byte) (serial & 0xFF);

        // CRC-ITU over bytes from index 2 to pos (exclusive)
        int crc = calculateCrc16(packet, 2, pos);
        packet[pos++] = (byte) ((crc >> 8) & 0xFF);
        packet[pos++] = (byte) (crc & 0xFF);
        // Stop bytes
        packet[pos++] = 0x0D;
        packet[pos++] = 0x0A;

        return packet;
    }

    private byte[] handleLocationPacket(byte[] packet, String clientId) {
        try {
            boolean extended = packet[0] == 0x79;
            int dataOffset = extended ? 5 : 4;

            // Parse GPS location packet
            // Date Time: 6 bytes (YY MM DD HH MM SS)
            int year = packet[dataOffset] & 0xFF;
            int month = packet[dataOffset + 1] & 0xFF;
            int day = packet[dataOffset + 2] & 0xFF;
            int hour = packet[dataOffset + 3] & 0xFF;
            int minute = packet[dataOffset + 4] & 0xFF;
            int second = packet[dataOffset + 5] & 0xFF;

            // GPS Satellites: 1 byte
            int gpsInfo = packet[dataOffset + 6] & 0xFF;
            int satelliteCount = gpsInfo & 0x0F;

            // Latitude: 4 bytes (divide by 1800000)
            int latRaw = ((packet[dataOffset + 7] & 0xFF) << 24) |
                        ((packet[dataOffset + 8] & 0xFF) << 16) |
                        ((packet[dataOffset + 9] & 0xFF) << 8) |
                        (packet[dataOffset + 10] & 0xFF);

            // Longitude: 4 bytes (divide by 1800000)
            int lngRaw = ((packet[dataOffset + 11] & 0xFF) << 24) |
                        ((packet[dataOffset + 12] & 0xFF) << 16) |
                        ((packet[dataOffset + 13] & 0xFF) << 8) |
                        (packet[dataOffset + 14] & 0xFF);

            // Speed: 1 byte
            int speed = packet[dataOffset + 15] & 0xFF;

            // Course & Status: 2 bytes (big-endian)
            // Bits 0-9: course (10 bits)
            // Bit 10: GPS located (1 = yes)
            // Bit 11: E/W (0 = East, 1 = West)
            // Bit 12: N/S (1 = North, 0 = South) — BR05 convention
            int courseStatus = ((packet[dataOffset + 16] & 0xFF) << 8) |
                              (packet[dataOffset + 17] & 0xFF);

            int course = courseStatus & 0x03FF; // 10 bits for course
            boolean gpsLocated = (courseStatus & 0x0400) != 0;
            boolean eastLongitude = (courseStatus & 0x0800) == 0; // 0 = East, 1 = West
            boolean northLatitude = (courseStatus & 0x1000) != 0; // 1 = North, 0 = South (BR05 convention)

            double latitude = latRaw / 1800000.0;
            double longitude = lngRaw / 1800000.0;

            if (!northLatitude) latitude = -latitude;
            if (!eastLongitude) longitude = -longitude;

            // LBS Data
            int mcc = ((packet[dataOffset + 18] & 0xFF) << 8) | (packet[dataOffset + 19] & 0xFF);
            int mnc = packet[dataOffset + 20] & 0xFF;
            int lac = ((packet[dataOffset + 21] & 0xFF) << 8) | (packet[dataOffset + 22] & 0xFF);
            int cellId = ((packet[dataOffset + 23] & 0xFF) << 16) |
                        ((packet[dataOffset + 24] & 0xFF) << 8) |
                        (packet[dataOffset + 25] & 0xFF);

            // ACC status (0x22 packet includes ACC byte but NOT Terminal Info)
            int acc = packet[dataOffset + 26] & 0xFF;
            boolean ignitionOn = (acc & 0x01) != 0;

            // Per BR05 protocol: the 0x22 GPS positioning packet ends with
            // ACC (1 byte) + Reporting Mode (1 byte). It does NOT include
            // Terminal Information, Voltage Level, or GSM Signal bytes.
            // Battery/charging/SOS/vibration/relay status are only available
            // from heartbeat (0x13) and alarm (0x26) packets.
            // Previously this code read terminalInfo from dataOffset+27 which
            // was past the 0x22 packet boundary — that has been removed.
            Boolean externalPowerConnected = null;
            Boolean sosPressed = null;
            Boolean vibrationDetected = null;
            Boolean relayOn = null;

            log.debug("[V5] GPS: {}, {} | Speed: {} km/h | Satellites: {} | GPS Located: {}",
                String.format("%.6f", latitude), String.format("%.6f", longitude), speed, satelliteCount, gpsLocated ? "Yes" : "No");
            log.debug("[V5] Time: 20{}-{}-{} {}:{}:{} | Course: {}°",
                String.format("%02d", year), String.format("%02d", month), String.format("%02d", day),
                String.format("%02d", hour), String.format("%02d", minute), String.format("%02d", second), course);
            log.debug("[V5] Ignition: {} | Terminal info not available in 0x22 packet",
                ignitionOn ? "ON" : "OFF");

            // Forward to HTTP API with hardware status fields
            if (gpsLocated) {
                String imei = clientImeiMap.get(clientId);
                forwardToHttpApi(latitude, longitude, speed, course, year, month, day, hour, minute, second, imei,
                        ignitionOn, externalPowerConnected, sosPressed, vibrationDetected, relayOn,
                        satelliteCount, mcc, mnc);
            }

            return null; // Location packet response is optional
        } catch (Exception e) {
            log.error("[V5] Error parsing location packet", e);
            return null;
        }
    }

    private byte[] handleAlarmPacket(byte[] packet, String clientId) {
        log.info("[V5] Alarm packet received from: {}", clientId);
        try {
            boolean extended = packet[0] == 0x79;
            int dataOffset = extended ? 5 : 4;

            // Per BR05 spec, the 0x26 alarm packet requires at least 32 bytes of
            // information content (datetime 6 + sats 1 + lat 4 + lng 4 + speed 1 +
            // course 2 + lbsLen 1 + mcc 2 + mnc 1 + lac 2 + cellId 3 + terminalInfo 1 +
            // voltageLevel 1 + gsmSignal 1 + alarmLang 2) after the header.
            // Validate minimum length before parsing to avoid ArrayIndexOutOfBoundsException.
            int minInfoLen = 32;
            if (packet.length < dataOffset + minInfoLen) {
                log.warn("[V5] Alarm packet too short: {} bytes (need at least {}), skipping",
                        packet.length, dataOffset + minInfoLen);
                return null;
            }

            // Date Time: 6 bytes (YY MM DD HH MM SS)
            int year = packet[dataOffset] & 0xFF;
            int month = packet[dataOffset + 1] & 0xFF;
            int day = packet[dataOffset + 2] & 0xFF;
            int hour = packet[dataOffset + 3] & 0xFF;
            int minute = packet[dataOffset + 4] & 0xFF;
            int second = packet[dataOffset + 5] & 0xFF;

            // GPS Satellites: 1 byte
            int gpsInfo = packet[dataOffset + 6] & 0xFF;
            int satelliteCount = gpsInfo & 0x0F;

            // Latitude: 4 bytes (divide by 1800000)
            int latRaw = ((packet[dataOffset + 7] & 0xFF) << 24) |
                        ((packet[dataOffset + 8] & 0xFF) << 16) |
                        ((packet[dataOffset + 9] & 0xFF) << 8) |
                        (packet[dataOffset + 10] & 0xFF);

            // Longitude: 4 bytes (divide by 1800000)
            int lngRaw = ((packet[dataOffset + 11] & 0xFF) << 24) |
                        ((packet[dataOffset + 12] & 0xFF) << 16) |
                        ((packet[dataOffset + 13] & 0xFF) << 8) |
                        (packet[dataOffset + 14] & 0xFF);

            // Speed: 1 byte
            int speed = packet[dataOffset + 15] & 0xFF;

            // Course & Status: 2 bytes
            int courseStatus = ((packet[dataOffset + 16] & 0xFF) << 8) |
                              (packet[dataOffset + 17] & 0xFF);
            int course = courseStatus & 0x03FF;
            boolean gpsLocated = (courseStatus & 0x0400) != 0;
            boolean eastLongitude = (courseStatus & 0x0800) == 0;
            boolean northLatitude = (courseStatus & 0x1000) != 0;

            double latitude = latRaw / 1800000.0;
            double longitude = lngRaw / 1800000.0;
            if (!northLatitude) latitude = -latitude;
            if (!eastLongitude) longitude = -longitude;

            // LBS Data
            int mcc = ((packet[dataOffset + 19] & 0xFF) << 8) | (packet[dataOffset + 20] & 0xFF);
            int mnc = packet[dataOffset + 21] & 0xFF;
            int lac = ((packet[dataOffset + 22] & 0xFF) << 8) | (packet[dataOffset + 23] & 0xFF);
            int cellId = ((packet[dataOffset + 24] & 0xFF) << 16) |
                        ((packet[dataOffset + 25] & 0xFF) << 8) |
                        (packet[dataOffset + 26] & 0xFF);

            // Terminal Information: 1 byte (at dataOffset + 27)
            int terminalInfo = packet[dataOffset + 27] & 0xFF;
            boolean ignitionOn = (terminalInfo & 0x02) != 0;
            boolean charging = (terminalInfo & 0x04) != 0;
            boolean sosPressed = (terminalInfo & 0x01) != 0;
            boolean vibrationDetected = (terminalInfo & 0x08) != 0;
            boolean relayOn = (terminalInfo & 0x80) != 0;

            // Voltage Level: 1 byte (at dataOffset + 28) — internal battery 7-level enum
            int voltageLevel = packet[dataOffset + 28] & 0xFF;
            Integer batteryPct = BatteryLevelMapper.toPercentage(voltageLevel);

            // GSM Signal Level: 1 byte (at dataOffset + 29)
            int gsmLevel = packet[dataOffset + 29] & 0xFF;
            Integer gsmSignal = BatteryLevelMapper.toGsmSignal(gsmLevel);

            // Alarm type/language: 2 bytes (at dataOffset + 30)
            int alarmCode = packet[dataOffset + 30] & 0xFF;
            int language = packet[dataOffset + 31] & 0xFF;

            log.info("[V5] Alarm: code=0x{}, Battery={}%, GSM={}, Charging={}, GPS={}, SOS={}, Vibration={}",
                String.format("%02X", alarmCode), batteryPct, gsmSignal,
                charging ? "Yes" : "No", gpsLocated ? "Yes" : "No",
                sosPressed ? "YES" : "no", vibrationDetected ? "YES" : "no");
            log.debug("[V5] Alarm GPS: {}, {} | Speed: {} km/h | Course: {}°",
                String.format("%.6f", latitude), String.format("%.6f", longitude), speed, course);

            String imei = clientImeiMap.get(clientId);
            if (imei != null) {
                // Forward telemetry (battery + GSM) from alarm packet
                forwardTelemetryFromHeartbeat(imei, batteryPct, gsmSignal, charging);

                // Forward location if GPS located
                if (gpsLocated) {
                    forwardToHttpApi(latitude, longitude, speed, course,
                        year, month, day, hour, minute, second, imei,
                        ignitionOn, charging, sosPressed, vibrationDetected, relayOn,
                        satelliteCount, mcc, mnc);
                }

                // Publish alarm event for backend alert generation
                publishAlarmEvent(imei, alarmCode);
            }

            return buildResponse(packet, (byte) 0x26);
        } catch (Exception e) {
            log.error("[V5] Error parsing alarm packet", e);
            return null;
        }
    }

    private byte[] handleInfoPacket(byte[] packet, String clientId) {
        String imei = clientImeiMap.get(clientId);

        // 0x94 (Information Transmission) packet structure per BR05 protocol:
        //   [0-1]   Start bits (79 79 for extended)
        //   [2-3]   Length (2 bytes, big-endian for extended)
        //   [4]     Protocol number (0x94)
        //   [5]     Information type (0x00 = external voltage, 0x02 = altitude, etc.)
        //   [6-7]   Information content (voltage value for type 0x00)
        //   [8-9]   Message sequence number
        //   [10-11] CRC
        //   [12-13] Stop bits (0x0D 0x0A)
        //
        // For external voltage (type 0x00):
        //   Bytes [6-7] form a 2-byte hex value. Convert to decimal, divide by 100.
        //   Example: 0x04C6 = 1222 → 12.22V
        //   (Per BR05 protocol document: "0X04, 0X9F, 049F → 1183 → 11.83V")

        if (packet.length < 8) {
            log.warn("[V5] Info packet too short: {} bytes, skipping", packet.length);
            return null;
        }

        int infoType = packet[5] & 0xFF;

        // Only parse external voltage (type 0x00). Other types (altitude, bluetooth
        // fuel, temperature, tire pressure) are not needed for this device.
        if (infoType != 0x00) {
            log.debug("[V5] Info packet: unsupported info type 0x{}, skipping", String.format("%02X", infoType));
            return null;
        }

        try {
            // Extract voltage: 2-byte big-endian value at bytes [6-7]
            int voltageRaw = ((packet[6] & 0xFF) << 8) | (packet[7] & 0xFF);
            double voltage = voltageRaw / 100.0;

            log.info("[V5] Info packet: external voltage from imei={} voltage={}V (raw=0x{})",
                    imei, voltage, String.format("%04X", voltageRaw));

            // Forward voltage to backend via RabbitMQ
            if (imei != null) {
                String deviceIdStr = deviceMappingCacheService.getDeviceIdByImei(imei);
                if (deviceIdStr != null) {
                    UUID deviceId = UUID.fromString(deviceIdStr);
                    gpsIngestService.forwardVoltage(deviceId, imei, voltage);
                } else {
                    log.warn("[V5] No device mapping for IMEI: {}, skipping voltage forward", imei);
                }
            } else {
                log.warn("[V5] No IMEI for clientId: {}, skipping voltage forward", clientId);
            }
        } catch (Exception e) {
            log.error("[V5] Failed to parse voltage from info packet: imei={} err={}", imei, e.getMessage());
        }

        return null;
    }

    private void forwardToHttpApi(double lat, double lng, int speed, int course,
                                   int year, int month, int day, int hour, int minute, int second,
                                   String imei,
                                   boolean ignitionOn, Boolean externalPowerConnected,
                                   Boolean sosPressed, Boolean vibrationDetected, Boolean relayOn,
                                   int satelliteCount, int mcc, int mnc) {
        try {
            String deviceId = imei != null ? deviceMappingCacheService.getDeviceIdByImei(imei) : null;
            if (deviceId == null) {
                log.error("[V5] No device mapping found for IMEI: {}", imei);
                return;
            }

            // Build timestamp as epoch millis
            java.time.LocalDateTime ldt = java.time.LocalDateTime.of(2000 + year, month, day, hour, minute, second);
            long sentAtMillis = ldt.toEpochSecond(java.time.ZoneOffset.UTC) * 1000;

            // Build GpsIngestRequest and call GpsIngestService directly (no HTTP loopback)
            GpsIngestRequest request = new GpsIngestRequest();
            request.setDeviceId(deviceId);
            request.setLat(lat);
            request.setLng(lng);
            request.setSpeedKph((double) speed);
            request.setHeading((double) course);
            request.setSentAt(sentAtMillis);
            request.setIsOnline(true);
            request.setIgnitionOn(ignitionOn);
            request.setExternalPowerConnected(externalPowerConnected);
            request.setSosPressed(sosPressed);
            request.setVibrationDetected(vibrationDetected);
            request.setRelayOn(relayOn);
            request.setGpsSatelliteCount(satelliteCount);
            // GSM signal strength is NOT available from the 0x22 GPS or 0x26 alarm
            // packet's LBS section — the MNC field is the Mobile Network Code, not
            // signal strength. GSM signal level is only available from the heartbeat
            // (0x13) and alarm (0x26) Terminal Info area, forwarded separately via
            // forwardTelemetryFromHeartbeat(). Set to null here to avoid storing
            // the MNC as a fake GSM signal value.
            request.setGsmSignalStrength(null);

            gpsIngestService.ingest(request);
            log.debug("[V5] Forwarded to GpsIngestService directly for device: {}", deviceId);
        } catch (Exception e) {
            log.error("[V5] Failed to forward to ingest service: {}", e.getMessage());
        }
    }

    private String convertBcdToImei(String hexString) {
        // Convert BCD bytes to IMEI number
        StringBuilder imei = new StringBuilder();
        for (int i = 0; i < hexString.length(); i += 2) {
            String byteStr = hexString.substring(i, i + 2);
            int b = Integer.parseInt(byteStr, 16);
            String bcd = String.format("%02X", b);
            // BCD encoding: each nibble is a digit
            imei.append(bcd.charAt(0));
            imei.append(bcd.charAt(1));
        }
        // Remove leading zero if present
        String result = imei.toString();
        if (result.startsWith("0")) {
            result = result.substring(1);
        }
        return result;
    }

    /**
     * Publishes a device lifecycle event (LOGIN, HEARTBEAT, DISCONNECT) to the backend.
     */
    private void publishDeviceEvent(String imei, String eventType) {
        try {
            String deviceIdStr = deviceMappingCacheService.getDeviceIdByImei(imei);
            UUID deviceId = deviceIdStr != null ? UUID.fromString(deviceIdStr) : null;
            DeviceEventMessage event = new DeviceEventMessage(
                    deviceId, imei, eventType, Instant.now()
            );
            deviceEventProducer.publishDeviceEvent(event);
        } catch (Exception e) {
            log.error("[V5] Failed to publish device event: imei={} type={} err={}", imei, eventType, e.getMessage());
        }
    }

    /**
     * Forwards battery and GSM telemetry extracted from a heartbeat or alarm
     * packet to the backend via RabbitMQ. Heartbeats are the primary source
     * of battery level data since the 0x22 GPS packet does not include the
     * voltage level or terminal info bytes.
     *
     * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
     * Per AGENTS.md rule 17: use shared message contracts (TelemetryMessage).
     */
    private void forwardTelemetryFromHeartbeat(String imei, Integer batteryPct,
                                                Integer gsmSignal, boolean charging) {
        try {
            String deviceIdStr = deviceMappingCacheService.getDeviceIdByImei(imei);
            if (deviceIdStr == null) {
                log.warn("[V5] No device mapping for IMEI: {}, skipping telemetry", imei);
                return;
            }
            UUID deviceId = UUID.fromString(deviceIdStr);

            gpsIngestService.forwardTelemetry(
                    deviceId, imei,
                    batteryPct != null ? batteryPct.doubleValue() : null,
                    gsmSignal,
                    charging
            );

            log.debug("[V5] Forwarded heartbeat/alarm telemetry: imei={} battery={}%, gsm={}, charging={}",
                    imei, batteryPct, gsmSignal, charging);
        } catch (Exception e) {
            log.error("[V5] Failed to forward heartbeat telemetry: imei={} err={}", imei, e.getMessage());
        }
    }

    /**
     * Publishes an alarm event to the backend via RabbitMQ so the backend
     * can map the BR05 alarm code to an alert type (e.g. 0x0E →
     * EXTERNAL_POWER_LOW, 0x19 → INTERNAL_BATTERY_LOW) and generate
     * canonical alerts and notifications.
     *
     * Per AGENTS.md rule 5: never assume a device command succeeded until
     * acknowledgement is received. Alarm events are device-initiated, not
     * commands, so this rule applies to command replies, not alarms.
     * Per AGENTS.md rule 17: use shared message contracts (DeviceEventMessage).
     */
    private void publishAlarmEvent(String imei, int alarmCode) {
        try {
            String deviceIdStr = deviceMappingCacheService.getDeviceIdByImei(imei);
            UUID deviceId = deviceIdStr != null ? UUID.fromString(deviceIdStr) : null;
            DeviceEventMessage event = new DeviceEventMessage(
                    deviceId, imei, DeviceEventMessage.EVENT_ALARM, alarmCode, Instant.now()
            );
            deviceEventProducer.publishDeviceEvent(event);
            log.info("[V5] Published alarm event: imei={} code=0x{}", imei, String.format("%02X", alarmCode));
        } catch (Exception e) {
            log.error("[V5] Failed to publish alarm event: imei={} err={}", imei, e.getMessage());
        }
    }

    private byte[] buildResponse(byte[] requestPacket, byte protocolNumber) {
        boolean extended = requestPacket[0] == 0x79;

        // Extract serial number from request
        int serialOffset = requestPacket.length - 4; // Before error check (2 bytes) + stop (2 bytes)
        byte serialHigh = requestPacket[serialOffset - 2];
        byte serialLow = requestPacket[serialOffset - 1];

        // Build response packet
        byte[] response;
        if (!extended) {
            // Standard packet: 78 78 05 [protocol] [serial-high] [serial-low] [crc-high] [crc-low] 0D 0A
            response = new byte[] {
                0x78, 0x78,           // Start bits
                0x05,                 // Length
                protocolNumber,       // Protocol (same as request)
                serialHigh,           // Serial number high
                serialLow,            // Serial number low
                0x00, 0x00,           // CRC placeholder
                0x0D, 0x0A            // Stop bits
            };
        } else {
            // Extended packet
            response = new byte[] {
                0x79, 0x79,           // Start bits
                0x00, 0x05,           // Length (2 bytes)
                protocolNumber,
                serialHigh,
                serialLow,
                0x00, 0x00,
                0x0D, 0x0A
            };
        }

        // Calculate CRC
        int crcStart = extended ? 2 : 2;
        int crcEnd = response.length - 4;
        int crc = calculateCrc16(response, crcStart, crcEnd);

        response[response.length - 4] = (byte) ((crc >> 8) & 0xFF);
        response[response.length - 3] = (byte) (crc & 0xFF);

        return response;
    }

    private int calculateCrc16(byte[] data, int start, int end) {
        int fcs = 0xFFFF;
        for (int i = start; i < end; i++) {
            fcs = (fcs >> 8) ^ CRC16_TABLE[(fcs ^ (data[i] & 0xFF)) & 0xFF];
        }
        return ~fcs & 0xFFFF;
    }

    // CRC-ITU lookup table
    private static final int[] CRC16_TABLE = {
        0x0000, 0x1189, 0x2312, 0x329B, 0x4624, 0x57AD, 0x6536, 0x74BF,
        0x8C48, 0x9DC1, 0xAF5A, 0xBED3, 0xCA6C, 0xDBE5, 0xE97E, 0xF8F7,
        0x1081, 0x0108, 0x3393, 0x221A, 0x56A5, 0x472C, 0x75B7, 0x643E,
        0x9CC9, 0x8D40, 0xBFDB, 0xAE52, 0xDAED, 0xCB64, 0xF9FF, 0xE876,
        0x2102, 0x308B, 0x0210, 0x1399, 0x6726, 0x76AF, 0x4434, 0x55BD,
        0xAD4A, 0xBCC3, 0x8E58, 0x9FD1, 0xEB6E, 0xFAE7, 0xC87C, 0xD9F5,
        0x3183, 0x200A, 0x1291, 0x0318, 0x77A7, 0x662E, 0x54B5, 0x453C,
        0xBDCB, 0xAC42, 0x9ED9, 0x8F50, 0xFBEF, 0xEA66, 0xD8FD, 0xC974,
        0x4204, 0x538D, 0x6116, 0x709F, 0x0420, 0x15A9, 0x2732, 0x36BB,
        0xCE4C, 0xDFC5, 0xED5E, 0xFCD7, 0x8868, 0x99E1, 0xAB7A, 0xBAF3,
        0x5285, 0x430C, 0x7197, 0x601E, 0x14A1, 0x0528, 0x37B3, 0x263A,
        0xDECD, 0xCF44, 0xFDDF, 0xEC56, 0x98E9, 0x8960, 0xBBFB, 0xAA72,
        0x6306, 0x728F, 0x4014, 0x519D, 0x2522, 0x34AB, 0x0630, 0x17B9,
        0xEF4E, 0xFEC7, 0xCC5C, 0xDDD5, 0xA96A, 0xB8E3, 0x8A78, 0x9BF1,
        0x7387, 0x620E, 0x5095, 0x411C, 0x35A3, 0x242A, 0x16B1, 0x0738,
        0xFFCF, 0xEE46, 0xDCDD, 0xCD54, 0xB9EB, 0xA862, 0x9AF9, 0x8B70,
        0x8408, 0x9581, 0xA71A, 0xB693, 0xC22C, 0xD3A5, 0xE13E, 0xF0B7,
        0x0840, 0x19C9, 0x2B52, 0x3ADB, 0x4E64, 0x5FED, 0x6D76, 0x7CFF,
        0x9489, 0x8500, 0xB79B, 0xA612, 0xD2AD, 0xC324, 0xF1BF, 0xE036,
        0x18C1, 0x0948, 0x3BD3, 0x2A5A, 0x5EE5, 0x4F6C, 0x7DF7, 0x6C7E,
        0xA50A, 0xB483, 0x8618, 0x9791, 0xE32E, 0xF2A7, 0xC03C, 0xD1B5,
        0x2942, 0x38CB, 0x0A50, 0x1BD9, 0x6F66, 0x7EEF, 0x4C74, 0x5DFD,
        0xB58B, 0xA402, 0x9699, 0x8710, 0xF3AF, 0xE226, 0xD0BD, 0xC134,
        0x39C3, 0x284A, 0x1AD1, 0x0B58, 0x7FE7, 0x6E6E, 0x5CF5, 0x4D7C,
        0xC60C, 0xD785, 0xE51E, 0xF497, 0x8028, 0x91A1, 0xA33A, 0xB2B3,
        0x4A44, 0x5BCD, 0x6956, 0x78DF, 0x0C60, 0x1DE9, 0x2F72, 0x3EFB,
        0xD68D, 0xC704, 0xF59F, 0xE416, 0x90A9, 0x8120, 0xB3BB, 0xA232,
        0x5AC5, 0x4B4C, 0x79D7, 0x685E, 0x1CE1, 0x0D68, 0x3FF3, 0x2E7A,
        0xE70E, 0xF687, 0xC41C, 0xD595, 0xA12A, 0xB0A3, 0x8238, 0x93B1,
        0x6B46, 0x7ACF, 0x4854, 0x59DD, 0x2D62, 0x3CEB, 0x0E70, 0x1FF9,
        0xF78F, 0xE606, 0xD49D, 0xC514, 0xB1AB, 0xA022, 0x92B9, 0x8330,
        0x7BC7, 0x6A4E, 0x58D5, 0x495C, 0x3DE3, 0x2C6A, 0x1EF1, 0x0F78
    };
}
