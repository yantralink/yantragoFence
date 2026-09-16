package com.yantrago.api.queue;

import com.yantrago.api.service.CanonicalAlertService;
import com.yantrago.api.service.DeviceHeartbeatService;
import com.yantrago.api.service.DeviceResolverService;
import com.yantrago.api.websocket.TelemetryBroadcastService;
import com.yantrago.shared.queue.DeviceEventMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes DeviceEventMessage from the gateway — handles LOGIN, HEARTBEAT,
 * DISCONNECT, and ALARM events.
 *
 * ALARM events carry a BR05 alarm code (0x00–0x23) which is mapped to an
 * alert type and forwarded to CanonicalAlertService for incident creation.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 7: organization_id is resolved from the device record,
 * never from the message payload.
 */
@Component
public class DeviceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventConsumer.class);

    /**
     * Maps BR05 protocol alarm codes to YantraGO alert types.
     * Only codes that warrant alert generation are mapped; others are logged
     * but do not create incidents (e.g. 0x00 Normal).
     *
     * ACC state codes (0xFE on / 0xFF off) are handled separately as a
     * state pair: 0xFE opens an INFO ACC_ON incident (notifies "engine
     * started"), 0xFF resolves it — ACC off is a return to normal, not a
     * problem, so it does not open its own incident.
     */
    private static final Map<Integer, AlarmMapping> ALARM_CODE_MAP = Map.of(
            0x0E, new AlarmMapping("EXTERNAL_POWER_LOW", "WARNING",
                    "External power voltage is low"),
            0x0F, new AlarmMapping("EXTERNAL_POWER_CUT", "CRITICAL",
                    "External power protection triggered — imminent shutdown"),
            0x15, new AlarmMapping("LOW_POWER_SHUTDOWN", "CRITICAL",
                    "Device shutting down due to low battery"),
            0x19, new AlarmMapping("INTERNAL_BATTERY_LOW", "WARNING",
                    "Internal backup battery is low")
    );

    private static final int ALARM_ACC_ON = 0xFE;
    private static final int ALARM_ACC_OFF = 0xFF;

    private final DeviceHeartbeatService deviceHeartbeatService;
    private final TelemetryBroadcastService telemetryBroadcastService;
    private final CanonicalAlertService canonicalAlertService;
    private final DeviceResolverService deviceResolverService;

    public DeviceEventConsumer(DeviceHeartbeatService deviceHeartbeatService,
                                TelemetryBroadcastService telemetryBroadcastService,
                                CanonicalAlertService canonicalAlertService,
                                DeviceResolverService deviceResolverService) {
        this.deviceHeartbeatService = deviceHeartbeatService;
        this.telemetryBroadcastService = telemetryBroadcastService;
        this.canonicalAlertService = canonicalAlertService;
        this.deviceResolverService = deviceResolverService;
    }

    @RabbitListener(queues = QueueNames.DEVICE_EVENT_QUEUE)
    public void handleDeviceEvent(DeviceEventMessage message) {
        log.info("Received device event: deviceId={} imei={} type={} alarmCode={} timestamp={}",
                message.getDeviceId(), message.getImei(), message.getEventType(),
                message.getAlarmCode() != null
                        ? String.format("0x%02X", message.getAlarmCode()) : "null",
                message.getTimestamp());

        try {
            boolean isOnline = switch (message.getEventType()) {
                case DeviceEventMessage.EVENT_LOGIN, DeviceEventMessage.EVENT_HEARTBEAT -> {
                    deviceHeartbeatService.recordHeartbeat(message.getDeviceId());
                    yield true;
                }
                case DeviceEventMessage.EVENT_DISCONNECT -> {
                    deviceHeartbeatService.markOffline(message.getDeviceId());
                    yield false;
                }
                case DeviceEventMessage.EVENT_ALARM -> {
                    // Alarm events carry a BR05 alarm code — record heartbeat (device is online)
                    deviceHeartbeatService.recordHeartbeat(message.getDeviceId());
                    // Generate canonical alert from the alarm code
                    handleAlarmCode(message);
                    yield true;
                }
                default -> {
                    log.warn("Unknown device event type: {}", message.getEventType());
                    yield false;
                }
            };

            // Broadcast device online/offline event to WebSocket subscribers
            telemetryBroadcastService.broadcastDeviceEvent(
                    message.getDeviceId(), message.getEventType(), isOnline
            );
        } catch (Exception e) {
            log.error("Failed to process device event for deviceId={}: {}",
                    message.getDeviceId(), e.getMessage(), e);
        }
    }

    /**
     * Maps a BR05 alarm code to an alert type and generates a canonical alert
     * via CanonicalAlertService. Unmapped alarm codes are logged but do not
     * create incidents.
     *
     * Per AGENTS.md rule 7: machineId and orgId are resolved from the device
     * record, never from the message payload.
     */
    private void handleAlarmCode(DeviceEventMessage message) {
        Integer alarmCode = message.getAlarmCode();
        if (alarmCode == null) {
            log.warn("ALARM event received without alarmCode: deviceId={}", message.getDeviceId());
            return;
        }

        // Resolve machineId from the device record (never from message payload)
        UUID deviceId = message.getDeviceId();
        if (deviceId == null) {
            log.warn("ALARM event with null deviceId: alarmCode=0x{}", String.format("%02X", alarmCode));
            return;
        }

        DeviceResolverService.DeviceInfo deviceInfo = deviceResolverService.resolve(deviceId);
        if (deviceInfo == null || deviceInfo.machineId() == null) {
            log.warn("Cannot generate alarm alert: device not found or no machine bound: deviceId={}", deviceId);
            return;
        }

        // ACC state pair: 0xFE opens the ACC_ON incident, 0xFF closes it.
        if (alarmCode == ALARM_ACC_ON || alarmCode == ALARM_ACC_OFF) {
            handleAccAlarm(alarmCode, deviceInfo, message);
            return;
        }

        AlarmMapping mapping = ALARM_CODE_MAP.get(alarmCode);
        if (mapping == null) {
            log.info("Unmapped alarm code 0x{} from deviceId={} — no alert generated",
                    String.format("%02X", alarmCode), message.getDeviceId());
            return;
        }

        UUID machineId = deviceInfo.machineId();
        Instant triggeredAt = message.getTimestamp() != null ? message.getTimestamp() : Instant.now();

        try {
            canonicalAlertService.processAlertEvent(
                    machineId,
                    mapping.alertType(),
                    mapping.severity(),
                    mapping.message(),
                    triggeredAt,
                    null,   // observedValue — alarm codes don't carry a numeric value
                    null,   // observedUnit
                    null    // sourceEventId — alarm events are device-initiated
            );
            log.info("Generated {} alert for machine={} from alarm code 0x{}",
                    mapping.alertType(), machineId, String.format("%02X", alarmCode));
        } catch (Exception e) {
            log.error("Failed to generate alert from alarm code 0x{} for machine={}: {}",
                    String.format("%02X", alarmCode), machineId, e.getMessage());
        }
    }

    /**
     * Handles the BR05 ACC alarm pair (0xFE on / 0xFF off). ACC on opens an
     * INFO ACC_ON incident (which notifies via the outbox); ACC off resolves
     * any open ACC_ON incident. Repeated 0xFE while already open increments
     * the occurrence count via processAlertEvent deduplication.
     */
    private void handleAccAlarm(int alarmCode, DeviceResolverService.DeviceInfo deviceInfo,
                                 DeviceEventMessage message) {
        UUID machineId = deviceInfo.machineId();
        Instant triggeredAt = message.getTimestamp() != null ? message.getTimestamp() : Instant.now();

        try {
            if (alarmCode == ALARM_ACC_ON) {
                canonicalAlertService.processAlertEvent(
                        machineId,
                        "ACC_ON",
                        "INFO",
                        "Ignition ON — engine started",
                        triggeredAt,
                        null, null, null
                );
                log.info("Generated ACC_ON alert for machine={} (engine started)", machineId);
            } else {
                canonicalAlertService.resolveIncident(
                        deviceInfo.organizationId(),
                        machineId,
                        "ACC_ON",
                        "Ignition OFF — engine stopped"
                );
                log.info("Resolved ACC_ON incident for machine={} (engine stopped)", machineId);
            }
        } catch (Exception e) {
            log.error("Failed to handle ACC alarm 0x{} for machine={}: {}",
                    String.format("%02X", alarmCode), machineId, e.getMessage());
        }
    }

    /**
     * Immutable mapping from a BR05 alarm code to an alert type.
     */
    private record AlarmMapping(String alertType, String severity, String message) {}
}
