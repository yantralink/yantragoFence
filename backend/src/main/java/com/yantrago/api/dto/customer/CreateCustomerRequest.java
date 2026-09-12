package com.yantrago.api.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CreateCustomerRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 50)
    private String phone;

    @Size(max = 255)
    private String email;

    private String address;
    private Double latitude;
    private Double longitude;

    /**
     * Required when the caller is super_admin (no org in JWT).
     * Ignored for org-scoped users — their org comes from the JWT per rule 7/8.
     */
    private UUID organizationId;

    /** Machine to assign to this customer (optional). UUID of the machine. */
    private UUID assignedMachineId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getAssignedMachineId() { return assignedMachineId; }
    public void setAssignedMachineId(UUID assignedMachineId) { this.assignedMachineId = assignedMachineId; }
}
