package com.yantrago.gateway.model;

/**
 * GPS ingest request — carries parsed GPS data from the protocol handler
 * to the ingest service for forwarding to the backend via RabbitMQ.
 *
 * This is the gateway-side DTO matching the fields used by ConcoxV5ProtocolHandler.
 * Phase 12 will wire this to TelemetryForwardService / TelemetryProducer.
 */
public class GpsIngestRequest {

    private String deviceId;
    private Double lat;
    private Double lng;
    private Double speedKph;
    private Double heading;
    private Long sentAt;
    private Boolean isOnline;
    private Boolean ignitionOn;
    private Boolean externalPowerConnected;
    private Boolean sosPressed;
    private Boolean vibrationDetected;
    private Boolean relayOn;
    private Integer gpsSatelliteCount;
    private Integer gsmSignalStrength;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
    public Double getSpeedKph() { return speedKph; }
    public void setSpeedKph(Double speedKph) { this.speedKph = speedKph; }
    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }
    public Long getSentAt() { return sentAt; }
    public void setSentAt(Long sentAt) { this.sentAt = sentAt; }
    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }
    public Boolean getIgnitionOn() { return ignitionOn; }
    public void setIgnitionOn(Boolean ignitionOn) { this.ignitionOn = ignitionOn; }
    public Boolean getExternalPowerConnected() { return externalPowerConnected; }
    public void setExternalPowerConnected(Boolean externalPowerConnected) { this.externalPowerConnected = externalPowerConnected; }
    public Boolean getSosPressed() { return sosPressed; }
    public void setSosPressed(Boolean sosPressed) { this.sosPressed = sosPressed; }
    public Boolean getVibrationDetected() { return vibrationDetected; }
    public void setVibrationDetected(Boolean vibrationDetected) { this.vibrationDetected = vibrationDetected; }
    public Boolean getRelayOn() { return relayOn; }
    public void setRelayOn(Boolean relayOn) { this.relayOn = relayOn; }
    public Integer getGpsSatelliteCount() { return gpsSatelliteCount; }
    public void setGpsSatelliteCount(Integer gpsSatelliteCount) { this.gpsSatelliteCount = gpsSatelliteCount; }
    public Integer getGsmSignalStrength() { return gsmSignalStrength; }
    public void setGsmSignalStrength(Integer gsmSignalStrength) { this.gsmSignalStrength = gsmSignalStrength; }
}
