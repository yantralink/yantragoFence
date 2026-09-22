package com.yantrago.gateway.service;

import com.yantrago.gateway.queue.CommandResultProducer;
import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.gateway.tcp.concox.ConcoxV5ProtocolHandler;
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

    // BR05 relay control commands (SMS-compatible ASCII strings)
    // The device firmware interprets:
    //   RELAY,1# → "Cut off the fuel supply" → FuelCut: YES → LED ON (fuel cut indicator)
    //   RELAY,0# → "Restore fuel supply"    → FuelCut: NO  → LED OFF
    // Turn ON  (machine ON)  → RELAY,1# → cut fuel   → FuelCut: YES → LED ON
    // Turn OFF (machine OFF) → RELAY,0# → restore    → FuelCut: NO  → LED OFF
    private static final String RELAY_ON_COMMAND = "RELAY,1#";
    private static final String RELAY_OFF_COMMAND = "RELAY,0#";

    private final DeviceConnectionRegistry connectionRegistry;
    private final CommandResultProducer commandResultProducer;
    private final ConcoxV5ProtocolHandler concoxV5ProtocolHandler;
    private final com.yantrago.gateway.tcp.fencing.FencingEncoder fencingEncoder;
    private final PendingCommandRegistry pendingCommandRegistry;

    public CommandDispatchService(DeviceConnectionRegistry connectionRegistry,
                                    CommandResultProducer commandResultProducer,
                                    ConcoxV5ProtocolHandler concoxV5ProtocolHandler,
                                    com.yantrago.gateway.tcp.fencing.FencingEncoder fencingEncoder,
                                    PendingCommandRegistry pendingCommandRegistry) {
        this.connectionRegistry = connectionRegistry;
        this.commandResultProducer = commandResultProducer;
        this.concoxV5ProtocolHandler = concoxV5ProtocolHandler;
        this.fencingEncoder = fencingEncoder;
        this.pendingCommandRegistry = pendingCommandRegistry;
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

        // Build command packet based on command type and device protocol
        byte[] commandPacket = buildCommandPacket(imei, commandType);
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
            // Register pending command so the ACK can be correlated back
            pendingCommandRegistry.register(imei, commandId);
            // Publish SENT status — ACK will come later when device replies
            commandResultProducer.publishCommandResult(new CommandResultMessage(
                    commandId, CommandResultMessage.STATUS_SENT, 1, null, Instant.now()
            ));
            log.info("Command sent to device: commandId={} imei={} type={}", commandId, imei, commandType);
        } else {
            commandResultProducer.publishCommandResult(new CommandResultMessage(
                    commandId, CommandResultMessage.STATUS_FAILED, 1,
                    "Failed to write to device socket", Instant.now()
            ));
            log.error("Failed to send command to device: commandId={} imei={}", commandId, imei);
        }
    }

    /**
     * Builds the command packet for the correct protocol.
     *
     * Fencing devices (YANTRAGO_FENCING) use FencingEncoder with
     * OP_CMD_ON (0x82) / OP_CMD_OFF (0x83) opcodes.
     * Concox devices use ConcoxV5ProtocolHandler with RELAY,1#/RELAY,0#.
     */
    private byte[] buildCommandPacket(String imei, String commandType) {
        String protocol = connectionRegistry.getProtocolForImei(imei);

        if ("YANTRAGO_FENCING".equals(protocol)) {
            // Fencing protocol commands
            if ("ON".equals(commandType) || "FENCING_ON".equals(commandType)) {
                log.debug("Building fencing ON command for imei={}", imei);
                return fencingEncoder.buildOnCommand();
            } else if ("OFF".equals(commandType) || "FENCING_OFF".equals(commandType)) {
                log.debug("Building fencing OFF command for imei={}", imei);
                return fencingEncoder.buildOffCommand();
            } else {
                log.warn("Unknown fencing command type: {}", commandType);
                return null;
            }
        }

        // Default: Concox V5 protocol commands
        String smsCommand;
        if ("ON".equals(commandType) || "FENCING_ON".equals(commandType)) {
            smsCommand = RELAY_ON_COMMAND;
        } else if ("OFF".equals(commandType) || "FENCING_OFF".equals(commandType)) {
            smsCommand = RELAY_OFF_COMMAND;
        } else {
            log.warn("Unknown command type: {}", commandType);
            return null;
        }

        log.debug("Building Concox 0x80 command packet: type={} smsCommand={}", commandType, smsCommand);
        return concoxV5ProtocolHandler.buildCommandPacket(smsCommand);
    }
}
