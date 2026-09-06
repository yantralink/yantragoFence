package com.yantrago.shared.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * GPS ingest request used by REST ingestion endpoints and reused by the
 * simulator. Adapted from HarvestTracker's GpsIngestRequest but trimmed
 * to the YantraGO machine-tracking domain (machineId instead of deviceId,
 * Instant instead of epoch millis).
 *
 * Validation annotations are intentionally omitted here to keep the
 * shared module dependency-free beyond amqp + jackson. The backend
 * re-validates at the controller boundary with its own request DTO.
 */
public class GpsIngestRequest {

    private UUID machineId;
    private Double latitude;   // [-90.0, 90.0]
    private Double longitude;  // [-180.0, 180.0]
    private Double speed;      // >= 0
    private Double course;     // [0.0, 359.99]
    private Instant timestamp;

    public GpsIngestRequest() {
    }

    public GpsIngestRequest(UUID machineId, Double latitude, Double longitude, Double speed, Double course, Instant timestamp) {
        this.machineId = machineId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.course = course;
        this.timestamp = timestamp;
    }

    public UUID getMachineId() {
        return machineId;
    }

    public void setMachineId(UUID machineId) {
        this.machineId = machineId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getCourse() {
        return course;
    }

    public void setCourse(Double course) {
        this.course = course;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
