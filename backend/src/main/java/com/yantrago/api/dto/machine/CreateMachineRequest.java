package com.yantrago.api.dto.machine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateMachineRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 20)
    private String imei;

    @Size(max = 30)
    private String simNumber;

    @NotBlank
    @Size(max = 20)
    private String protocolType;

    @Size(max = 100)
    private String serialNumber;

    @Size(max = 100)
    private String model;

    @Size(max = 50)
    private String firmwareVersion;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }
    public String getProtocolType() { return protocolType; }
    public void setProtocolType(String protocolType) { this.protocolType = protocolType; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
}
