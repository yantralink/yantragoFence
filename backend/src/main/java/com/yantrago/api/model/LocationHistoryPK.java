package com.yantrago.api.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for location_history (id + recorded_at).
 * Required because the table is partitioned by recorded_at.
 */
public class LocationHistoryPK implements Serializable {
    private UUID id;
    private LocalDateTime recordedAt;

    public LocationHistoryPK() {}

    public LocationHistoryPK(UUID id, LocalDateTime recordedAt) {
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
        if (!(o instanceof LocationHistoryPK that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(recordedAt, that.recordedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, recordedAt);
    }
}
