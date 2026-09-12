package com.yantrago.api.queue;

import com.yantrago.api.model.Alert;
import com.yantrago.api.service.CanonicalAlertService;
import com.yantrago.api.service.DeviceHeartbeatService;
import com.yantrago.api.service.DeviceResolverService;
import com.yantrago.api.websocket.TelemetryBroadcastService;
import com.yantrago.shared.queue.DeviceEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DeviceEventConsumer.
 *
 * Verifies that:
 * - LOGIN/HEARTBEAT events record heartbeat and mark device online
 * - DISCONNECT events mark device offline
 * - ALARM events with mapped alarm codes generate canonical alerts
 * - ALARM events with unmapped codes are logged but do not create alerts
 * - ALARM events still record heartbeat (device is online)
 *
 * Per AGENTS.md rule 7: machineId is resolved from the device record, not from the message.
 */
class DeviceEventConsumerTest {

    private DeviceHeartbeatService deviceHeartbeatService;
    private TelemetryBroadcastService telemetryBroadcastService;
    private CanonicalAlertService canonicalAlertService;
    private DeviceResolverService deviceResolverService;
    private DeviceEventConsumer consumer;

    private final UUID deviceId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final String imei = "123456789012345";

    @BeforeEach
    void setUp() {
        deviceHeartbeatService = mock(DeviceHeartbeatService.class);
        telemetryBroadcastService = mock(TelemetryBroadcastService.class);
        canonicalAlertService = mock(CanonicalAlertService.class);
        deviceResolverService = mock(DeviceResolverService.class);

        consumer = new DeviceEventConsumer(
                deviceHeartbeatService, telemetryBroadcastService,
                canonicalAlertService, deviceResolverService
        );

        // Default: device resolves to a machine
        when(deviceResolverService.resolve(deviceId))
                .thenReturn(new DeviceResolverService.DeviceInfo(orgId, machineId, imei));
    }

    @Test
    @DisplayName("LOGIN event should record heartbeat and broadcast online")
    void loginEvent_shouldRecordHeartbeatAndBroadcastOnline() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_LOGIN, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(deviceHeartbeatService).recordHeartbeat(deviceId);
        verify(telemetryBroadcastService).broadcastDeviceEvent(deviceId, "LOGIN", true);
    }

    @Test
    @DisplayName("HEARTBEAT event should record heartbeat and broadcast online")
    void heartbeatEvent_shouldRecordHeartbeatAndBroadcastOnline() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_HEARTBEAT, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(deviceHeartbeatService).recordHeartbeat(deviceId);
        verify(telemetryBroadcastService).broadcastDeviceEvent(deviceId, "HEARTBEAT", true);
    }

    @Test
    @DisplayName("DISCONNECT event should mark offline and broadcast offline")
    void disconnectEvent_shouldMarkOfflineAndBroadcastOffline() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_DISCONNECT, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(deviceHeartbeatService).markOffline(deviceId);
        verify(telemetryBroadcastService).broadcastDeviceEvent(deviceId, "DISCONNECT", false);
    }

    @Test
    @DisplayName("ALARM event with code 0x0E should generate EXTERNAL_POWER_LOW alert")
    void alarmEvent_externalPowerLow_shouldGenerateAlert() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x0E, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        // Should record heartbeat (alarm = device is online)
        verify(deviceHeartbeatService).recordHeartbeat(deviceId);

        // Should generate alert
        verify(canonicalAlertService).processAlertEvent(
                eq(machineId),
                eq("EXTERNAL_POWER_LOW"),
                eq("WARNING"),
                eq("External power voltage is low"),
                any(Instant.class),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("ALARM event with code 0x0F should generate EXTERNAL_POWER_CUT alert")
    void alarmEvent_externalPowerCut_shouldGenerateAlert() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x0F, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId),
                eq("EXTERNAL_POWER_CUT"),
                eq("CRITICAL"),
                eq("External power protection triggered — imminent shutdown"),
                any(Instant.class),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("ALARM event with code 0x15 should generate LOW_POWER_SHUTDOWN alert")
    void alarmEvent_lowPowerShutdown_shouldGenerateAlert() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x15, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId),
                eq("LOW_POWER_SHUTDOWN"),
                eq("CRITICAL"),
                eq("Device shutting down due to low battery"),
                any(Instant.class),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("ALARM event with code 0x19 should generate INTERNAL_BATTERY_LOW alert")
    void alarmEvent_internalBatteryLow_shouldGenerateAlert() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x19, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId),
                eq("INTERNAL_BATTERY_LOW"),
                eq("WARNING"),
                eq("Internal backup battery is low"),
                any(Instant.class),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("ALARM event with unmapped code (0x01 SOS) should not generate alert but should record heartbeat")
    void alarmEvent_unmappedCode_shouldNotGenerateAlert() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x01, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        // Should still record heartbeat
        verify(deviceHeartbeatService).recordHeartbeat(deviceId);
        // Should NOT generate an alert (0x01 SOS is not in the map)
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), anyString(), anyString(), anyString(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("ALARM event with null alarmCode should not generate alert")
    void alarmEvent_nullAlarmCode_shouldNotGenerateAlert() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, null, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(deviceHeartbeatService).recordHeartbeat(deviceId);
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), anyString(), anyString(), anyString(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("ALARM event should not generate alert when device has no machine bound")
    void alarmEvent_noMachineBound_shouldNotGenerateAlert() {
        when(deviceResolverService.resolve(deviceId))
                .thenReturn(new DeviceResolverService.DeviceInfo(orgId, null, imei));
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x0E, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(deviceHeartbeatService).recordHeartbeat(deviceId);
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), anyString(), anyString(), anyString(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("ALARM event should not generate alert when device not found")
    void alarmEvent_deviceNotFound_shouldNotGenerateAlert() {
        when(deviceResolverService.resolve(deviceId)).thenReturn(null);
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x0E, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), anyString(), anyString(), anyString(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("ALARM event should broadcast online status")
    void alarmEvent_shouldBroadcastOnline() {
        DeviceEventMessage msg = new DeviceEventMessage(
                deviceId, imei, DeviceEventMessage.EVENT_ALARM, 0x0E, Instant.now()
        );

        consumer.handleDeviceEvent(msg);

        verify(telemetryBroadcastService).broadcastDeviceEvent(deviceId, "ALARM", true);
    }
}
