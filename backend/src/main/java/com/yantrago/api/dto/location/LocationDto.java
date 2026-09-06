package com.yantrago.api.dto.location;

import java.time.LocalDateTime;
import java.util.UUID;

public class LocationDto {

    private UUID deviceId;
    private UUID machineId;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double course;
    private LocalDateTime recordedAt;
    private LocalDateTime updatedAt;

    public LocationDto() {}

    public LocationDto(UUID deviceId, UUID machineId, Double latitude, Double longitude,
                       Double speed, Double course, LocalDateTime recordedAt, LocalDateTime updatedAt) {
        this.deviceId = deviceId;
        this.machineId = machineId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.course = course;
        this.recordedAt = recordedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
    public Double getCourse() { return course; }
    public void setCourse(Double course) { this.course = course; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
