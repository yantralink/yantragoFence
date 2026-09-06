package com.yantrago.api.dto.machine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CreateMachineRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String serialNumber;

    @Size(max = 100)
    private String model;

    @Pattern(regexp = "ACTIVE|INACTIVE|FAULTY|RETIRED", message = "Status must be one of: ACTIVE, INACTIVE, FAULTY, RETIRED")
    private String status;

    private UUID customerId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
}
