package com.yantrago.gateway.tcp.concox;

import com.yantrago.gateway.queue.DeviceEventProducer;
import com.yantrago.gateway.model.GpsIngestRequest;
import com.yantrago.gateway.service.DeviceHeartbeatService;
import com.yantrago.gateway.service.DeviceMappingCacheService;
import com.yantrago.gateway.service.GpsIngestService;
import com.yantrago.gateway.service.VehicleCommandService;
import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.shared.queue.DeviceEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConcoxV5ProtocolHandler.
 *
 * Verifies protocol detection (canHandle), protocol name, and basic packet routing.
 *
 * Per AGENTS.md rule 16: reused protocol code is copied as-is, not rewritten.
 */
class ConcoxV5ProtocolHandlerTest {

    private DeviceConnectionRegistry connectionRegistry;
    private VehicleCommandService vehicleCommandService;
    private GpsIngestService gpsIngestService;
    private DeviceMappingCacheService deviceMappingCacheService;
    private DeviceEventProducer deviceEventProducer;
    private DeviceHeartbeatService deviceHeartbeatService;
    private ConcoxV5ProtocolHandler handler;

    @BeforeEach
    void setUp() {
        connectionRegistry = mock(DeviceConnectionRegistry.class);
        vehicleCommandService = mock(VehicleCommandService.class);
        gpsIngestService = mock(GpsIngestService.class);
        deviceMappingCacheService = mock(DeviceMappingCacheService.class);
        deviceEventProducer = mock(DeviceEventProducer.class);
        deviceHeartbeatService = mock(DeviceHeartbeatService.class);

        handler = new ConcoxV5ProtocolHandler(
                connectionRegistry, vehicleCommandService, gpsIngestService,
                deviceMappingCacheService, deviceEventProducer, deviceHeartbeatService
        );
    }

    @Test
    @DisplayName("getProtocolName should return CONCOX_V5")
    void getProtocolName_shouldReturnConcoxV5() {
        assertEquals("CONCOX_V5", handler.getProtocolName());
    }

    @Test
    @DisplayName("canHandle should return true for 0x78 0x78 header")
    void canHandle_shouldReturnTrueFor7878() {
        byte[] data = {0x78, 0x78, 0x00, 0x01, 0x00};
        assertTrue(handler.canHandle(data));
    }

    @Test
    @DisplayName("canHandle should return true for 0x79 0x79 header (extended)")
    void canHandle_shouldReturnTrueFor7979() {
        byte[] data = {0x79, 0x79, 0x00, 0x01, 0x00};
        assertTrue(handler.canHandle(data));
    }

    @Test
    @DisplayName("canHandle should return false for non-Concox header")
    void canHandle_shouldReturnFalseForNonConcox() {
        byte[] data = {0x7E, 0x00, 0x01, 0x00};
        assertFalse(handler.canHandle(data));
    }

    @Test
    @DisplayName("canHandle should return false for null input")
    void canHandle_shouldReturnFalseForNull() {
        assertFalse(handler.canHandle(null));
    }

    @Test
    @DisplayName("canHandle should return false for short input (< 2 bytes)")
    void canHandle_shouldReturnFalseForShortInput() {
        assertFalse(handler.canHandle(new byte[]{0x78}));
        assertFalse(handler.canHandle(new byte[]{}));
    }

    @Test
    @DisplayName("getImeiForClient should return null for unknown client")
    void getImeiForClient_shouldReturnNullForUnknown() {
        assertNull(handler.getImeiForClient("unknown-client"));
    }

    @Test
    @DisplayName("handlePacket should return null for packets shorter than 5 bytes")
    void handlePacket_shouldReturnNullForShortPackets() {
        assertNull(handler.handlePacket(new byte[]{0x78, 0x78, 0x01}, "client-1"));
        assertNull(handler.handlePacket(new byte[]{}, "client-1"));
    }

