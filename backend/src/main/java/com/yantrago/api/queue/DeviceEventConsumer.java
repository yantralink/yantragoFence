package com.yantrago.api.queue;

import com.yantrago.api.service.DeviceHeartbeatService;
import com.yantrago.api.websocket.TelemetryBroadcastService;
import com.yantrago.shared.queue.DeviceEventMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes DeviceEventMessage from the gateway — handles LOGIN, HEARTBEAT,
 * and DISCONNECT events to update device online/offline state.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 */
@Component
public class DeviceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventConsumer.class);

    private final DeviceHeartbeatService deviceHeartbeatService;
    private final TelemetryBroadcastService telemetryBroadcastService;

    public DeviceEventConsumer(DeviceHeartbeatService deviceHeartbeatService,
                                TelemetryBroadcastService telemetryBroadcastService) {
        this.deviceHeartbeatService = deviceHeartbeatService;
        this.telemetryBroadcastService = telemetryBroadcastService;
    }

    @RabbitListener(queues = QueueNames.DEVICE_EVENT_QUEUE)
    public void handleDeviceEvent(DeviceEventMessage message) {
        log.info("Received device event: deviceId={} imei={} type={} timestamp={}",
                message.getDeviceId(), message.getImei(), message.getEventType(), message.getTimestamp());

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
}
