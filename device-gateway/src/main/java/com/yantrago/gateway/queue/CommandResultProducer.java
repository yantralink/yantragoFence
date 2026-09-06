package com.yantrago.gateway.queue;

import com.yantrago.shared.queue.CommandResultMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes CommandResultMessage to the command.result exchange for the backend to consume.
 *
 * The gateway publishes command lifecycle updates (SENT, ACK, DONE, FAILED) as the
 * command progresses through the state machine.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 * Per AGENTS.md rule 6: all commands must be auditable.
 * Per AGENTS.md rule 17: use shared message contracts.
 */
@Component
public class CommandResultProducer {

    private static final Logger log = LoggerFactory.getLogger(CommandResultProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public CommandResultProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishCommandResult(CommandResultMessage message) {
        log.info("Publishing command result: commandId={} status={} attemptCount={}",
                message.getCommandId(), message.getStatus(), message.getAttemptCount());
        rabbitTemplate.convertAndSend(
                QueueNames.COMMAND_RESULT_EXCHANGE,
                QueueNames.COMMAND_RESULT_ROUTING_KEY,
                message
        );
    }
}
