package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-channel delivery attempt for a notification inbox item.
 * An inbox item may have multiple delivery attempts (retries) across channels.
 *
 * Per notification plan Phase 3: inbox/delivery/attempt schema.
 */
@Entity
@Table(name = "notification_delivery", indexes = {
        @jakarta.persistence.Index(name = "idx_delivery_inbox", columnList = "inbox_id"),
        @jakarta.persistence.Index(name = "idx_delivery_user_status", columnList = "user_id, status")
})
public class NotificationDelivery extends BaseEntity {

    @Column(name = "inbox_id", nullable = false)
    private UUID inboxId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel; // PUSH | EMAIL | SMS | WHATSAPP

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // PENDING | SENT | DELIVERED | FAILED | SKIPPED

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // Getters and setters
    public UUID getInboxId() { return inboxId; }
    public void setInboxId(UUID inboxId) { this.inboxId = inboxId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
}
