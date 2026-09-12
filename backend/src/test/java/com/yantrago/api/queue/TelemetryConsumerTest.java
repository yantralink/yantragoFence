package com.yantrago.api.queue;

import com.yantrago.api.model.Alert;
import com.yantrago.api.service.AlertGenerationService;
import com.yantrago.api.service.DeviceResolverService;
import com.yantrago.api.service.TelemetryService;
import com.yantrago.api.websocket.TelemetryBroadcastService;
import com.yantrago.shared.queue.TelemetryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for TelemetryConsumer.
 *
 * Verifies that telemetry messages from the gateway are:
 * - Persisted to time-series tables (voltage_readings, battery_readings, gsm_readings)
 * - Used to update the latest device state on the devices table (battery_pct, charging, gsm_signal)
 * - Broadcast to WebSocket subscribers with charging status
 * - Evaluated for alert rules after ingestion
 *
 * Per AGENTS.md rule 7: organization_id is resolved from the device record, not from the message.
 */
class TelemetryConsumerTest {

    private TelemetryService telemetryService;
    private TelemetryBroadcastService telemetryBroadcastService;
    private DeviceResolverService deviceResolverService;
    private AlertGenerationService alertGenerationService;
    private JdbcTemplate jdbcTemplate;
    private TelemetryConsumer consumer;

    private final UUID deviceId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final String imei = "123456789012345";

    @BeforeEach
    void setUp() {
        telemetryService = mock(TelemetryService.class);
        telemetryBroadcastService = mock(TelemetryBroadcastService.class);
        deviceResolverService = mock(DeviceResolverService.class);
        alertGenerationService = mock(AlertGenerationService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        consumer = new TelemetryConsumer(
                telemetryService, telemetryBroadcastService,
                deviceResolverService, alertGenerationService, jdbcTemplate
        );

        // Default: device resolves successfully
        when(deviceResolverService.resolve(deviceId))
                .thenReturn(new DeviceResolverService.DeviceInfo(orgId, machineId, imei));
    }

    @Test
    @DisplayName("handleTelemetry should persist battery reading and update device state")
    void handleTelemetry_shouldPersistBatteryAndUpdateDeviceState() {
        TelemetryMessage msg = new TelemetryMessage(
                deviceId, imei, null, 60.0, 3, true, Instant.now()
        );

        consumer.handleTelemetry(msg);

        // Verify battery reading persisted to time-series table
        verify(telemetryService).storeBatteryReadings(any());

        // Verify device state updated with battery, charging, gsm, voltage
        verify(jdbcTemplate).update(
                eq("UPDATE devices SET " +
                        "battery_pct = COALESCE(?, battery_pct), " +
                        "charging = COALESCE(?, charging), " +
                        "gsm_signal = COALESCE(?, gsm_signal), " +
                        "voltage = COALESCE(?, voltage), " +
                        "last_telemetry_at = ?, " +
                        "updated_at = now() " +
                        "WHERE id = ?"),
                eq(60.0), eq(true), eq(3), eq(null), any(), eq(deviceId)
        );
    }

    @Test
    @DisplayName("handleTelemetry should broadcast telemetry with charging status")
    void handleTelemetry_shouldBroadcastTelemetryWithCharging() {
        TelemetryMessage msg = new TelemetryMessage(
                deviceId, imei, null, 60.0, 3, true, Instant.now()
        );

        consumer.handleTelemetry(msg);

        verify(telemetryBroadcastService).broadcastTelemetry(
                eq(machineId), eq(deviceId),
                eq(null), eq(60.0), eq(3), eq(true)
        );
    }

    @Test
    @DisplayName("handleTelemetry should skip when device not found")
    void handleTelemetry_shouldSkipWhenDeviceNotFound() {
        when(deviceResolverService.resolve(deviceId)).thenReturn(null);
        TelemetryMessage msg = new TelemetryMessage(
                deviceId, imei, null, 60.0, 3, true, Instant.now()
        );

        consumer.handleTelemetry(msg);

        verify(telemetryService, never()).storeBatteryReadings(any());
        verify(jdbcTemplate, never()).update(anyString(), (Object) any());
    }

    @Test
    @DisplayName("handleTelemetry should skip device state update when battery is null but still update charging")
    void handleTelemetry_shouldUpdateChargingEvenWhenBatteryNull() {
        TelemetryMessage msg = new TelemetryMessage(
                deviceId, imei, null, null, null, false, Instant.now()
        );

        consumer.handleTelemetry(msg);

        // Battery/GSM readings should NOT be persisted (null values)
        verify(telemetryService, never()).storeBatteryReadings(any());
        verify(telemetryService, never()).storeGsmReadings(any());

        // But device state should still be updated (COALESCE preserves existing values,
        // only charging=false is new). voltage=null is also passed (COALESCE preserves).
        verify(jdbcTemplate).update(
                anyString(),
                eq(null), eq(false), eq(null), eq(null), any(), eq(deviceId)
        );
    }

    @Test
    @DisplayName("handleTelemetry should evaluate alert rules after ingestion")
    void handleTelemetry_shouldEvaluateAlertRules() {
        TelemetryMessage msg = new TelemetryMessage(
                deviceId, imei, null, 60.0, 3, true, Instant.now()
        );

        consumer.handleTelemetry(msg);

        verify(alertGenerationService).evaluateAlertsForMachine(orgId, machineId);
    }

    @Test
    @DisplayName("handleTelemetry should continue if alert evaluation fails (telemetry still persisted)")
    void handleTelemetry_shouldContinueIfAlertEvaluationFails() {
        TelemetryMessage msg = new TelemetryMessage(
                deviceId, imei, null, 60.0, 3, true, Instant.now()
        );
        doThrow(new RuntimeException("Alert eval failed"))
                .when(alertGenerationService).evaluateAlertsForMachine(orgId, machineId);

        consumer.handleTelemetry(msg);

        // Telemetry should still be persisted despite alert evaluation failure
        verify(telemetryService).storeBatteryReadings(any());
        verify(jdbcTemplate).update(anyString(), eq(60.0), eq(true), eq(3), eq(null), any(), eq(deviceId));
    }

    @Test
    @DisplayName("handleTelemetry should persist voltage reading and update device voltage state")
    void handleTelemetry_shouldPersistVoltageAndUpdateDeviceState() {
        TelemetryMessage msg = new TelemetryMessage(
                deviceId, imei, 12.22, null, null, null, Instant.now()
        );

        consumer.handleTelemetry(msg);

        // Verify voltage reading persisted to time-series table
        verify(telemetryService).storeVoltageReadings(any());
        // Battery/GSM readings should NOT be persisted (null values)
        verify(telemetryService, never()).storeBatteryReadings(any());
        verify(telemetryService, never()).storeGsmReadings(any());

        // Verify device state updated with voltage (COALESCE preserves other fields)
        verify(jdbcTemplate).update(
                eq("UPDATE devices SET " +
                        "battery_pct = COALESCE(?, battery_pct), " +
                        "charging = COALESCE(?, charging), " +
                        "gsm_signal = COALESCE(?, gsm_signal), " +
                        "voltage = COALESCE(?, voltage), " +
                        "last_telemetry_at = ?, " +
                        "updated_at = now() " +
                        "WHERE id = ?"),
                eq(null), eq(null), eq(null), eq(12.22), any(), eq(deviceId)
        );
    }
}
