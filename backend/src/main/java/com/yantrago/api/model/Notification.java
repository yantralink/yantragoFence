package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @jakarta.persistence.Index(name = "idx_notifications_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @jakarta.persistence.Index(name = "idx_notifications_status", columnList = "status"),
        @jakarta.persistence.Index(name = "idx_notifications_created_at", columnList = "created_at")
})
public class Notification extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "user_id")
    private java.util.UUID userId;

    @Column(name = "alert_id")
    private java.util.UUID alertId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getUserId() { return userId; }
    public void setUserId(java.util.UUID userId) { this.userId = userId; }
    public java.util.UUID getAlertId() { return alertId; }
    public void setAlertId(java.util.UUID alertId) { this.alertId = alertId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
}
