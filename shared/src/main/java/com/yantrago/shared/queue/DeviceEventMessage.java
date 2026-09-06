package com.yantrago.shared.queue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Device lifecycle event published by the gateway (LOGIN, HEARTBEAT,
 * DISCONNECT) and consumed by the backend to update device_states and
 * drive online/offline alerts.
 */
public class DeviceEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String EVENT_LOGIN = "LOGIN";
    public static final String EVENT_HEARTBEAT = "HEARTBEAT";
    public static final String EVENT_DISCONNECT = "DISCONNECT";

    private UUID deviceId;
    private String imei;
    private String eventType;
    private Instant timestamp;

    public DeviceEventMessage() {
    }

    public DeviceEventMessage(UUID deviceId, String imei, String eventType, Instant timestamp) {
        this.deviceId = deviceId;
        this.imei = imei;
        this.eventType = eventType;
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
