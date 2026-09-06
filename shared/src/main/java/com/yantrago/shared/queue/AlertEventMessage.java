package com.yantrago.shared.queue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Alert event contract. Published by the backend (rule evaluation) or by
 * the gateway (e.g. SOS, low battery) and consumed by the alert and
 * notification pipelines.
 */
public class AlertEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID alertId;
    private UUID machineId;
    private String alertType;
    private String severity; // INFO | WARNING | CRITICAL
    private String message;
    private Instant timestamp;

    public AlertEventMessage() {
    }

    public AlertEventMessage(UUID alertId, UUID machineId, String alertType, String severity, String message, Instant timestamp) {
        this.alertId = alertId;
        this.machineId = machineId;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.timestamp = timestamp;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public UUID getMachineId() {
        return machineId;
    }

    public void setMachineId(UUID machineId) {
        this.machineId = machineId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
