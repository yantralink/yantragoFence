package com.yantrago.shared.queue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Versioned alert transition contract published from the outbox after a
 * committed alert state change. Consumed by the notification pipeline on a
 * dedicated queue (NOTIFICATION_QUEUE).
 *
 * Per AGENTS.md rule 17: shared module defines RabbitMQ message contracts.
 */
public class AlertTransitionMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Contract schema version for backward-compatible evolution. */
    private int schemaVersion = 1;

    /** Stable idempotency key. Same across redeliveries. */
    private UUID eventId;

    /** Correlation ID linking source event to this transition. */
    private UUID correlationId;

    /** When the transition occurred (server time). */
    private Instant occurredAt;

    /** The persisted alert record ID. */
    private UUID alertId;

    /** Organization that owns the alert. */
    private UUID organizationId;

    /** Machine that triggered the alert. */
    private UUID machineId;

    /** Device associated with the alert, if any. */
    private UUID deviceId;

    /** Alert type (LOW_BATTERY, DEVICE_OFFLINE, etc.). */
    private String alertType;

    /** Severity: INFO | WARNING | CRITICAL. */
    private String severity;

    /** Incident state transition: OPEN | RESOLVED | ESCALATED. */
    private String incidentState;

    /** Observed value that triggered the alert, if applicable. */
    private Double observedValue;

    /** Unit of the observed value, if applicable. */
    private String observedUnit;

    /** Human-readable message. */
    private String message;

    /** Number of times this condition has been observed while open. */
    private int occurrenceCount;

    public AlertTransitionMessage() {
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getMachineId() {
        return machineId;
    }

    public void setMachineId(UUID machineId) {
        this.machineId = machineId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
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

    public String getIncidentState() {
        return incidentState;
    }

    public void setIncidentState(String incidentState) {
        this.incidentState = incidentState;
    }

    public Double getObservedValue() {
        return observedValue;
    }

    public void setObservedValue(Double observedValue) {
        this.observedValue = observedValue;
    }

    public String getObservedUnit() {
        return observedUnit;
    }

    public void setObservedUnit(String observedUnit) {
        this.observedUnit = observedUnit;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }
}
