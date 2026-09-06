package com.yantrago.api.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class UpdateCustomerRequest {

    @Size(max = 255)
    private String name;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    private String address;
    private Double latitude;
    private Double longitude;
    private Boolean isActive;

    /** Machine to assign to this customer. Set to null to unassign. */
    private UUID assignedMachineId;

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
}
