package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-recipient notification inbox item. One row per (event_id, user_id).
 * Deduplicated via unique constraint on (event_id, user_id).
 */
@Entity
@Table(name = "notification_inbox", indexes = {
        @jakarta.persistence.Index(name = "idx_inbox_user_org", columnList = "organization_id, user_id"),
        @jakarta.persistence.Index(name = "idx_inbox_alert", columnList = "alert_id")
})
public class NotificationInbox extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "incident_state", nullable = false, length = 20)
    private String incidentState;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "machine_id")
    private UUID machineId;

    @Column(name = "observed_value")
    private Double observedValue;

    @Column(name = "observed_unit", length = 20)
    private String observedUnit;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "en";

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion = 1;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // Event-time recipient snapshot: captures customer identity at event time.
    @Column(name = "recipient_customer_id")
    private UUID recipientCustomerId;

    @Column(name = "recipient_customer_name")
    private String recipientCustomerName;

    // Authorized acknowledgement: explicit user action separate from is_read.
    @Column(name = "is_acknowledged", nullable = false)
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    // Getters and setters
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getIncidentState() { return incidentState; }
    public void setIncidentState(String incidentState) { this.incidentState = incidentState; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public Double getObservedValue() { return observedValue; }
    public void setObservedValue(Double observedValue) { this.observedValue = observedValue; }
    public String getObservedUnit() { return observedUnit; }
    public void setObservedUnit(String observedUnit) { this.observedUnit = observedUnit; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public UUID getRecipientCustomerId() { return recipientCustomerId; }
    public void setRecipientCustomerId(UUID recipientCustomerId) { this.recipientCustomerId = recipientCustomerId; }
    public String getRecipientCustomerName() { return recipientCustomerName; }
    public void setRecipientCustomerName(String recipientCustomerName) { this.recipientCustomerName = recipientCustomerName; }
    public Boolean getIsAcknowledged() { return isAcknowledged; }
    public void setIsAcknowledged(Boolean isAcknowledged) { this.isAcknowledged = isAcknowledged; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
