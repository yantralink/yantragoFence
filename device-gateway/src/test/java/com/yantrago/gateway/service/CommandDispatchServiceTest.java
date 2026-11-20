package com.yantrago.gateway.service;

import com.yantrago.gateway.queue.CommandResultProducer;
import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.gateway.tcp.concox.ConcoxV5ProtocolHandler;
import com.yantrago.shared.queue.CommandMessage;
import com.yantrago.shared.queue.CommandResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandDispatchService.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * Per AGENTS.md rule 6: all commands must be auditable.
 * Per AGENTS.md rule 17: use shared message contracts.
 */
class CommandDispatchServiceTest {

    private DeviceConnectionRegistry connectionRegistry;
    private CommandResultProducer commandResultProducer;
    private ConcoxV5ProtocolHandler concoxV5ProtocolHandler;
    private com.yantrago.gateway.tcp.fencing.FencingEncoder fencingEncoder;
    private PendingCommandRegistry pendingCommandRegistry;
    private CommandDispatchService commandDispatchService;

    private final UUID commandId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final String imei = "123456789012345";

    @BeforeEach
    void setUp() {
        connectionRegistry = mock(DeviceConnectionRegistry.class);
        commandResultProducer = mock(CommandResultProducer.class);
        concoxV5ProtocolHandler = mock(ConcoxV5ProtocolHandler.class);
        fencingEncoder = mock(com.yantrago.gateway.tcp.fencing.FencingEncoder.class);
        pendingCommandRegistry = mock(PendingCommandRegistry.class);
        commandDispatchService = new CommandDispatchService(connectionRegistry, commandResultProducer, concoxV5ProtocolHandler, fencingEncoder, pendingCommandRegistry);
    }

    @Test
    @DisplayName("dispatchCommand should publish FAILED when device is offline")
    void dispatchCommand_shouldFailWhenDeviceOffline() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(false);
        CommandMessage message = new CommandMessage(commandId, machineId, imei, "ON", Instant.now());

        commandDispatchService.dispatchCommand(message);