    @Test
    @DisplayName("handlePacket should return null for unknown protocol number")
    void handlePacket_shouldReturnNullForUnknownProtocol() {
        // 0x78 0x78 [len] [len] [protocol=0xFF] ... [crc] [0x0D 0x0A]
        byte[] packet = {0x78, 0x78, 0x00, 0x05, (byte) 0xFF, 0x00, 0x00, 0x0D, 0x0A};
        assertNull(handler.handlePacket(packet, "client-1"));
    }

    @Test
    @DisplayName("handlePacket should handle login packet (protocol 0x01)")
    void handlePacket_shouldHandleLoginPacket() {
        // Build a minimal login packet: 0x78 0x78 [len] [protocol=0x01] [imei 8 bytes] [serial 2 bytes] [crc] [0x0D 0x0A]
        byte[] packet = new byte[]{
                0x78, 0x78,                     // Start bit
                0x0D,                           // Length (13 bytes payload)
                0x01,                           // Protocol number: Login
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, // IMEI (8 bytes)
                0x00, 0x01,                     // Serial number
                0x00, 0x00,                     // CRC (placeholder)
                0x0D, 0x0A                      // End bit
        };
        byte[] response = handler.handlePacket(packet, "client-1");
        // Login should produce a response (login ACK)
        // The response may be null if CRC validation fails, but the handler should not throw
        assertNotNull(response, "Login packet should produce a response");
    }

    @Test
    @DisplayName("handlePacket should handle heartbeat packet (protocol 0x13)")
    void handlePacket_shouldHandleHeartbeatPacket() {
        // Heartbeat: 0x78 0x78 [len=1] [protocol=0x13] [serial] [crc] [0x0D 0x0A]
        // Minimal valid structure — the handler may return null or a reply
        byte[] packet = new byte[]{
                0x78, 0x78,
                0x05,
                0x13,
                0x00, 0x01,
                0x00, 0x00,
                0x0D, 0x0A
        };
        // Should not throw — heartbeat from unknown client
        assertDoesNotThrow(() -> handler.handlePacket(packet, "client-unknown"));
    }

    @Test
    @DisplayName("removeClient should not throw for unknown client")
    void removeClient_shouldNotThrowForUnknown() {
        assertDoesNotThrow(() -> handler.removeClient("unknown-client"));
    }

    @Test
    @DisplayName("handlePacket should parse battery level and GSM from heartbeat and forward telemetry")
    void handlePacket_shouldParseBatteryFromHeartbeat() {
        // Heartbeat: 0x78 0x78 [len] [protocol=0x13] [terminalInfo] [voltageLevel] [gsmSignal]
        //            [language 2 bytes] [serial 2 bytes] [crc 2 bytes] [0x0D 0x0A]
        // terminalInfo=0x04 (charging, Bit2=1), voltageLevel=0x04 (60%), gsmSignal=0x03 (good)
        byte[] packet = new byte[]{
                0x78, 0x78,
                0x0B,       // Length (11 bytes payload)
                0x13,       // Protocol: heartbeat
                0x04,       // Terminal Info: Bit2=1 (charging)
                0x04,       // Voltage Level: 0x04 = 60%
                0x03,       // GSM Signal: 0x03 = good
                0x32, 0x01, // Language
                0x00, 0x01, // Serial number
                0x00, 0x00, // CRC (placeholder)
                0x0D, 0x0A  // Stop bits
        };

        // Map the client to a known IMEI and device ID so telemetry forwarding is triggered
        String testImei = "123456789012345";
        String testDeviceId = UUID.randomUUID().toString();
        // First send a login packet to register the IMEI for this client
        byte[] loginPacket = new byte[]{
                0x78, 0x78,
                0x0D,       // Length
                0x01,       // Protocol: login
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, 0x01, 0x23, 0x45, // IMEI bytes
                0x00, 0x01, // Serial
                0x00, 0x00, // CRC
                0x0D, 0x0A
        };
        handler.handlePacket(loginPacket, "client-heartbeat-test");
        when(deviceMappingCacheService.getDeviceIdByImei(testImei)).thenReturn(testDeviceId);

        // Now send the heartbeat
        handler.handlePacket(packet, "client-heartbeat-test");

        // Verify telemetry was forwarded with battery=60.0, gsmSignal=3, charging=true
        verify(gpsIngestService).forwardTelemetry(
                eq(UUID.fromString(testDeviceId)), eq(testImei),
                eq(60.0), eq(3), eq(true)
        );
        // Verify heartbeat was recorded (login + heartbeat = 2 calls)
        verify(deviceHeartbeatService, times(2)).recordHeartbeat(testImei);
        // Verify device event was published (LOGIN + HEARTBEAT = 2 events)
        verify(deviceEventProducer, times(2)).publishDeviceEvent(any());
    }

