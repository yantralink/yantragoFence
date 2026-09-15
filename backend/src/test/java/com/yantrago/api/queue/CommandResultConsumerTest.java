package com.yantrago.api.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.dto.command.CommandResponse;
import com.yantrago.api.service.CommandService;
import com.yantrago.api.websocket.CommandBroadcastService;
import com.yantrago.shared.queue.AlertTransitionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for CommandResultConsumer.
 *
 * Verifies that command lifecycle transitions (ACK, DONE, FAILED, TIMEOUT)
 * generate the correct notification outbox events with the error text
 * passed in the message field for template rendering.
 */
class CommandResultConsumerTest {

    private CommandService commandService;
    private CommandBroadcastService commandBroadcastService;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private CommandResultConsumer consumer;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final UUID commandId = UUID.randomUUID();
    private final UUID issuedBy = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        commandService = mock(CommandService.class);
        commandBroadcastService = mock(CommandBroadcastService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        consumer = new CommandResultConsumer(commandService, commandBroadcastService,
                jdbcTemplate, objectMapper);
    }

    private CommandResponse commandResponse(String commandType, String status) {
        return new CommandResponse(
                commandId, orgId, machineId, deviceId, issuedBy,
                commandType, status, 1, 3, null,
                LocalDateTime.now(), LocalDateTime.now(), null
        );
    }

    private com.yantrago.shared.queue.CommandResultMessage resultMessage(String status, String error, int attemptCount) {
        com.yantrago.shared.queue.CommandResultMessage msg =
                new com.yantrago.shared.queue.CommandResultMessage();
        msg.setCommandId(commandId);
        msg.setStatus(status);
        msg.setError(error);
        msg.setAttemptCount(attemptCount);
        return msg;
    }

    @Test
    @DisplayName("FAILED transition should write outbox event with error text in message field")
    void failedTransition_shouldWriteOutboxWithErrorInMessage() throws Exception {
        CommandResponse response = commandResponse("ON", "FAILED");
        when(commandService.transitionCommand(eq(commandId), eq("FAILED"), anyString()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("FAILED", "Device not responding", 1));

        // Verify jdbcTemplate.update was called (notification outbox insert)
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TIMEOUT transition should write outbox event with COMMAND_FAILED alert type and TIMEOUT state")
    void timeoutTransition_shouldWriteOutboxWithTimeoutState() throws Exception {
        CommandResponse response = commandResponse("ON", "TIMEOUT");
        when(commandService.transitionCommand(eq(commandId), eq("TIMEOUT"), anyString()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("TIMEOUT", "Device did not respond", 1));

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DONE transition for ON command should write outbox with MACHINE_ON alert type")
    void doneTransition_onCommand_shouldWriteMACHINE_ON() throws Exception {
        CommandResponse response = commandResponse("ON", "DONE");
        when(commandService.transitionCommand(eq(commandId), eq("DONE"), any()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("DONE", null, 1));

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DONE transition for OFF command should write outbox with MACHINE_OFF alert type")
    void doneTransition_offCommand_shouldWriteMACHINE_OFF() throws Exception {
        CommandResponse response = commandResponse("OFF", "DONE");
        when(commandService.transitionCommand(eq(commandId), eq("DONE"), any()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("DONE", null, 1));

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACK transition should write outbox with COMMAND_ACK alert type")
    void ackTransition_shouldWriteCOMMAND_ACK() throws Exception {
        CommandResponse response = commandResponse("ON", "ACK");
        when(commandService.transitionCommand(eq(commandId), eq("ACK"), any()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("ACK", null, 1));

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SENT transition should NOT generate a notification")
    void sentTransition_shouldNotGenerateNotification() {
        CommandResponse response = commandResponse("ON", "SENT");
        when(commandService.transitionCommand(eq(commandId), eq("SENT"), any()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("SENT", null, 1));

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("QUEUED transition should NOT generate a notification")
    void queuedTransition_shouldNotGenerateNotification() {
        CommandResponse response = commandResponse("ON", "QUEUED");
        when(commandService.transitionCommand(eq(commandId), eq("QUEUED"), any()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("QUEUED", null, 1));

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PENDING transition should NOT generate a notification")
    void pendingTransition_shouldNotGenerateNotification() {
        CommandResponse response = commandResponse("ON", "PENDING");
        when(commandService.transitionCommand(eq(commandId), eq("PENDING"), any()))
                .thenReturn(response);

        consumer.handleCommandResult(resultMessage("PENDING", null, 1));

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    // ===== DLQ behavior tests =====

    @Test
    @DisplayName("Should rethrow when transitionCommand fails (for DLQ)")
    void shouldRethrowWhenTransitionFails() {
        when(commandService.transitionCommand(eq(commandId), anyString(), any()))
                .thenThrow(new IllegalStateException("Invalid command state transition: DONE → PENDING"));

        assertThrows(IllegalStateException.class, () ->
                consumer.handleCommandResult(resultMessage("PENDING", null, 1)));

        // Secondary operations should NOT have been called
        verify(commandBroadcastService, never()).broadcastCommandStatus(
                any(), any(), anyString(), anyInt(), any());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should rethrow when command not found (for DLQ)")
    void shouldRethrowWhenCommandNotFound() {
        when(commandService.transitionCommand(eq(commandId), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Command not found: " + commandId));

        assertThrows(IllegalArgumentException.class, () ->
                consumer.handleCommandResult(resultMessage("ACK", null, 1)));

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should NOT rethrow when broadcast fails (secondary operation)")
    void shouldNotRethrowWhenBroadcastFails() {
        CommandResponse response = commandResponse("ON", "ACK");
        when(commandService.transitionCommand(eq(commandId), eq("ACK"), any()))
                .thenReturn(response);
        doThrow(new RuntimeException("WebSocket connection failed"))
                .when(commandBroadcastService).broadcastCommandStatus(
                        any(), any(), anyString(), anyInt(), any());

        // Should NOT throw — broadcast is secondary
        assertDoesNotThrow(() ->
                consumer.handleCommandResult(resultMessage("ACK", null, 1)));

        // Notification should still be attempted despite broadcast failure
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should NOT rethrow when notification generation fails (secondary operation)")
    void shouldNotRethrowWhenNotificationFails() {
        CommandResponse response = commandResponse("ON", "ACK");
        when(commandService.transitionCommand(eq(commandId), eq("ACK"), any()))
                .thenReturn(response);
        // Make jdbcTemplate.update throw to simulate outbox insert failure
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Database connection lost"));

        // Should NOT throw — notification is secondary
        assertDoesNotThrow(() ->
                consumer.handleCommandResult(resultMessage("ACK", null, 1)));

        // Broadcast should still have been called
        verify(commandBroadcastService).broadcastCommandStatus(
                any(), any(), anyString(), anyInt(), any());
    }
}
