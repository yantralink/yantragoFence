package com.yantrago.api.dto.machine;

import java.time.LocalDateTime;
import java.util.UUID;

public class MachineDto {

    private UUID id;
    private String machineId;
    private UUID organizationId;
    private UUID customerId;
    private String name;
    private String serialNumber;
    private String model;
    private String status;
    private Boolean isOnline;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Device fields (from joined devices table)
    private String imei;
    private String simNumber;
    private String protocolType;
    private String firmwareVersion;
    // Resolved names for display
    private String organizationName;
    private String customerName;

    public MachineDto() {}

    public MachineDto(UUID id, String machineId, UUID organizationId, UUID customerId, String name,
                      String serialNumber, String model, String status, Boolean isOnline,
                      LocalDateTime lastSeenAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.machineId = machineId;
        this.organizationId = organizationId;
        this.customerId = customerId;
        this.name = name;
        this.serialNumber = serialNumber;
        this.model = model;
        this.status = status;
        this.isOnline = isOnline;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }
    public String getProtocolType() { return protocolType; }
    public void setProtocolType(String protocolType) { this.protocolType = protocolType; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