        ArgumentCaptor<CommandResultMessage> captor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandResultProducer).publishCommandResult(captor.capture());
        assertEquals(CommandResultMessage.STATUS_FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getError().contains("Device not online"));
    }

    @Test
    @DisplayName("dispatchCommand should publish SENT when command is successfully sent to device")
    void dispatchCommand_shouldPublishSentWhenSuccessful() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(true);
        when(concoxV5ProtocolHandler.buildCommandPacket(anyString())).thenReturn(new byte[]{(byte) 0x80, 0x01});
        when(connectionRegistry.sendCommand(eq(imei), any(byte[].class))).thenReturn(true);
        CommandMessage message = new CommandMessage(commandId, machineId, imei, "ON", Instant.now());

        commandDispatchService.dispatchCommand(message);

        ArgumentCaptor<CommandResultMessage> captor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandResultProducer).publishCommandResult(captor.capture());
        assertEquals(CommandResultMessage.STATUS_SENT, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getAttemptCount());
        assertNull(captor.getValue().getError());
    }

    @Test
    @DisplayName("dispatchCommand should publish FAILED when socket write fails")
    void dispatchCommand_shouldPublishFailedWhenSocketWriteFails() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(true);
        when(concoxV5ProtocolHandler.buildCommandPacket(anyString())).thenReturn(new byte[]{(byte) 0x80, 0x01});
        when(connectionRegistry.sendCommand(eq(imei), any(byte[].class))).thenReturn(false);
        CommandMessage message = new CommandMessage(commandId, machineId, imei, "ON", Instant.now());

        commandDispatchService.dispatchCommand(message);

        ArgumentCaptor<CommandResultMessage> captor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandResultProducer).publishCommandResult(captor.capture());
        assertEquals(CommandResultMessage.STATUS_FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getError().contains("Failed to write"));
    }

    @Test
    @DisplayName("dispatchCommand should publish FAILED for unknown command type")
    void dispatchCommand_shouldFailForUnknownCommandType() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(true);
        CommandMessage message = new CommandMessage(commandId, machineId, imei, "UNKNOWN", Instant.now());

        commandDispatchService.dispatchCommand(message);

        ArgumentCaptor<CommandResultMessage> captor = ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandResultProducer).publishCommandResult(captor.capture());
        assertEquals(CommandResultMessage.STATUS_FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getError().contains("Unknown command type"));
    }

    @Test
    @DisplayName("dispatchCommand should send command packet via connection registry for ON command")
    void dispatchCommand_shouldSendPacketForOnCommand() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(true);
        when(concoxV5ProtocolHandler.buildCommandPacket(anyString())).thenReturn(new byte[]{(byte) 0x80, 0x01});
        when(connectionRegistry.sendCommand(eq(imei), any(byte[].class))).thenReturn(true);
        CommandMessage message = new CommandMessage(commandId, machineId, imei, "ON", Instant.now());

        commandDispatchService.dispatchCommand(message);

        verify(connectionRegistry).sendCommand(eq(imei), any(byte[].class));
    }

    @Test
    @DisplayName("Concox ON releases the relay (RELAY,0#) and OFF engages it (RELAY,1#)")
    void dispatchCommand_shouldMapOnOffToRelayPolarity() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(true);
        when(concoxV5ProtocolHandler.buildCommandPacket(anyString())).thenReturn(new byte[]{(byte) 0x80, 0x01});
        when(connectionRegistry.sendCommand(eq(imei), any(byte[].class))).thenReturn(true);

        commandDispatchService.dispatchCommand(new CommandMessage(commandId, machineId, imei, "ON", Instant.now()));
        verify(concoxV5ProtocolHandler).buildCommandPacket("RELAY,0#");

        commandDispatchService.dispatchCommand(new CommandMessage(commandId, machineId, imei, "OFF", Instant.now()));
        verify(concoxV5ProtocolHandler).buildCommandPacket("RELAY,1#");
    }

    @Test
    @DisplayName("Fencing-protocol devices keep their dedicated ON/OFF opcodes")
    void dispatchCommand_shouldKeepFencingProtocolMapping() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(true);
        when(connectionRegistry.getProtocolForImei(imei)).thenReturn("YANTRAGO_FENCING");
        when(fencingEncoder.buildOnCommand()).thenReturn(new byte[]{(byte) 0x82});
        when(fencingEncoder.buildOffCommand()).thenReturn(new byte[]{(byte) 0x83});
        when(connectionRegistry.sendCommand(eq(imei), any(byte[].class))).thenReturn(true);

        commandDispatchService.dispatchCommand(new CommandMessage(commandId, machineId, imei, "ON", Instant.now()));
        verify(fencingEncoder).buildOnCommand();

        commandDispatchService.dispatchCommand(new CommandMessage(commandId, machineId, imei, "OFF", Instant.now()));
        verify(fencingEncoder).buildOffCommand();
        verifyNoInteractions(concoxV5ProtocolHandler);
    }

    @Test
    @DisplayName("dispatchCommand should send command packet via connection registry for OFF command")
    void dispatchCommand_shouldSendPacketForOffCommand() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(true);
        when(concoxV5ProtocolHandler.buildCommandPacket(anyString())).thenReturn(new byte[]{(byte) 0x80, 0x01});
        when(connectionRegistry.sendCommand(eq(imei), any(byte[].class))).thenReturn(true);
        CommandMessage message = new CommandMessage(commandId, machineId, imei, "OFF", Instant.now());

        commandDispatchService.dispatchCommand(message);

        verify(connectionRegistry).sendCommand(eq(imei), any(byte[].class));
    }

    @Test
    @DisplayName("dispatchCommand should not send packet when device is offline")
    void dispatchCommand_shouldNotSendWhenOffline() {
        when(connectionRegistry.isDeviceOnline(imei)).thenReturn(false);
        CommandMessage message = new CommandMessage(commandId, machineId, imei, "ON", Instant.now());

        commandDispatchService.dispatchCommand(message);

        verify(connectionRegistry, never()).sendCommand(any(), any());
    }
}
