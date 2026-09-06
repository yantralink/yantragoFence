package com.yantrago.shared.queue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Message published by the backend to the command exchange and consumed
 * by the device gateway. Instructs the gateway to send a command (ON/OFF)
 * to a specific machine/device over the TCP protocol.
 */
public class CommandMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID commandId;
    private UUID machineId;
    private String imei;
    private String commandType; // ON | OFF
    private Instant timestamp;

    public CommandMessage() {
    }

    public CommandMessage(UUID commandId, UUID machineId, String imei, String commandType, Instant timestamp) {
        this.commandId = commandId;
        this.machineId = machineId;
        this.imei = imei;
        this.commandType = commandType;
        this.timestamp = timestamp;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public void setCommandId(UUID commandId) {
        this.commandId = commandId;
    }

    public UUID getMachineId() {
        return machineId;
    }

    public void setMachineId(UUID machineId) {
        this.machineId = machineId;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
