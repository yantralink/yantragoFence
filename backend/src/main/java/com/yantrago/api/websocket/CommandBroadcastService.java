package com.yantrago.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcasts command status updates to subscribers via STOMP WebSocket.
 *
 * Clients subscribe to /topic/command/{machineId} to receive real-time
 * command lifecycle updates (PENDING → QUEUED → SENT → ACK → DONE/FAILED).
 *
 * Called by CommandResultConsumer when a CommandResultMessage is received from the gateway.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * This service broadcasts the actual state transitions as they occur.
 */
@Service
public class CommandBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(CommandBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public CommandBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts a command status update to /topic/command/{machineId}.
     *
     * @param machineId the machine whose command status changed
     * @param commandId the command UUID
     * @param status the new status (PENDING, QUEUED, SENT, ACK, DONE, FAILED)
     * @param attemptCount the current attempt count
     * @param error error message if status is FAILED (optional)
     */
    public void broadcastCommandStatus(UUID machineId, UUID commandId,
                                        String status, int attemptCount, String error) {
        if (machineId == null) {
            log.debug("Skipping command broadcast: machineId is null (command={})", commandId);
            return;
        }

        String destination = "/topic/command/" + machineId;
        Map<String, Object> payload = Map.of(
                "commandId", commandId.toString(),
                "machineId", machineId.toString(),
                "status", status,
                "attemptCount", attemptCount,
                "error", error,
                "timestamp", LocalDateTime.now().toString()
        );

        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Broadcasted command status to {} status={}", destination, status);
    }
}
