package com.yantrago.shared.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Last-known state for a device. Used by both backend (REST responses,
 * WebSocket broadcasts) and gateway (cached state lookups).
 */
public class DeviceStateDto {

    private UUID deviceId;
    private String imei;
    private Boolean online;
    private Instant lastSeen;
    private Double voltage;
    private Double battery;
    private String relayState; // ON | OFF | UNKNOWN

    public DeviceStateDto() {
    }

    public DeviceStateDto(UUID deviceId, String imei, Boolean online, Instant lastSeen,
                          Double voltage, Double battery, String relayState) {
        this.deviceId = deviceId;
        this.imei = imei;
        this.online = online;
        this.lastSeen = lastSeen;
        this.voltage = voltage;
        this.battery = battery;
        this.relayState = relayState;
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

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Double getVoltage() {
        return voltage;
    }

    public void setVoltage(Double voltage) {
        this.voltage = voltage;
    }

    public Double getBattery() {
        return battery;
    }

    public void setBattery(Double battery) {
        this.battery = battery;
    }

    public String getRelayState() {
        return relayState;
    }

    public void setRelayState(String relayState) {
        this.relayState = relayState;
    }
}