    @Test
    @DisplayName("handlePacket should parse alarm packet, forward telemetry, and publish alarm event")
    void handlePacket_shouldHandleAlarmPacket() {
        // Build a minimal alarm packet (0x26) with enough bytes for the parser.
        // Format: 0x78 0x78 [len] [0x26] [datetime 6] [sats 1] [lat 4] [lng 4] [speed 1]
        //         [course 2] [lbsLen 1] [mcc 2] [mnc 1] [lac 2] [cellId 3]
        //         [terminalInfo 1] [voltageLevel 1] [gsmSignal 1] [alarmLang 2]
        //         [serial 2] [crc 2] [0x0D 0x0A]
        byte[] packet = new byte[42];
        packet[0] = 0x78; packet[1] = 0x78;
        packet[2] = 0x26; // length (placeholder)
        packet[3] = 0x26; // protocol: alarm
        // datetime: 25 09 11 12 00 00
        packet[4] = 0x25; packet[5] = 0x09; packet[6] = 0x0B; packet[7] = 0x0C; packet[8] = 0x00; packet[9] = 0x00;
        packet[10] = 0x03; // satellites
        // lat (4 bytes) — 18.0 degrees = 18 * 1800000 = 32400000 = 0x01EE6280
        packet[11] = 0x01; packet[12] = (byte) 0xEE; packet[13] = 0x62; packet[14] = (byte) 0x80;
        // lng (4 bytes) — 73.0 degrees = 73 * 1800000 = 131400000 = 0x07D2D200
        packet[15] = 0x07; packet[16] = (byte) 0xD2; packet[17] = (byte) 0xD2; packet[18] = 0x00;
        packet[19] = 0x00; // speed
        packet[20] = 0x04; packet[21] = 0x00; // course/status (GPS located bit set)
        packet[22] = 0x06; // LBS length
        packet[23] = 0x00; packet[24] = 0x28; // MCC = 40 (India)
        packet[25] = 0x01; // MNC
        packet[26] = 0x00; packet[27] = 0x10; // LAC
        packet[28] = 0x00; packet[29] = 0x00; packet[30] = 0x01; // Cell ID
        packet[31] = 0x04; // Terminal Info: charging
        packet[32] = 0x04; // Voltage Level: 60%
        packet[33] = 0x03; // GSM Signal: good
        packet[34] = 0x0E; // Alarm code: 0x0E = External power low alarm
        packet[35] = 0x02; // Language: English
        packet[36] = 0x00; packet[37] = 0x01; // Serial
        packet[38] = 0x00; packet[39] = 0x00; // CRC
        packet[40] = 0x0D; packet[41] = 0x0A; // Stop

        // Map the client to a known IMEI and device ID
        String testImei = "123456789012345";
        String testDeviceId = UUID.randomUUID().toString();
        // First send a login packet to register the IMEI
        byte[] loginPacket = new byte[]{
                0x78, 0x78, 0x0D, 0x01,
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, 0x01, 0x23, 0x45,
                0x00, 0x01, 0x00, 0x00, 0x0D, 0x0A
        };
        handler.handlePacket(loginPacket, "client-alarm-test");
        when(deviceMappingCacheService.getDeviceIdByImei(testImei)).thenReturn(testDeviceId);

        // Now send the alarm packet
        handler.handlePacket(packet, "client-alarm-test");

        // Verify telemetry was forwarded with battery=60.0, gsmSignal=3, charging=true
        verify(gpsIngestService).forwardTelemetry(
                eq(UUID.fromString(testDeviceId)), eq(testImei),
                eq(60.0), eq(3), eq(true)
        );

        // Verify alarm event was published with alarm code 0x0E
        verify(deviceEventProducer).publishDeviceEvent(argThat(msg ->
                DeviceEventMessage.EVENT_ALARM.equals(msg.getEventType()) &&
                        msg.getAlarmCode() != null &&
                        msg.getAlarmCode() == 0x0E));
    }

