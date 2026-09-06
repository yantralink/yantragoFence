package com.yantrago.api.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for time-series telemetry tables (id + recorded_at).
 * Shared by voltage_readings, battery_readings, gsm_readings.
 */
public class TelemetryReadingPK implements Serializable {
    private UUID id;
    private LocalDateTime recordedAt;

    public TelemetryReadingPK() {}

    public TelemetryReadingPK(UUID id, LocalDateTime recordedAt) {
        this.id = id;
        this.recordedAt = recordedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TelemetryReadingPK that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(recordedAt, that.recordedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, recordedAt);
    }
}
