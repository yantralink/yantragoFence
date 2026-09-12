package com.yantrago.api.dto.machine;

import java.time.LocalDateTime;

/**
 * Latest telemetry snapshot for a machine's bound device.
 *
 * Returned by GET /api/v1/machines/{id}/telemetry/latest.
 * Fields are null when no heartbeat/alarm packet has been received yet.
 *
 * Per BR05 protocol:
 * - batteryPct: approximate 0–100 from the 7-level voltage enum (heartbeat/alarm only)
 * - charging: Terminal Info Bit2 (true = external power connected)
 * - gsmSignal: 0–4 from the GSM signal level byte (heartbeat/alarm only)
 * - voltage: external power voltage in volts from 0x94 info packet (type 0x00)
 */
public class TelemetryLatestDto {

    private Double batteryPct;
    private Boolean charging;
    private Integer gsmSignal;
    private Double voltage;
    private LocalDateTime lastTelemetryAt;

    public TelemetryLatestDto() {}

    public TelemetryLatestDto(Double batteryPct, Boolean charging, Integer gsmSignal,
                              Double voltage, LocalDateTime lastTelemetryAt) {
        this.batteryPct = batteryPct;
        this.charging = charging;
        this.gsmSignal = gsmSignal;
        this.voltage = voltage;
        this.lastTelemetryAt = lastTelemetryAt;
    }

    public Double getBatteryPct() { return batteryPct; }
    public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
    public Boolean getCharging() { return charging; }
    public void setCharging(Boolean charging) { this.charging = charging; }
    public Integer getGsmSignal() { return gsmSignal; }
    public void setGsmSignal(Integer gsmSignal) { this.gsmSignal = gsmSignal; }
    public Double getVoltage() { return voltage; }
    public void setVoltage(Double voltage) { this.voltage = voltage; }
    public LocalDateTime getLastTelemetryAt() { return lastTelemetryAt; }
    public void setLastTelemetryAt(LocalDateTime lastTelemetryAt) { this.lastTelemetryAt = lastTelemetryAt; }
}
