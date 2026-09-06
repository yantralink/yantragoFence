package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recharges", indexes = {
        @jakarta.persistence.Index(name = "idx_recharges_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_recharges_device_id", columnList = "device_id"),
        @jakarta.persistence.Index(name = "idx_recharges_recharged_at", columnList = "recharged_at")
})
public class Recharge extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "device_id", nullable = false)
    private java.util.UUID deviceId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "plan_name", length = 100)
    private String planName;

    @Column(name = "recharged_at", nullable = false)
    private LocalDateTime rechargedAt;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getDeviceId() { return deviceId; }
    public void setDeviceId(java.util.UUID deviceId) { this.deviceId = deviceId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public LocalDateTime getRechargedAt() { return rechargedAt; }
    public void setRechargedAt(LocalDateTime rechargedAt) { this.rechargedAt = rechargedAt; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
}
