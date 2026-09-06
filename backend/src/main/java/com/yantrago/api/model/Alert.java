package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts", indexes = {
        @jakarta.persistence.Index(name = "idx_alerts_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_alerts_machine_id", columnList = "machine_id"),
        @jakarta.persistence.Index(name = "idx_alerts_triggered_at", columnList = "triggered_at")
})
public class Alert extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "alert_rule_id")
    private java.util.UUID alertRuleId;

    @Column(name = "machine_id", nullable = false)
    private java.util.UUID machineId;

    @Column(name = "device_id")
    private java.util.UUID deviceId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "is_acknowledged", nullable = false)
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_by")
    private java.util.UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getAlertRuleId() { return alertRuleId; }
    public void setAlertRuleId(java.util.UUID alertRuleId) { this.alertRuleId = alertRuleId; }
    public java.util.UUID getMachineId() { return machineId; }
    public void setMachineId(java.util.UUID machineId) { this.machineId = machineId; }
    public java.util.UUID getDeviceId() { return deviceId; }
    public void setDeviceId(java.util.UUID deviceId) { this.deviceId = deviceId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Boolean getIsAcknowledged() { return isAcknowledged; }
    public void setIsAcknowledged(Boolean isAcknowledged) { this.isAcknowledged = isAcknowledged; }
    public java.util.UUID getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(java.util.UUID acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
}
