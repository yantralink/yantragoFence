package com.yantrago.api.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.dto.command.CommandResponse;
import com.yantrago.api.service.CommandService;
import com.yantrago.api.websocket.CommandBroadcastService;
import com.yantrago.shared.queue.AlertTransitionMessage;
import com.yantrago.shared.queue.CommandResultMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes CommandResultMessage from the gateway — reports command lifecycle
 * state transitions (QUEUED, SENT, ACK, DONE, FAILED).
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * This consumer is where the ack/failed transitions are applied to the command record.
 *
 * Command notifications: when a relay ON/OFF command reaches ACK, DONE, or FAILED,
 * an AlertTransitionMessage is written to the event_outbox so the existing
 * notification pipeline (OutboxPublisher → NotificationEventConsumer) can create
 * inbox items and push notifications for the assigned customer's user.
 */
@Component
public class CommandResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommandResultConsumer.class);

    private final CommandService commandService;
    private final CommandBroadcastService commandBroadcastService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CommandResultConsumer(CommandService commandService,
                                 CommandBroadcastService commandBroadcastService,
                                 JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper) {
        this.commandService = commandService;
        this.commandBroadcastService = commandBroadcastService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = QueueNames.COMMAND_RESULT_QUEUE)
    public void handleCommandResult(CommandResultMessage message) {
        log.info("Received command result: commandId={} status={} attemptCount={}",
                message.getCommandId(), message.getStatus(), message.getAttemptCount());

        try {
            CommandResponse response = commandService.transitionCommand(
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
            commandBroadcastService.broadcastCommandStatus(
                    response.getMachineId(), message.getCommandId(),
                    message.getStatus(), message.getAttemptCount(), message.getError()
            );

            // Generate notification for terminal and ACK transitions
            generateCommandNotification(response, message);

        } catch (Exception e) {
            log.error("Failed to process command result for commandId={}: {}",
                    message.getCommandId(), e.getMessage(), e);
            // Don't rethrow — RabbitMQ will not redeliver (avoid poison pill).
            // In production, consider a dead-letter queue.
        }
    }

    /**
     * Writes an AlertTransitionMessage to the event_outbox for command lifecycle
     * transitions that should generate notifications (ACK, DONE, FAILED).
     *
     * The existing notification pipeline (OutboxPublisher → NotificationEventConsumer)
     * will pick up the outbox event and create inbox items for the assigned
     * customer's user.
     */
    private void generateCommandNotification(CommandResponse command, CommandResultMessage message) {
        String status = command.getStatus();
        String alertType;
        String severity;
        String incidentState;
        String notificationMessage;

        switch (status) {
            case "ACK":
                alertType = "COMMAND_ACK";
                severity = "INFO";
                incidentState = "ACK";
                notificationMessage = String.format("Command %s acknowledged by device",
                        command.getCommandType());
                break;
            case "DONE":
                if ("ON".equals(command.getCommandType())) {
                    alertType = "MACHINE_ON";
                } else if ("OFF".equals(command.getCommandType())) {
                    alertType = "MACHINE_OFF";
                } else {
                    alertType = "COMMAND_ACK";
                }
                severity = "INFO";
                incidentState = "DONE";
                notificationMessage = String.format("Machine %s command completed",
                        command.getCommandType());
                break;
            case "FAILED":
                alertType = "COMMAND_FAILED";
                severity = "WARNING";
                incidentState = "FAILED";
                notificationMessage = message.getError() != null
                        ? String.format("Command %s failed: %s", command.getCommandType(), message.getError())
                        : String.format("Command %s failed", command.getCommandType());
                break;
            default:
                // QUEUED, SENT, PENDING — no notification
                return;
        }

        try {
            writeCommandNotificationOutbox(command, alertType, severity, incidentState,
                    notificationMessage, message.getError());
            log.info("Generated command notification: commandId={} alertType={} state={} machine={}",
                    command.getId(), alertType, incidentState, command.getMachineId());
        } catch (Exception e) {
            log.error("Failed to generate command notification for commandId={}: {}",
                    command.getId(), e.getMessage(), e);
        }
    }

    /**
     * Writes an AlertTransitionMessage to the event_outbox table.
     * The OutboxPublisher will publish it to the notification queue.
     */
    private void writeCommandNotificationOutbox(CommandResponse command,
                                                 String alertType,
                                                 String severity,
                                                 String incidentState,
                                                 String message,
                                                 String error) throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        AlertTransitionMessage transition = new AlertTransitionMessage();
        transition.setSchemaVersion(1);
        transition.setEventId(eventId);
        transition.setCorrelationId(command.getId()); // use command ID as correlation ID
        transition.setOccurredAt(occurredAt);
        transition.setAlertId(command.getId()); // use command ID as alertId for traceability
        transition.setOrganizationId(command.getOrganizationId());
        transition.setMachineId(command.getMachineId());
        transition.setDeviceId(command.getDeviceId());
        transition.setAlertType(alertType);
        transition.setSeverity(severity);
        transition.setIncidentState(incidentState);
        transition.setMessage(message);
        transition.setOccurrenceCount(1);
        // For COMMAND_FAILED, pass the error as observedValue for template rendering
        if (error != null && "COMMAND_FAILED".equals(alertType)) {
            // observedValue is Double, so we can't pass a string directly.
            // The NotificationEventConsumer will use the message field as fallback.
        }

        String payload = objectMapper.writeValueAsString(transition);

        jdbcTemplate.update(
                "INSERT INTO event_outbox (organization_id, event_type, schema_version, " +
                        "aggregate_id, event_id, payload) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
                command.getOrganizationId(),
                "ALERT_TRANSITION",
                1,
                command.getId(),
                eventId,
                payload
        );

        log.debug("Wrote command notification outbox event eventId={} commandId={} alertType={} state={}",
                eventId, command.getId(), alertType, incidentState);
    }
}
