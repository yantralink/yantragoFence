package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices", indexes = {
        @jakarta.persistence.Index(name = "idx_devices_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_devices_machine_id", columnList = "machine_id"),
        @jakarta.persistence.Index(name = "idx_devices_imei", columnList = "imei", unique = true)
})
public class Device extends BaseEntity {

    @Column(name = "organization_id")
    private java.util.UUID organizationId;

    @Column(name = "machine_id")
    private java.util.UUID machineId;

    @Column(name = "imei", nullable = false, unique = true, length = 20)
    private String imei;

    @Column(name = "sim_number", length = 30)
    private String simNumber;

    @Column(name = "protocol_type", nullable = false, length = 20)
    private String protocolType;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // Latest telemetry state from heartbeat/alarm packets (V33 migration).
    // Time-series history remains in battery_readings, voltage_readings, gsm_readings.
    @Column(name = "battery_pct")
    private Double batteryPct;

    @Column(name = "charging")
    private Boolean charging;

    @Column(name = "gsm_signal")
    private Integer gsmSignal;

    @Column(name = "last_telemetry_at")
    private LocalDateTime lastTelemetryAt;

    // Latest external power voltage from 0x94 info packet (V35 migration).
    @Column(name = "voltage")
    private Double voltage;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getMachineId() { return machineId; }
    public void setMachineId(java.util.UUID machineId) { this.machineId = machineId; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }
    public String getProtocolType() { return protocolType; }
    public void setProtocolType(String protocolType) { this.protocolType = protocolType; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Double getBatteryPct() { return batteryPct; }
    public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
    public Boolean getCharging() { return charging; }
    public void setCharging(Boolean charging) { this.charging = charging; }
    public Integer getGsmSignal() { return gsmSignal; }
    public void setGsmSignal(Integer gsmSignal) { this.gsmSignal = gsmSignal; }
    public LocalDateTime getLastTelemetryAt() { return lastTelemetryAt; }
    public void setLastTelemetryAt(LocalDateTime lastTelemetryAt) { this.lastTelemetryAt = lastTelemetryAt; }
    public Double getVoltage() { return voltage; }
    public void setVoltage(Double voltage) { this.voltage = voltage; }
}