    @Test
    @DisplayName("handlePacket should handle GPS packet (0x22) without terminal info and not set externalPowerConnected")
    void handlePacket_shouldHandleGpsPacketWithoutTerminalInfo() {
        // Build a minimal 0x22 GPS packet with ACC + reporting mode (no terminal info).
        // Format: 0x78 0x78 [len] [0x22] [datetime 6] [sats 1] [lat 4] [lng 4] [speed 1]
        //         [course 2] [mcc 2] [mnc 1] [lac 2] [cellId 3] [acc 1] [reportMode 1]
        //         [serial 2] [crc 2] [0x0D 0x0A]
        byte[] packet = new byte[38];
        packet[0] = 0x78; packet[1] = 0x78;
        packet[2] = 0x22; // length (placeholder)
        packet[3] = 0x22; // protocol: GPS positioning
        // datetime
        packet[4] = 0x25; packet[5] = 0x09; packet[6] = 0x0B; packet[7] = 0x0C; packet[8] = 0x00; packet[9] = 0x00;
        packet[10] = 0x03; // satellites
        // lat (4 bytes) — 18.0 degrees
        packet[11] = 0x01; packet[12] = (byte) 0xEE; packet[13] = 0x62; packet[14] = (byte) 0x80;
        // lng (4 bytes) — 73.0 degrees
        packet[15] = 0x07; packet[16] = (byte) 0xD2; packet[17] = (byte) 0xD2; packet[18] = 0x00;
        packet[19] = 0x00; // speed
        packet[20] = 0x04; packet[21] = 0x00; // course/status (GPS located bit set)
        packet[22] = 0x00; packet[23] = 0x28; // MCC
        packet[24] = 0x01; // MNC
        packet[25] = 0x00; packet[26] = 0x10; // LAC
        packet[27] = 0x00; packet[28] = 0x00; packet[29] = 0x01; // Cell ID
        packet[30] = 0x01; // ACC high
        packet[31] = 0x00; // Reporting mode: regular
        packet[32] = 0x00; packet[33] = 0x01; // Serial
        packet[34] = 0x00; packet[35] = 0x00; // CRC
        packet[36] = 0x0D; packet[37] = 0x0A; // Stop

        // Map the client to a known IMEI and device ID so forwarding is triggered
        String testImei = "123456789012345";
        String testDeviceId = UUID.randomUUID().toString();
        byte[] loginPacket = new byte[]{
                0x78, 0x78, 0x0D, 0x01,
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, 0x01, 0x23, 0x45,
                0x00, 0x01, 0x00, 0x00, 0x0D, 0x0A
        };
        handler.handlePacket(loginPacket, "client-gps-test");
        when(deviceMappingCacheService.getDeviceIdByImei(testImei)).thenReturn(testDeviceId);

        // Send the GPS packet
        handler.handlePacket(packet, "client-gps-test");

        // Verify ingest was called and the request does NOT set externalPowerConnected
        // (0x22 packet has no terminal info byte — battery/charging are null)
        ArgumentCaptor<GpsIngestRequest> captor = ArgumentCaptor.forClass(GpsIngestRequest.class);
        verify(gpsIngestService).ingest(captor.capture());
        GpsIngestRequest request = captor.getValue();
        assertNull(request.getExternalPowerConnected(),
                "externalPowerConnected must be null for 0x22 GPS packets (no terminal info byte)");
        assertNull(request.getBatteryLevel(),
                "batteryLevel must be null for 0x22 GPS packets (no voltage level byte)");
        assertNull(request.getGsmSignalStrength(),
                "gsmSignalStrength must be null for 0x22 GPS packets (MNC is not GSM signal)");
    }

