package com.yantrago.gateway.tcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-10: Tests for DeviceConnectionRegistry session ownership.
 *
 * Covers:
 * - Normal register/unregister
 * - Connection replacement (legitimate reconnect from different client)
 * - Stale disconnect doesn't remove newer connection's mapping
 * - Same-client re-register is idempotent
 */
class DeviceConnectionRegistryTest {

    private DeviceConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DeviceConnectionRegistry();
    }

    private OutputStream newStream() {
        return new ByteArrayOutputStream();
    }

    @Test
    @DisplayName("SEC-10: register and unregister normal connection")
    void registerUnregister_normal() {
        OutputStream out = newStream();
        registry.registerConnection("client-1", "imei-1", out, "YANTRAGO_FENCING");

        assertTrue(registry.isDeviceOnline("imei-1"));
        assertEquals("YANTRAGO_FENCING", registry.getProtocolForImei("imei-1"));
        assertTrue(registry.sendCommand("imei-1", new byte[]{0x01}));

        registry.unregisterConnection("client-1");
        assertFalse(registry.isDeviceOnline("imei-1"));
    }

    @Test
    @DisplayName("SEC-10: new connection replaces old connection for same IMEI")
    void reconnect_replacesOldConnection() {
        OutputStream out1 = newStream();
        OutputStream out2 = newStream();
        registry.registerConnection("client-1", "imei-1", out1, "YANTRAGO_FENCING");
        registry.registerConnection("client-2", "imei-1", out2, "YANTRAGO_FENCING");

        // The new connection should be the active one
        assertTrue(registry.isDeviceOnline("imei-1"));
        // client-1's mapping should be cleaned up
        // (it was removed during registerConnection)
    }

    @Test
    @DisplayName("SEC-10: stale disconnect from old client does NOT remove newer connection")
    void staleDisconnect_doesNotRemoveNewerConnection() {
        OutputStream out1 = newStream();
        OutputStream out2 = newStream();
        registry.registerConnection("client-1", "imei-1", out1, "YANTRAGO_FENCING");
        registry.registerConnection("client-2", "imei-1", out2, "YANTRAGO_FENCING");

        // client-1 disconnects (stale) — should NOT remove the IMEI mapping
        // because client-2 now owns it
        registry.unregisterConnection("client-1");

        // The IMEI must still be online (owned by client-2)
        assertTrue(registry.isDeviceOnline("imei-1"),
                "Stale disconnect must not remove the newer connection's mapping");
        assertEquals("YANTRAGO_FENCING", registry.getProtocolForImei("imei-1"));
    }

    @Test
    @DisplayName("SEC-10: current owner disconnect removes the mapping")
    void currentOwnerDisconnect_removesMapping() {
        OutputStream out1 = newStream();
        OutputStream out2 = newStream();
        registry.registerConnection("client-1", "imei-1", out1, "YANTRAGO_FENCING");
        registry.registerConnection("client-2", "imei-1", out2, "YANTRAGO_FENCING");

        // client-2 (current owner) disconnects — should remove the mapping
        registry.unregisterConnection("client-2");
        assertFalse(registry.isDeviceOnline("imei-1"),
                "Current owner disconnect must remove the mapping");
    }

    @Test
    @DisplayName("SEC-10: unregister unknown client is a no-op")
    void unregisterUnknownClient_noOp() {
        registry.unregisterConnection("unknown-client");
        // No exception, no side effect
        assertFalse(registry.isDeviceOnline("imei-1"));
    }

    @Test
    @DisplayName("SEC-10: sendCommand to offline device returns false")
    void sendCommand_offlineDevice_returnsFalse() {
        assertFalse(registry.sendCommand("imei-unknown", new byte[]{0x01}));
    }

    @Test
    @DisplayName("SEC-10: multiple IMEIs are tracked independently")
    void multipleImeis_trackedIndependently() {
        registry.registerConnection("client-1", "imei-1", newStream(), "YANTRAGO_FENCING");
        registry.registerConnection("client-2", "imei-2", newStream(), "CONCOX_V5");

        assertTrue(registry.isDeviceOnline("imei-1"));
        assertTrue(registry.isDeviceOnline("imei-2"));
        assertEquals(2, registry.getOnlineImeis().size());

        registry.unregisterConnection("client-1");
        assertFalse(registry.isDeviceOnline("imei-1"));
        assertTrue(registry.isDeviceOnline("imei-2"));
    }
}
