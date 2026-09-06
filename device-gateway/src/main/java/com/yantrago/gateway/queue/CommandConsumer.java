package com.yantrago.gateway.queue;

import com.yantrago.gateway.service.CommandDispatchService;
import com.yantrago.shared.queue.CommandMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes CommandMessage from the command queue (published by the backend)
 * and dispatches the command to the device via CommandDispatchService.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 * Per AGENTS.md rule 6: all commands must be auditable.
 */
@Component
public class CommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommandConsumer.class);

    private final CommandDispatchService commandDispatchService;

    public CommandConsumer(CommandDispatchService commandDispatchService) {
        this.commandDispatchService = commandDispatchService;
    }

    @RabbitListener(queues = QueueNames.COMMAND_QUEUE)
    public void handleCommand(CommandMessage message) {
        log.info("Received command: commandId={} machineId={} imei={} type={}",
                message.getCommandId(), message.getMachineId(),
                message.getImei(), message.getCommandType());

        try {
            commandDispatchService.dispatchCommand(message);
        } catch (Exception e) {
            log.error("Failed to dispatch command commandId={}: {}",
                    message.getCommandId(), e.getMessage(), e);
            // Don't rethrow — avoid poison pill. In production, use a dead-letter queue.
        }
    }
}
