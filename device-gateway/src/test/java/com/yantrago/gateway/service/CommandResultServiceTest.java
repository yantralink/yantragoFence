package com.yantrago.gateway.service;

import com.yantrago.gateway.queue.CommandResultProducer;
import com.yantrago.shared.queue.CommandResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandResultService.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 * Per AGENTS.md rule 6: all commands must be auditable.
 */
class CommandResultServiceTest {

    private CommandResultProducer commandResultProducer;
    private PendingCommandRegistry pendingCommandRegistry;
    private CommandResultService commandResultService;

    private final String imei = "123456789012345";
    private final UUID commandId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        commandResultProducer = mock(CommandResultProducer.class);
        pendingCommandRegistry = mock(PendingCommandRegistry.class);
        commandResultService = new CommandResultService(commandResultProducer, pendingCommandRegistry);
    }

    @Test
    @DisplayName("processCommandReply should publish ACK then DONE on success")
    void processCommandReply_shouldPublishAckThenDoneOnSuccess() {
        when(pendingCommandRegistry.remove(imei)).thenReturn(commandId);

        commandResultService.processCommandReply(imei, true, "OK", true);

        ArgumentCaptor<CommandResultMessage> captor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandResultProducer, times(2)).publishCommandResult(captor.capture());

        List<CommandResultMessage> messages = captor.getAllValues();
        assertEquals(CommandResultMessage.STATUS_ACK, messages.get(0).getStatus());
        assertEquals(CommandResultMessage.STATUS_DONE, messages.get(1).getStatus());
        assertNull(messages.get(0).getError());
        assertNull(messages.get(1).getError());
    }

    @Test
    @DisplayName("processCommandReply should publish only FAILED on failure")
    void processCommandReply_shouldPublishFailedOnFailure() {
        when(pendingCommandRegistry.remove(imei)).thenReturn(commandId);

        commandResultService.processCommandReply(imei, false, "Device error", false);

        ArgumentCaptor<CommandResultMessage> captor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandResultProducer, times(1)).publishCommandResult(captor.capture());
        assertEquals(CommandResultMessage.STATUS_FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getError().contains("Device reported command failure"));
    }

    @Test
    @DisplayName("processCommandReply should ignore reply when no pending command")
    void processCommandReply_shouldIgnoreWhenNoPendingCommand() {
        when(pendingCommandRegistry.remove(imei)).thenReturn(null);

        commandResultService.processCommandReply(imei, true, "OK", true);

        verify(commandResultProducer, never()).publishCommandResult(any());
    }
}
