package com.yantrago.api.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CreateDeviceRequest {

    @NotBlank
    @Size(max = 20)
    private String imei;

    @Size(max = 30)
    private String simNumber;

    @NotBlank
    @Pattern(regexp = "CONCOX_V5|JT808|FENCING", message = "Protocol must be one of: CONCOX_V5, JT808, FENCING")
    private String protocolType;

    @Size(max = 50)
    private String firmwareVersion;

    private UUID machineId;

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }
    public String getProtocolType() { return protocolType; }
    public void setProtocolType(String protocolType) { this.protocolType = protocolType; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
}
