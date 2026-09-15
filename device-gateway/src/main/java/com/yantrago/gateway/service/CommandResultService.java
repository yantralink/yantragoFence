package com.yantrago.gateway.service;

import com.yantrago.gateway.queue.CommandResultProducer;
import com.yantrago.shared.queue.CommandResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Command result service — processes ACK/reply from devices and publishes
 * CommandResultMessage to the backend via RabbitMQ.
 *
 * Implements the VehicleCommandService interface used by ConcoxV5ProtocolHandler.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 * This service is where the ACK/FAILED transitions are published.
 *
 * Per AGENTS.md rule 6: all commands must be auditable.
 */
@Service
public class CommandResultService implements VehicleCommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandResultService.class);

    private final CommandResultProducer commandResultProducer;
    private final PendingCommandRegistry pendingCommandRegistry;

    public CommandResultService(CommandResultProducer commandResultProducer,
                                  PendingCommandRegistry pendingCommandRegistry) {
        this.commandResultProducer = commandResultProducer;
        this.pendingCommandRegistry = pendingCommandRegistry;
    }

    @Override
    public void updateLockStateFromHeartbeat(String imei, boolean fuelCutOff) {
        log.debug("Lock state from heartbeat: imei={} fuelCutOff={}", imei, fuelCutOff);
        // In a full implementation, this would update the device state in Redis
        // and potentially trigger an alert if the state changed unexpectedly.
        // The command result is not published here since this is a heartbeat, not a command reply.
    }

    @Override
    public void processCommandReply(String imei, boolean success, String resultText, boolean fuelCutOff) {
        log.info("Command reply: imei={} success={} resultText={} fuelCutOff={}",
                imei, success, resultText, fuelCutOff);

        String status = success ? CommandResultMessage.STATUS_ACK : CommandResultMessage.STATUS_FAILED;
        String error = success ? null : "Device reported command failure: " + resultText;

        // Look up the pending commandId by IMEI
        UUID commandId = pendingCommandRegistry.remove(imei);
        if (commandId == null) {
            log.warn("No pending command for imei={} — reply ignored (success={} resultText={})",
                    imei, success, resultText);
            return;
        }

        // Publish the ACK/FAILED result to the backend via RabbitMQ
        publishResult(commandId, status, error);
        log.info("Command reply processed: imei={} commandId={} status={} error={}", imei, commandId, status, error);
    }

    /**
     * Publishes a command result for a specific command ID.
     * Called when the commandId is known (e.g. from a pending command map).
     */
    public void publishResult(UUID commandId, String status, String error) {
        CommandResultMessage message = new CommandResultMessage(
                commandId, status, 1, error, Instant.now()
        );
        commandResultProducer.publishCommandResult(message);
        log.info("Published command result: commandId={} status={}", commandId, status);
    }
}
