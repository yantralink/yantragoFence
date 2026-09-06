package com.yantrago.api.dto.customer;

import java.util.UUID;

public class CustomerDto {

    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private Double latitude;
    private Double longitude;
    private Boolean isActive;
    private UUID assignedMachineId;
    private String assignedMachineName;
    private String assignedMachineCode;

    public CustomerDto() {}

    public CustomerDto(UUID id, UUID organizationId, UUID userId, String name, String email, String phone,
                       String address, Double latitude, Double longitude, Boolean isActive) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = isActive;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public UUID getAssignedMachineId() { return assignedMachineId; }
    public void setAssignedMachineId(UUID assignedMachineId) { this.assignedMachineId = assignedMachineId; }
    public String getAssignedMachineName() { return assignedMachineName; }
    public void setAssignedMachineName(String assignedMachineName) { this.assignedMachineName = assignedMachineName; }
    public String getAssignedMachineCode() { return assignedMachineCode; }
    public void setAssignedMachineCode(String assignedMachineCode) { this.assignedMachineCode = assignedMachineCode; }
}
