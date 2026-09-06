package com.yantrago.api.queue;

import com.yantrago.api.service.TelemetryService;
import com.yantrago.api.websocket.TelemetryBroadcastService;
import com.yantrago.shared.queue.QueueNames;
import com.yantrago.shared.queue.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consumes TelemetryMessage from the gateway and persists voltage, battery,
 * and GSM readings via TelemetryService (batch insert into partitioned tables).
 *
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    private final TelemetryService telemetryService;
    private final TelemetryBroadcastService telemetryBroadcastService;

    public TelemetryConsumer(TelemetryService telemetryService,
                             TelemetryBroadcastService telemetryBroadcastService) {
        this.telemetryService = telemetryService;
        this.telemetryBroadcastService = telemetryBroadcastService;
    }

    @RabbitListener(queues = QueueNames.TELEMETRY_QUEUE)
    public void handleTelemetry(TelemetryMessage message) {
        log.debug("Received telemetry: deviceId={} imei={} voltage={} battery={} gsm={}",
                message.getDeviceId(), message.getImei(),
                message.getVoltage(), message.getBattery(), message.getGsmSignal());

        try {
            LocalDateTime recordedAt = message.getTimestamp() != null
                    ? LocalDateTime.ofInstant(message.getTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now();

            UUID id = UUID.randomUUID();
            // organizationId and machineId would be resolved by the service layer
            // from the device lookup. For now, we pass the deviceId and let the
            // service resolve the org/machine. The batch insert expects:
            // [id, orgId, deviceId, machineId, imei, value, recordedAt, receivedAt]

            // We batch a single message into a list for the batch insert API.
            // In high-volume production, the consumer would accumulate messages
            // and flush in batches (similar to LocationPersistenceService).

            if (message.getVoltage() != null) {
                List<Object[]> voltageRows = new ArrayList<>();
                voltageRows.add(new Object[]{
                        id, null, message.getDeviceId(), null, message.getImei(),
                        message.getVoltage(), recordedAt, LocalDateTime.now()
                });
                telemetryService.storeVoltageReadings(voltageRows);
            }

            if (message.getBattery() != null) {
                List<Object[]> batteryRows = new ArrayList<>();
                batteryRows.add(new Object[]{
                        UUID.randomUUID(), null, message.getDeviceId(), null, message.getImei(),
                        message.getBattery(), recordedAt, LocalDateTime.now()
                });
                telemetryService.storeBatteryReadings(batteryRows);
            }

            if (message.getGsmSignal() != null) {
                List<Object[]> gsmRows = new ArrayList<>();
                gsmRows.add(new Object[]{
                        UUID.randomUUID(), null, message.getDeviceId(), null, message.getImei(),
                        message.getGsmSignal(), recordedAt, LocalDateTime.now()
                });
                telemetryService.storeGsmReadings(gsmRows);
            }

            log.debug("Persisted telemetry for device={}", message.getDeviceId());

            // Broadcast to WebSocket subscribers via /topic/telemetry/{machineId}
            // machineId is null for now (would be resolved from device binding in production)
            telemetryBroadcastService.broadcastTelemetry(
                    null, message.getDeviceId(),
                    message.getVoltage(), message.getBattery(), message.getGsmSignal()
            );
        } catch (Exception e) {
            log.error("Failed to persist telemetry for deviceId={}: {}",
                    message.getDeviceId(), e.getMessage(), e);
        }
    }
}
