package com.yantrago.gateway.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dashcam model — represents a JT808 dashcam device (T98).
 *
 * This is the gateway-side model used by JT808ProtocolHandler and DashcamConnectionRegistry.
 * Phase 12 will wire this to DeviceAuthService for real database-backed lookups.
 */
public class Dashcam {

    public enum DashcamStatus {
        ONLINE,
        OFFLINE,
        ALARM
    }

    private UUID id;
    private String simPhone;
    private String imei;
    private String manufacturerId;
    private String model;
    private DashcamStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime lastHeartbeat;
    private String authCode;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSimPhone() { return simPhone; }
    public void setSimPhone(String simPhone) { this.simPhone = simPhone; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getManufacturerId() { return manufacturerId; }
    public void setManufacturerId(String manufacturerId) { this.manufacturerId = manufacturerId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public DashcamStatus getStatus() { return status; }
    public void setStatus(DashcamStatus status) { this.status = status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
}
