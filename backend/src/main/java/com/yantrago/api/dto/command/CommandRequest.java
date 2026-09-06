package com.yantrago.api.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Request to issue a command to a machine (ON/OFF).
 */
public class CommandRequest {

    @NotNull
    private UUID machineId;

    @NotBlank
    @Pattern(regexp = "ON|OFF", message = "Command type must be ON or OFF")
    private String commandType;

    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }
}
