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
            log.debug("[V5] ACC: {}, Charging: {}, GPS: {}, FuelCut: {}",
                accOn ? "ON" : "OFF", charging ? "Yes" : "No", gpsTracking ? "ON" : "OFF", fuelCutOff ? "YES" : "NO");

            // Sync lock state from heartbeat Bit7
            String imei = clientImeiMap.get(clientId);
            if (imei != null) {
                try {
                    vehicleCommandService.updateLockStateFromHeartbeat(imei, fuelCutOff);
                    deviceHeartbeatService.recordHeartbeat(imei);
                    publishDeviceEvent(imei, DeviceEventMessage.EVENT_HEARTBEAT);
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

            // Flag byte: command execution result
            int flag = packet[dataOffset] & 0xFF;
            boolean success = (flag == 0x00);
            log.debug("[V5] Command reply flag: 0x{} ({})", String.format("%02X", flag), success ? "SUCCESS" : "FAILURE");

            // Terminal info byte: Bit7 = fuel/relay cut-off state
            int terminalInfo = packet[dataOffset + 1] & 0xFF;
            boolean fuelCutOff = (terminalInfo & 0x80) != 0;
            log.debug("[V5] Command reply terminal info: FuelCut={}", fuelCutOff ? "YES" : "NO");

            // Extract result text (remaining bytes before serial number + CRC + stop)
            int serialOffset = packet.length - 4; // Before CRC(2) + Stop(2)
            int contentEnd = serialOffset - 2; // Before serial number(2)
            StringBuilder resultText = new StringBuilder();
            for (int i = dataOffset + 2; i < contentEnd; i++) {
                resultText.append((char) (packet[i] & 0xFF));
            }
            log.debug("[V5] Command reply result text: {}", resultText);

            // Process the reply in the command service
            String imei = clientImeiMap.get(clientId);
            if (imei != null) {
                try {
                    vehicleCommandService.processCommandReply(imei, success, resultText.toString(), fuelCutOff);
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
     * Packet structure (per BR05 protocol doc):
     *   Start: 0x78 0x78
     *   Length: 1 byte = protocol(1) + server_flag(4) + content(N) + language(2) + serial(2) + crc(2)
     *   Protocol: 0x80
     *   Server flags: 4 bytes (binary, returned by terminal in reply)
     *   Command content: M bytes (ASCII, compatible with SMS commands)
     *   Language: 2 bytes (0x01 = Chinese, 0x02 = English)
     *   Serial number: 2 bytes
     *   CRC: 2 bytes
     *   Stop: 0x0D 0x0A
     *
     * @param commandContent the ASCII command string (e.g. "DYD=00" for relay ON, "DYD=01" for relay OFF)
     * @return the complete packet bytes ready to send over TCP
     */
    public byte[] buildCommandPacket(String commandContent) {
        byte[] contentBytes = commandContent.getBytes(StandardCharsets.US_ASCII);
        int serial = serialCounter.getAndIncrement() & 0xFFFF;

        // Information content = server_flag(4) + content(N) + language(2)
        int infoContentLen = 4 + contentBytes.length + 2;

        // Length = protocol(1) + info_content(N+6) + serial(2) + crc(2) = N + 11
        int length = 1 + infoContentLen + 2 + 2;

        // Total packet = start(2) + length_byte(1) + [protocol(1) + info_content + serial(2) + crc(2)] + stop(2)
        //             = 2 + 1 + length + 2 = N + 16
        byte[] packet = new byte[2 + 1 + length + 2];

        packet[0] = 0x78;
        packet[1] = 0x78;
        packet[2] = (byte) length;
        packet[3] = (byte) 0x80; // Protocol number

        int pos = 4;
        // Server flags: 4 bytes (all zeros — terminal returns these in reply)
        packet[pos++] = 0x00;
        packet[pos++] = 0x00;
        packet[pos++] = 0x00;
        packet[pos++] = 0x00;
        // Command content (ASCII)
        System.arraycopy(contentBytes, 0, packet, pos, contentBytes.length);
        pos += contentBytes.length;
        // Language: 0x02 = English
        packet[pos++] = 0x00;
        packet[pos++] = 0x02;
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

            // ACC status
            int acc = packet[dataOffset + 26] & 0xFF;

            // Terminal info byte (next byte after ACC)
            int terminalInfo = 0;
            if (packet.length > dataOffset + 27) {
                terminalInfo = packet[dataOffset + 27] & 0xFF;
            }
            boolean ignitionOn = (acc & 0x01) != 0 || (terminalInfo & 0x02) != 0;
            boolean externalPowerConnected = (terminalInfo & 0x04) == 0; // Bit2: 0=charging, 1=not charging
            boolean sosPressed = (terminalInfo & 0x01) != 0; // Bit0: SOS
            boolean vibrationDetected = (terminalInfo & 0x08) != 0; // Bit3: vibration
            boolean relayOn = (terminalInfo & 0x80) != 0; // Bit7: fuel/relay cut-off

            log.debug("[V5] GPS: {}, {} | Speed: {} km/h | Satellites: {} | GPS Located: {}",
                String.format("%.6f", latitude), String.format("%.6f", longitude), speed, satelliteCount, gpsLocated ? "Yes" : "No");
            log.debug("[V5] Time: 20{}-{}-{} {}:{}:{} | Course: {}°",
                String.format("%02d", year), String.format("%02d", month), String.format("%02d", day),
                String.format("%02d", hour), String.format("%02d", minute), String.format("%02d", second), course);
            log.debug("[V5] Ignition: {} | ExtPower: {} | SOS: {} | Vibration: {} | Relay: {}",
                ignitionOn ? "ON" : "OFF",
                externalPowerConnected ? "Connected" : "Disconnected",
                sosPressed ? "YES" : "no",
                vibrationDetected ? "YES" : "no",
                relayOn ? "ON" : "OFF");

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
        // Similar to location packet but with alarm type
        log.info("[V5] Alarm packet received from: {}", clientId);
        // Parse alarm type and forward location
        return null;
    }

    private byte[] handleInfoPacket(byte[] packet, String clientId) {
        log.info("[V5] Info packet from: {}", clientId);
        // Handle voltage, etc.
        return null;
    }

    private void forwardToHttpApi(double lat, double lng, int speed, int course,
                                   int year, int month, int day, int hour, int minute, int second,
                                   String imei,
                                   boolean ignitionOn, boolean externalPowerConnected,
                                   boolean sosPressed, boolean vibrationDetected, boolean relayOn,
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
            request.setGsmSignalStrength(mnc);

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
