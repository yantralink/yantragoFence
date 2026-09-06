package com.yantrago.gateway.queue;

import com.yantrago.shared.queue.QueueNames;
import com.yantrago.shared.queue.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes TelemetryMessage to the telemetry exchange for the backend to consume.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 17: use shared message contracts.
 */
@Component
public class TelemetryProducer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public TelemetryProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTelemetry(TelemetryMessage message) {
        log.debug("Publishing telemetry: deviceId={} imei={} voltage={} battery={} gsm={}",
                message.getDeviceId(), message.getImei(),
                message.getVoltage(), message.getBattery(), message.getGsmSignal());
        rabbitTemplate.convertAndSend(
                QueueNames.TELEMETRY_EXCHANGE,
                QueueNames.TELEMETRY_ROUTING_KEY,
                message
        );
    }
}
