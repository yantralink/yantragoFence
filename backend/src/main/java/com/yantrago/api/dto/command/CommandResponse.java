package com.yantrago.api.dto.command;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a machine command — includes full lifecycle state.
 */
public class CommandResponse {

    private UUID id;
    private UUID organizationId;
    private UUID machineId;
    private UUID deviceId;
    private UUID issuedBy;
    private String commandType;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public CommandResponse() {}

    public CommandResponse(UUID id, UUID organizationId, UUID machineId, UUID deviceId, UUID issuedBy,
                           String commandType, String status, Integer attemptCount, Integer maxAttempts,
                           String lastError, LocalDateTime createdAt, LocalDateTime updatedAt,
                           LocalDateTime completedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.machineId = machineId;
        this.deviceId = deviceId;
        this.issuedBy = issuedBy;
        this.commandType = commandType;
        this.status = status;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getIssuedBy() { return issuedBy; }
    public void setIssuedBy(UUID issuedBy) { this.issuedBy = issuedBy; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
