package com.yantrago.shared.queue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * GPS location message published by the device gateway and consumed by
 * the backend for persistence into device_locations (upsert) and
 * location_history (partitioned time-series).
 */
public class LocationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID deviceId;
    private String imei;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double course;
    private Instant timestamp;

    public LocationMessage() {
    }

    public LocationMessage(UUID deviceId, String imei, Double latitude, Double longitude, Double speed, Double course, Instant timestamp) {
        this.deviceId = deviceId;
        this.imei = imei;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.course = course;
        this.timestamp = timestamp;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
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
