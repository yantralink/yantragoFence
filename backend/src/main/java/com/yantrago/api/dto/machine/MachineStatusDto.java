package com.yantrago.api.dto.machine;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight status DTO for machine online/offline state.
 */
public class MachineStatusDto {

    private UUID id;
    private String status;
    private Boolean isOnline;
    private LocalDateTime lastSeenAt;
    private String fencingState;

    public MachineStatusDto() {}

    public MachineStatusDto(UUID id, String status, Boolean isOnline, LocalDateTime lastSeenAt) {
        this(id, status, isOnline, lastSeenAt, "UNKNOWN");
    }

    public MachineStatusDto(UUID id, String status, Boolean isOnline,
                            LocalDateTime lastSeenAt, String fencingState) {
        this.id = id;
        this.status = status;
        this.isOnline = isOnline;
        this.lastSeenAt = lastSeenAt;
        this.fencingState = fencingState;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getFencingState() { return fencingState; }
    public void setFencingState(String fencingState) { this.fencingState = fencingState; }
}
