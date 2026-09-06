package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "machine_assignments", indexes = {
        @jakarta.persistence.Index(name = "idx_machine_assignments_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_machine_assignments_machine_id", columnList = "machine_id"),
        @jakarta.persistence.Index(name = "idx_machine_assignments_customer_id", columnList = "customer_id")
})
public class MachineAssignment extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "machine_id", nullable = false)
    private java.util.UUID machineId;

    @Column(name = "customer_id", nullable = false)
    private java.util.UUID customerId;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getMachineId() { return machineId; }
    public void setMachineId(java.util.UUID machineId) { this.machineId = machineId; }
    public java.util.UUID getCustomerId() { return customerId; }
    public void setCustomerId(java.util.UUID customerId) { this.customerId = customerId; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public LocalDateTime getUnassignedAt() { return unassignedAt; }
    public void setUnassignedAt(LocalDateTime unassignedAt) { this.unassignedAt = unassignedAt; }
}
