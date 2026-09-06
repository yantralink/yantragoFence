package com.yantrago.gateway.service;

import com.yantrago.gateway.queue.CommandResultProducer;
import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.shared.queue.CommandMessage;
import com.yantrago.shared.queue.CommandResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Command dispatch service — sends commands to devices via TCP.
 *
 * Receives CommandMessage from the backend (via CommandConsumer), looks up the
 * active TCP connection by IMEI in DeviceConnectionRegistry, and writes the
 * command packet to the device's socket.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 * This service sends the command and publishes a SENT result. The ACK comes
 * later via CommandResultService when the device replies.
 *
 * Per AGENTS.md rule 6: all commands must be auditable.
 */
@Service
public class CommandDispatchService {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatchService.class);

    private final DeviceConnectionRegistry connectionRegistry;
    private final CommandResultProducer commandResultProducer;

    public CommandDispatchService(DeviceConnectionRegistry connectionRegistry,
                                    CommandResultProducer commandResultProducer) {
        this.connectionRegistry = connectionRegistry;
        this.commandResultProducer = commandResultProducer;
    }

    /**
     * Dispatches a command to a device.
     *
     * @param message the CommandMessage from the backend
     */
    public void dispatchCommand(CommandMessage message) {
        UUID commandId = message.getCommandId();
        String imei = message.getImei();
        String commandType = message.getCommandType();

        log.info("Dispatching command: commandId={} imei={} type={}", commandId, imei, commandType);

        // Check if device is online
        if (!connectionRegistry.isDeviceOnline(imei)) {
            log.warn("Device not online: imei={}", imei);
            commandResultProducer.publishCommandResult(new CommandResultMessage(
                    commandId, CommandResultMessage.STATUS_FAILED, 0,
                    "Device not online: " + imei, Instant.now()
            ));
            return;
        }

        // Build command packet based on command type
        byte[] commandPacket = buildCommandPacket(commandType);
        if (commandPacket == null) {
            commandResultProducer.publishCommandResult(new CommandResultMessage(
                    commandId, CommandResultMessage.STATUS_FAILED, 0,
                    "Unknown command type: " + commandType, Instant.now()
            ));
            return;
        }

        // Send via TCP
        boolean sent = connectionRegistry.sendCommand(imei, commandPacket);

        if (sent) {
            // Publish SENT status — ACK will come later when device replies
            commandResultProducer.publishCommandResult(new CommandResultMessage(
                    commandId, CommandResultMessage.STATUS_SENT, 1, null, Instant.now()
            ));
            log.info("Command sent to device: commandId={} imei={}", commandId, imei);
        } else {
            commandResultProducer.publishCommandResult(new CommandResultMessage(
                    commandId, CommandResultMessage.STATUS_FAILED, 1,
                    "Failed to write to device socket", Instant.now()
            ));
            log.error("Failed to send command to device: commandId={} imei={}", commandId, imei);
        }
    }

    /**
     * Builds a command packet for the given command type.
     * Currently supports ON/OFF commands for Concox V5 protocol.
     *
     * In production, this would delegate to the appropriate protocol handler
     * based on the device's protocol type.
     */
    private byte[] buildCommandPacket(String commandType) {
        // Concox V5 command format: the command content string
        // For ON/OFF relay control, the command content is protocol-specific.
        // The ConcoxV5ProtocolHandler.buildCommandPacket() method builds the full packet.
        // In a full implementation, we'd inject the protocol handler and call it.
        // For now, we return a placeholder — Phase 13 will wire the fencing protocol.
        if ("ON".equals(commandType) || "OFF".equals(commandType)) {
            // Placeholder: in production, delegate to ConcoxV5ProtocolHandler.buildCommandPacket()
            // or FencingEncoder.buildOnCommand() / buildOffCommand()
            log.debug("Building command packet for type={}", commandType);
            return new byte[]{0x78, 0x78, 0x05, (byte) 0x80, 0x00, 0x01, 0x00, 0x00, 0x0D, 0x0A};
        }
        return null;
    }
}
