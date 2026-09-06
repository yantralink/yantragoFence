package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "machine_commands", indexes = {
        @jakarta.persistence.Index(name = "idx_machine_commands_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_machine_commands_machine_id", columnList = "machine_id"),
        @jakarta.persistence.Index(name = "idx_machine_commands_status", columnList = "status"),
        @jakarta.persistence.Index(name = "idx_machine_commands_created_at", columnList = "created_at")
})
public class MachineCommand extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "machine_id", nullable = false)
    private java.util.UUID machineId;

    @Column(name = "device_id")
    private java.util.UUID deviceId;

    @Column(name = "issued_by")
    private java.util.UUID issuedBy;

    @Column(name = "command_type", nullable = false, length = 20)
    private String commandType;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getMachineId() { return machineId; }
    public void setMachineId(java.util.UUID machineId) { this.machineId = machineId; }
    public java.util.UUID getDeviceId() { return deviceId; }
    public void setDeviceId(java.util.UUID deviceId) { this.deviceId = deviceId; }
    public java.util.UUID getIssuedBy() { return issuedBy; }
    public void setIssuedBy(java.util.UUID issuedBy) { this.issuedBy = issuedBy; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
