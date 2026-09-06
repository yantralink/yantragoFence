package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "device_states", indexes = {
        @jakarta.persistence.Index(name = "idx_device_states_organization_id", columnList = "organization_id")
})
public class DeviceState {

    @Id
    @Column(name = "device_id", updatable = false, nullable = false)
    private UUID deviceId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "online", nullable = false)
    private Boolean online = false;

    @Column(name = "relay_state", nullable = false, length = 10)
    private String relayState = "UNKNOWN";

    @Column(name = "voltage")
    private Double voltage;

    @Column(name = "battery")
    private Double battery;

    @Column(name = "gsm_signal")
    private Integer gsmSignal;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }
    public String getRelayState() { return relayState; }
    public void setRelayState(String relayState) { this.relayState = relayState; }
    public Double getVoltage() { return voltage; }
    public void setVoltage(Double voltage) { this.voltage = voltage; }
    public Double getBattery() { return battery; }
    public void setBattery(Double battery) { this.battery = battery; }
    public Integer getGsmSignal() { return gsmSignal; }
    public void setGsmSignal(Integer gsmSignal) { this.gsmSignal = gsmSignal; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
