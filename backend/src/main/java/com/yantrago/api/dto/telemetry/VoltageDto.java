package com.yantrago.api.dto.telemetry;

import java.time.LocalDateTime;

public class VoltageDto {

    private LocalDateTime recordedAt;
    private Double voltage;

    public VoltageDto() {}

    public VoltageDto(LocalDateTime recordedAt, Double voltage) {
        this.recordedAt = recordedAt;
        this.voltage = voltage;
    }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public Double getVoltage() { return voltage; }
    public void setVoltage(Double voltage) { this.voltage = voltage; }
}
