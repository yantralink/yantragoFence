package com.yantrago.gateway.tcp.concox;

import com.yantrago.gateway.queue.DeviceEventProducer;
import com.yantrago.gateway.service.DeviceHeartbeatService;
import com.yantrago.gateway.service.DeviceMappingCacheService;
import com.yantrago.gateway.service.GpsIngestService;
import com.yantrago.gateway.service.VehicleCommandService;
import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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
}
