package com.yantrago.api.dto.command;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight status DTO for a command.
 */
public class CommandStatusDto {

    private UUID id;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String lastError;
    private LocalDateTime completedAt;

    public CommandStatusDto() {}

    public CommandStatusDto(UUID id, String status, Integer attemptCount, Integer maxAttempts,
                            String lastError, LocalDateTime completedAt) {
        this.id = id;
        this.status = status;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.lastError = lastError;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
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
