package com.yantrago.gateway.queue;

import com.yantrago.shared.queue.DeviceEventMessage;
import com.yantrago.shared.queue.LocationMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes device lifecycle events (LOGIN, HEARTBEAT, DISCONNECT) and
 * location updates to the backend via RabbitMQ.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 17: use shared message contracts.
 */
@Component
public class DeviceEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public DeviceEventProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishDeviceEvent(DeviceEventMessage message) {
        log.info("Publishing device event: deviceId={} imei={} type={}",
                message.getDeviceId(), message.getImei(), message.getEventType());
        rabbitTemplate.convertAndSend(
                QueueNames.DEVICE_EVENT_EXCHANGE,
                QueueNames.DEVICE_EVENT_ROUTING_KEY,
                message
        );
    }

    public void publishLocation(LocationMessage message) {
        log.debug("Publishing location: deviceId={} lat={} lon={} speed={}",
                message.getDeviceId(), message.getLatitude(), message.getLongitude(), message.getSpeed());
        rabbitTemplate.convertAndSend(
                QueueNames.LOCATION_EXCHANGE,
                QueueNames.LOCATION_ROUTING_KEY,
                message
        );
    }
}
