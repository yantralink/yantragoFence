package com.yantrago.api.dto.telemetry;

import java.time.LocalDateTime;

public class BatteryDto {

    private LocalDateTime recordedAt;
    private Double batteryPct;

    public BatteryDto() {}

    public BatteryDto(LocalDateTime recordedAt, Double batteryPct) {
        this.recordedAt = recordedAt;
        this.batteryPct = batteryPct;
    }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public Double getBatteryPct() { return batteryPct; }
    public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
}
