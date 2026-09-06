package com.yantrago.api.queue;

import com.yantrago.api.service.CommandService;
import com.yantrago.api.websocket.CommandBroadcastService;
import com.yantrago.shared.queue.CommandResultMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes CommandResultMessage from the gateway — reports command lifecycle
 * state transitions (QUEUED, SENT, ACK, DONE, FAILED).
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * This consumer is where the ack/failed transitions are applied to the command record.
 */
@Component
public class CommandResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommandResultConsumer.class);

    private final CommandService commandService;
    private final CommandBroadcastService commandBroadcastService;

    public CommandResultConsumer(CommandService commandService,
                                 CommandBroadcastService commandBroadcastService) {
        this.commandService = commandService;
        this.commandBroadcastService = commandBroadcastService;
    }

    @RabbitListener(queues = QueueNames.COMMAND_RESULT_QUEUE)
    public void handleCommandResult(CommandResultMessage message) {
        log.info("Received command result: commandId={} status={} attemptCount={}",
                message.getCommandId(), message.getStatus(), message.getAttemptCount());

        try {
            commandService.transitionCommand(
                    message.getCommandId(),
                    message.getStatus(),
                    message.getError()
            );

            if (message.getAttemptCount() > 0) {
                commandService.recordAttempt(
                        message.getCommandId(),
                        message.getAttemptCount(),
                        message.getStatus(),
                        message.getError()
                );
            }

            // Broadcast command status update to WebSocket subscribers
            // machineId is null here — in production, resolve from the command record
            commandBroadcastService.broadcastCommandStatus(
                    null, message.getCommandId(),
                    message.getStatus(), message.getAttemptCount(), message.getError()
            );
        } catch (Exception e) {
            log.error("Failed to process command result for commandId={}: {}",
                    message.getCommandId(), e.getMessage(), e);
            // Don't rethrow — RabbitMQ will not redeliver (avoid poison pill).
            // In production, consider a dead-letter queue.
        }
    }
}