    @Test
    @DisplayName("handlePacket should skip alarm packet that is too short")
    void handlePacket_shouldSkipShortAlarmPacket() {
        // Build a short alarm packet (only 10 bytes — not enough for the 0x26 parser)
        byte[] packet = new byte[]{
                0x78, 0x78, 0x05, 0x26,
                0x00, 0x01, 0x00, 0x00,
                0x0D, 0x0A
        };

        // Should not throw and should not forward any telemetry
        assertDoesNotThrow(() -> handler.handlePacket(packet, "client-short-alarm"));
        verify(gpsIngestService, never()).forwardTelemetry(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handlePacket should parse external voltage from 0x94 info packet and forward")
    void handlePacket_shouldParseVoltageFromInfoPacket() {
        // 0x94 Info packet (extended format 79 79):
        //   79 79 00 08 94 00 04 C6 03 8D D1 0B 0D 0A
        //   Info type=0x00 (external voltage), voltage=0x04C6=1222→12.22V
        byte[] packet = new byte[]{
                0x79, 0x79,       // Start bits (extended)
                0x00, 0x08,       // Length (8 bytes payload)
                (byte) 0x94,      // Protocol: info packet
                0x00,             // Info type: external voltage
                0x04, (byte) 0xC6, // Voltage: 0x04C6 = 1222 → 12.22V
                0x03, (byte) 0x8D, // Sequence number
                (byte) 0xD1, 0x0B, // CRC
                0x0D, 0x0A        // Stop bits
        };

        // Map the client to a known IMEI and device ID
        String testImei = "123456789012345";
        String testDeviceId = UUID.randomUUID().toString();
        // First send a login packet to register the IMEI for this client
        byte[] loginPacket = new byte[]{
                0x78, 0x78,
                0x0D,       // Length
                0x01,       // Protocol: login
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, 0x01, 0x23, 0x45, // IMEI bytes
                0x00, 0x01, // Serial
                0x00, 0x00, // CRC
                0x0D, 0x0A
        };
        handler.handlePacket(loginPacket, "client-info-test");
        when(deviceMappingCacheService.getDeviceIdByImei(testImei)).thenReturn(testDeviceId);

        // Now send the info packet
        handler.handlePacket(packet, "client-info-test");

        // Verify voltage was forwarded: 12.22V
        verify(gpsIngestService).forwardVoltage(
                eq(UUID.fromString(testDeviceId)), eq(testImei), eq(12.22)
        );
    }

    @Test
    @DisplayName("handlePacket should skip 0x94 info packet with non-voltage info type")
    void handlePacket_shouldSkipNonVoltageInfoPacket() {
        // 0x94 Info packet with info type 0x02 (altitude, not voltage)
        byte[] packet = new byte[]{
                0x79, 0x79,
                0x00, 0x08,
                (byte) 0x94,
                0x02,             // Info type: altitude (not voltage)
                0x01, 0x64,       // Altitude: 356 meters
                0x00, 0x01,
                0x77, (byte) 0xB7,
                0x0D, 0x0A
        };

        handler.handlePacket(packet, "client-unknown");
        // Should not forward any voltage
        verify(gpsIngestService, never()).forwardVoltage(any(), any(), any());
    }

    @Test
    @DisplayName("handlePacket should skip 0x94 info packet that is too short")
    void handlePacket_shouldSkipShortInfoPacket() {
        // 0x94 Info packet with only 6 bytes (too short for voltage parsing)
        byte[] packet = new byte[]{
                0x79, 0x79,
                0x00, 0x04,
                (byte) 0x94,
                0x00,
                0x0D, 0x0A
        };

        handler.handlePacket(packet, "client-unknown");
        // Should not forward any voltage
        verify(gpsIngestService, never()).forwardVoltage(any(), any(), any());
    }
}
