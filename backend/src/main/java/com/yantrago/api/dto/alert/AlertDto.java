package com.yantrago.api.dto.alert;

import java.time.LocalDateTime;
import java.util.UUID;

public class AlertDto {

    private UUID id;
    private UUID organizationId;
    private UUID alertRuleId;
    private UUID machineId;
    private UUID deviceId;
    private String alertType;
    private String severity;
    private String message;
    private Boolean isAcknowledged;
    private UUID acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;

    public AlertDto() {}

    public AlertDto(UUID id, UUID organizationId, UUID alertRuleId, UUID machineId, UUID deviceId,
                    String alertType, String severity, String message, Boolean isAcknowledged,
                    UUID acknowledgedBy, LocalDateTime acknowledgedAt, LocalDateTime triggeredAt,
                    LocalDateTime createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.alertRuleId = alertRuleId;
        this.machineId = machineId;
        this.deviceId = deviceId;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.isAcknowledged = isAcknowledged;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
        this.triggeredAt = triggeredAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getAlertRuleId() { return alertRuleId; }
    public void setAlertRuleId(UUID alertRuleId) { this.alertRuleId = alertRuleId; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Boolean getIsAcknowledged() { return isAcknowledged; }
    public void setIsAcknowledged(Boolean isAcknowledged) { this.isAcknowledged = isAcknowledged; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(UUID acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
