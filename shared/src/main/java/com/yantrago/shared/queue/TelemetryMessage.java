package com.yantrago.shared.queue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Telemetry reading published by the device gateway and consumed by the
 * backend for persistence into the time-series telemetry tables.
 */
public class TelemetryMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID deviceId;
    private String imei;
    private Double voltage;
    private Double battery;
    private Integer gsmSignal;
    private Boolean charging;  // true = external power connected, false = on battery
    private Instant timestamp;

    public TelemetryMessage() {
    }

    public TelemetryMessage(UUID deviceId, String imei, Double voltage, Double battery, Integer gsmSignal, Instant timestamp) {
        this.deviceId = deviceId;
        this.imei = imei;
        this.voltage = voltage;
        this.battery = battery;
        this.gsmSignal = gsmSignal;
        this.timestamp = timestamp;
    }

    public TelemetryMessage(UUID deviceId, String imei, Double voltage, Double battery,
                            Integer gsmSignal, Boolean charging, Instant timestamp) {
        this.deviceId = deviceId;
        this.imei = imei;
        this.voltage = voltage;
        this.battery = battery;
        this.gsmSignal = gsmSignal;
        this.charging = charging;
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

    public Integer getGsmSignal() {
        return gsmSignal;
    }

    public void setGsmSignal(Integer gsmSignal) {
        this.gsmSignal = gsmSignal;
    }

    public Boolean getCharging() {
        return charging;
    }

    public void setCharging(Boolean charging) {
        this.charging = charging;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
