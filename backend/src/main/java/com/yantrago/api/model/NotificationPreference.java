package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_preferences", indexes = {
        @jakarta.persistence.Index(name = "idx_notification_preferences_user_id", columnList = "user_id")
})
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private java.util.UUID userId;

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "alert_type", length = 50)
    private String alertType;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    public java.util.UUID getUserId() { return userId; }
    public void setUserId(java.util.UUID userId) { this.userId = userId; }
    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }
    public Boolean getPushEnabled() { return pushEnabled; }
    public void setPushEnabled(Boolean pushEnabled) { this.pushEnabled = pushEnabled; }
}
