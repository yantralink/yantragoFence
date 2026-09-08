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
    // Note: BR05 relay is inverted (low-side switch) — "cut fuel" closes the relay, "restore" opens it.
    // Turn ON  (machine ON)  → RELAY,1# → relay closes → LED/machine ON
    // Turn OFF (machine OFF) → RELAY,0# → relay opens  → LED/machine OFF
    private static final String RELAY_ON_COMMAND = "RELAY,1#";
    private static final String RELAY_OFF_COMMAND = "RELAY,0#";

    private final DeviceConnectionRegistry connectionRegistry;
    private final CommandResultProducer commandResultProducer;
    private final ConcoxV5ProtocolHandler concoxV5ProtocolHandler;

    public CommandDispatchService(DeviceConnectionRegistry connectionRegistry,
                                    CommandResultProducer commandResultProducer,
                                    ConcoxV5ProtocolHandler concoxV5ProtocolHandler) {
        this.connectionRegistry = connectionRegistry;
        this.commandResultProducer = commandResultProducer;
        this.concoxV5ProtocolHandler = concoxV5ProtocolHandler;
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
     * Builds a 0x80 Online Instruction command packet for the BR05/Concox V5 protocol.
     *
     * ON  → DYD=00 (relay connected → machine ON)
     * OFF → DYD=01 (relay disconnected → machine OFF)
     *
     * The packet is built by ConcoxV5ProtocolHandler.buildCommandPacket() which
     * constructs the full 0x80 packet with server flags, ASCII command content,
     * language, serial number, and CRC.
     */
    private byte[] buildCommandPacket(String commandType) {
        String smsCommand;
        if ("ON".equals(commandType) || "FENCING_ON".equals(commandType)) {
            smsCommand = RELAY_ON_COMMAND;
        } else if ("OFF".equals(commandType) || "FENCING_OFF".equals(commandType)) {
            smsCommand = RELAY_OFF_COMMAND;
        } else {
            log.warn("Unknown command type: {}", commandType);
            return null;
        }

        log.debug("Building 0x80 command packet: type={} smsCommand={}", commandType, smsCommand);
        return concoxV5ProtocolHandler.buildCommandPacket(smsCommand);
    }
}
