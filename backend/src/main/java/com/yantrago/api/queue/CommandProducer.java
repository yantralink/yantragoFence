package com.yantrago.api.queue;

import com.yantrago.shared.queue.CommandMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes CommandMessage to the command exchange for the device gateway to consume.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 17: uses shared module message contracts.
 */
@Component
public class CommandProducer {

    private static final Logger log = LoggerFactory.getLogger(CommandProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public CommandProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes a command message to the command exchange.
     *
     * @param commandId the UUID of the machine_commands record
     * @param machineId the target machine UUID
     * @param imei the device IMEI for the gateway to route the command
     * @param commandType ON or OFF
     */
    public void sendCommand(UUID commandId, UUID machineId, String imei, String commandType) {
        CommandMessage message = new CommandMessage(
                commandId,
                machineId,
                imei,
                commandType,
                Instant.now()
        );

        rabbitTemplate.convertAndSend(
                QueueNames.COMMAND_EXCHANGE,
                QueueNames.COMMAND_ROUTING_KEY,
                message
        );

        log.info("Published command id={} machineId={} type={} imei={}",
                commandId, machineId, commandType, imei);
    }
}
