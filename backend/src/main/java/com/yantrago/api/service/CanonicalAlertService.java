package com.yantrago.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.model.Alert;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.shared.queue.AlertTransitionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Canonical alert application service — the single entry point for all alert
 * persistence. Deduplicates using incident_key (org:machine:alertType), updates
 * existing open incidents, and writes an outbox event in the same DB transaction.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 7: tenant isolation from machine lookup, never from input.
 */
@Service
public class CanonicalAlertService {

    private static final Logger log = LoggerFactory.getLogger(CanonicalAlertService.class);

    private final AlertRepository alertRepository;
    private final MachineRepository machineRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CanonicalAlertService(AlertRepository alertRepository,
                                  MachineRepository machineRepository,
                                  JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.machineRepository = machineRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes an incoming alert event from the gateway or rule evaluator.
     * Creates a new open incident or updates the existing open incident for the
     * same (org, machine, alertType) key. Writes an outbox event atomically.
     *
     * @param machineId    the machine that triggered the alert
     * @param alertType    alert type (LOW_BATTERY, DEVICE_OFFLINE, etc.)
     * @param severity     INFO | WARNING | CRITICAL
     * @param message      human-readable message
     * @param triggeredAt  when the condition was observed (may be null for now)
     * @param observedValue observed value, if applicable
     * @param observedUnit  unit of the observed value, if applicable
     * @param sourceEventId the source event ID (from gateway alertId) for correlation;
     *                      may be null for rule-evaluation sources
     * @return the persisted or updated Alert, or null if the machine was not found
     */
    @Transactional
    public Alert processAlertEvent(UUID machineId,
                                    String alertType,
                                    String severity,
                                    String message,
                                    Instant triggeredAt,
                                    Double observedValue,
                                    String observedUnit,
                                    UUID sourceEventId) {
        // Resolve organization from the machine (never from input)
        UUID orgId = machineRepository.findById(machineId)
                .map(m -> m.getOrganizationId())
                .orElse(null);

        if (orgId == null) {
            log.warn("Cannot process alert: machine not found machineId={}", machineId);
            return null;
        }

        String incidentKey = buildIncidentKey(orgId, machineId, alertType);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime observedAt = triggeredAt != null
                ? LocalDateTime.ofInstant(triggeredAt, ZoneOffset.UTC)
                : now;

        // Check for existing open incident
        Alert existing = alertRepository.findOpenIncident(orgId, machineId, alertType).orElse(null);

        Alert alert;
        String incidentState;

        if (existing != null) {
            // Update existing open incident — increment occurrence count, update last observed
            existing.setLastObservedAt(observedAt);
            existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
            existing.setSeverity(severity); // allow escalation
            existing.setMessage(message);
            if (observedValue != null) {
                existing.setObservedValue(observedValue);
            }
            if (observedUnit != null) {
                existing.setObservedUnit(observedUnit);
            }
            alert = alertRepository.save(existing);
            incidentState = "OPEN";
            log.info("Updated open incident alertId={} type={} occurrences={}",
                    alert.getId(), alertType, alert.getOccurrenceCount());
        } else {
            // Create new open incident
            alert = new Alert();
            alert.setOrganizationId(orgId);
            alert.setMachineId(machineId);
            alert.setAlertType(alertType);
            alert.setSeverity(severity);
            alert.setMessage(message);
            alert.setIsAcknowledged(false);
            alert.setTriggeredAt(observedAt);
            alert.setIncidentState("OPEN");
            alert.setIncidentKey(incidentKey);
            alert.setFirstObservedAt(observedAt);
            alert.setLastObservedAt(observedAt);
            alert.setOccurrenceCount(1);
            alert.setObservedValue(observedValue);
            alert.setObservedUnit(observedUnit);
            alert = alertRepository.save(alert);
            incidentState = "OPEN";
            log.info("Created new open incident alertId={} type={} severity={} machineId={}",
                    alert.getId(), alertType, severity, machineId);
        }

        // Write outbox event in the same transaction
        writeOutboxEvent(alert, incidentState, triggeredAt, sourceEventId);

        return alert;
    }

    /**
     * Escalates an existing open incident to a higher severity.
     * Sets incidentState to "ESCALATED" and writes an outbox event with
     * state "ESCALATED" so downstream consumers (notification system) can
     * distinguish escalation from a normal occurrence update.
     *
     * @param machineId           the machine whose incident is being escalated
     * @param alertType            alert type (LOW_BATTERY, VOLTAGE_DROP, etc.)
     * @param escalationSeverity   the new severity (INFO | WARNING | CRITICAL)
     * @param message              human-readable escalation message
     * @param triggeredAt          when the escalation was observed (may be null)
     * @param observedValue        latest observed value, if applicable
     * @param observedUnit         unit of the observed value, if applicable
     * @return the escalated Alert, or null if the machine or open incident was not found
     */
    @Transactional
    public Alert escalateIncident(UUID machineId,
                                   String alertType,
                                   String escalationSeverity,
                                   String message,
                                   Instant triggeredAt,
                                   Double observedValue,
                                   String observedUnit) {
        // Resolve organization from the machine (never from input)
        UUID orgId = machineRepository.findById(machineId)
                .map(m -> m.getOrganizationId())
                .orElse(null);

        if (orgId == null) {
            log.warn("Cannot escalate alert: machine not found machineId={}", machineId);
            return null;
        }

        Alert existing = alertRepository.findOpenIncident(orgId, machineId, alertType).orElse(null);
        if (existing == null) {
            log.debug("No open incident to escalate for org={} machine={} type={}", orgId, machineId, alertType);
            return null;
        }

        LocalDateTime observedAt = triggeredAt != null
                ? LocalDateTime.ofInstant(triggeredAt, ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC);

        existing.setSeverity(escalationSeverity);
        existing.setIncidentState("ESCALATED");
        existing.setMessage(message);
        existing.setLastObservedAt(observedAt);
        existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
        if (observedValue != null) {
            existing.setObservedValue(observedValue);
        }
        if (observedUnit != null) {
            existing.setObservedUnit(observedUnit);
        }
        Alert escalated = alertRepository.save(existing);

        // Write outbox event with ESCALATED state
        writeOutboxEvent(escalated, "ESCALATED", triggeredAt, null);

        log.info("Escalated incident alertId={} type={} to severity={} machineId={}",
                escalated.getId(), alertType, escalationSeverity, machineId);
        return escalated;
    }

    /**
     * Resolves an existing open incident for the given (org, machine, alertType).
     * Used by rule evaluation and recovery detection.
     */
    @Transactional
    public Alert resolveIncident(UUID orgId, UUID machineId, String alertType, String resolutionMessage) {
        Alert existing = alertRepository.findOpenIncident(orgId, machineId, alertType).orElse(null);
        if (existing == null) {
            log.debug("No open incident to resolve for org={} machine={} type={}", orgId, machineId, alertType);
            return null;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        existing.setIncidentState("RESOLVED");
        existing.setResolvedAt(now);
        existing.setLastObservedAt(now);
        if (resolutionMessage != null) {
            existing.setMessage(resolutionMessage);
        }
        Alert resolved = alertRepository.save(existing);

        // Write outbox event for the resolution transition
        writeOutboxEvent(resolved, "RESOLVED", now.toInstant(ZoneOffset.UTC), null);

        log.info("Resolved incident alertId={} type={} machineId={}",
                resolved.getId(), alertType, machineId);
        return resolved;
    }

    private void writeOutboxEvent(Alert alert, String incidentState, Instant occurredAt,
                                   UUID sourceEventId) {
        UUID eventId = UUID.randomUUID();
        AlertTransitionMessage transition = new AlertTransitionMessage();
        transition.setSchemaVersion(1);
        transition.setEventId(eventId);
        // Phase 1 fix: use source event ID as correlation ID when available
        transition.setCorrelationId(sourceEventId != null ? sourceEventId : eventId);
        transition.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
        transition.setAlertId(alert.getId());
        transition.setOrganizationId(alert.getOrganizationId());
        transition.setMachineId(alert.getMachineId());
        transition.setAlertType(alert.getAlertType());
        transition.setSeverity(alert.getSeverity());
        transition.setIncidentState(incidentState);
        transition.setObservedValue(alert.getObservedValue());
        transition.setObservedUnit(alert.getObservedUnit());
        transition.setMessage(alert.getMessage());
        transition.setOccurrenceCount(alert.getOccurrenceCount());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(transition);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AlertTransitionMessage", e);
        }

        jdbcTemplate.update(
                "INSERT INTO event_outbox (organization_id, event_type, schema_version, " +
                        "aggregate_id, event_id, payload) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
                alert.getOrganizationId(),
                "ALERT_TRANSITION",
                1,
                alert.getId(),
                eventId,
                payload
        );

        log.debug("Wrote outbox event eventId={} alertId={} state={}", eventId, alert.getId(), incidentState);
    }

    private String buildIncidentKey(UUID orgId, UUID machineId, String alertType) {
        return orgId + ":" + machineId + ":" + alertType;
    }
}
