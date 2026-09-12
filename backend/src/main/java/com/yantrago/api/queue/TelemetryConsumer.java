package com.yantrago.api.queue;

import com.yantrago.api.service.AlertGenerationService;
import com.yantrago.api.service.DeviceResolverService;
import com.yantrago.api.service.TelemetryService;
import com.yantrago.api.websocket.TelemetryBroadcastService;
import com.yantrago.shared.queue.QueueNames;
import com.yantrago.shared.queue.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consumes TelemetryMessage from the gateway and persists voltage, battery,
 * and GSM readings via TelemetryService (batch insert into partitioned tables).
 * Also updates the latest device state (battery_pct, charging, gsm_signal) on
 * the devices table for fast lookup by the REST API and mobile app.
 *
 * Per AGENTS.md rule 7: organization_id is resolved from the devices table, not
 * from the message payload or JWT (no HTTP context in RabbitMQ consumer).
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    private final TelemetryService telemetryService;
    private final TelemetryBroadcastService telemetryBroadcastService;
    private final DeviceResolverService deviceResolverService;
    private final AlertGenerationService alertGenerationService;
    private final JdbcTemplate jdbcTemplate;

    public TelemetryConsumer(TelemetryService telemetryService,
                             TelemetryBroadcastService telemetryBroadcastService,
                             DeviceResolverService deviceResolverService,
                             AlertGenerationService alertGenerationService,
                             JdbcTemplate jdbcTemplate) {
        this.telemetryService = telemetryService;
        this.telemetryBroadcastService = telemetryBroadcastService;
        this.deviceResolverService = deviceResolverService;
        this.alertGenerationService = alertGenerationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @RabbitListener(queues = QueueNames.TELEMETRY_QUEUE)
    public void handleTelemetry(TelemetryMessage message) {
        log.debug("Received telemetry: deviceId={} imei={} voltage={} battery={} gsm={}",
                message.getDeviceId(), message.getImei(),
                message.getVoltage(), message.getBattery(), message.getGsmSignal());

        try {
            // Resolve device metadata (org_id, machine_id, imei) from devices table.
            // RabbitMQ consumers have no HTTP/JWT context, so we cannot use OwnerContextService.
            DeviceResolverService.DeviceInfo deviceInfo = deviceResolverService.resolve(message.getDeviceId());
            if (deviceInfo == null || deviceInfo.organizationId() == null) {
                log.warn("Cannot persist telemetry: device not found or missing org_id for deviceId={}",
                        message.getDeviceId());
                return;
            }

            UUID orgId = deviceInfo.organizationId();
            UUID machineId = deviceInfo.machineId();
            String imei = deviceInfo.imei() != null ? deviceInfo.imei() : message.getImei();

            LocalDateTime recordedAt = message.getTimestamp() != null
                    ? LocalDateTime.ofInstant(message.getTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now();

            // Batch insert expects rows as:
            // [id, orgId, deviceId, machineId, imei, value, recordedAt, receivedAt]

            if (message.getVoltage() != null) {
                List<Object[]> voltageRows = new ArrayList<>();
                voltageRows.add(new Object[]{
                        UUID.randomUUID(), orgId, message.getDeviceId(), machineId, imei,
                        message.getVoltage(), recordedAt, LocalDateTime.now()
                });
                telemetryService.storeVoltageReadings(voltageRows);
            }

            if (message.getBattery() != null) {
                List<Object[]> batteryRows = new ArrayList<>();
                batteryRows.add(new Object[]{
                        UUID.randomUUID(), orgId, message.getDeviceId(), machineId, imei,
                        message.getBattery(), recordedAt, LocalDateTime.now()
                });
                telemetryService.storeBatteryReadings(batteryRows);
            }

            if (message.getGsmSignal() != null) {
                List<Object[]> gsmRows = new ArrayList<>();
                gsmRows.add(new Object[]{
                        UUID.randomUUID(), orgId, message.getDeviceId(), machineId, imei,
                        message.getGsmSignal(), recordedAt, LocalDateTime.now()
                });
                telemetryService.storeGsmReadings(gsmRows);
            }

            log.debug("Persisted telemetry for device={}", message.getDeviceId());

            // Update latest device state on the devices table for fast API lookup.
            // Only update fields that are present in the message (null = no data).
            updateDeviceState(message.getDeviceId(), message.getBattery(),
                    message.getCharging(), message.getGsmSignal(),
                    message.getVoltage(), recordedAt);

            // Broadcast to WebSocket subscribers via /topic/telemetry/{machineId}
            telemetryBroadcastService.broadcastTelemetry(
                    machineId, message.getDeviceId(),
                    message.getVoltage(), message.getBattery(), message.getGsmSignal(),
                    message.getCharging()
            );

            // Evaluate alert rules after successful ingestion.
            // Per notification plan Phase 2: connect telemetry evaluation after
            // successful ingestion, using current valid observations.
            try {
                alertGenerationService.evaluateAlertsForMachine(orgId, machineId);
            } catch (Exception ae) {
                log.warn("Alert evaluation failed for machine={} (telemetry still persisted): {}",
                        machineId, ae.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to persist telemetry for deviceId={}: {}",
                    message.getDeviceId(), e.getMessage(), e);
        }
    }

    /**
     * Updates the latest device state (battery_pct, charging, gsm_signal,
     * voltage, last_telemetry_at) on the devices table. Uses COALESCE so
     * that null fields in the message do not overwrite existing values.
     *
     * Per AGENTS.md rule 7: this is a RabbitMQ consumer with no HTTP/JWT context;
     * the deviceId is trusted because it was resolved from the device's IMEI
     * by the gateway's device mapping cache.
     */
    private void updateDeviceState(UUID deviceId, Double battery,
                                    Boolean charging, Integer gsmSignal,
                                    Double voltage, LocalDateTime recordedAt) {
        try {
            jdbcTemplate.update(
                    "UPDATE devices SET " +
                            "battery_pct = COALESCE(?, battery_pct), " +
                            "charging = COALESCE(?, charging), " +
                            "gsm_signal = COALESCE(?, gsm_signal), " +
                            "voltage = COALESCE(?, voltage), " +
                            "last_telemetry_at = ?, " +
                            "updated_at = now() " +
                            "WHERE id = ?",
                    battery, charging, gsmSignal, voltage, recordedAt, deviceId
            );
            log.debug("Updated device state: deviceId={} battery={} charging={} gsm={} voltage={}",
                    deviceId, battery, charging, gsmSignal, voltage);
        } catch (Exception e) {
            log.error("Failed to update device state for deviceId={}: {}", deviceId, e.getMessage());
        }
    }
}
