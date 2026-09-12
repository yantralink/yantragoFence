package com.yantrago.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.model.Alert;
import com.yantrago.api.model.AlertRule;
import com.yantrago.api.model.AlertRuleState;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.repository.AlertRuleRepository;
import com.yantrago.api.repository.AlertRuleStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates alert rules against telemetry data and manages incident lifecycle
 * with hysteresis (sustain/recovery windows) and escalation.
 *
 * Phase 2 replaces the one-row suppression query with durable incident lifecycle:
 * - Sustain window: condition must persist for sustain_minutes before opening
 * - Recovery window: condition must clear for recovery_minutes before resolving
 * - Escalation: after escalation_minutes, escalate severity
 * - Restart-safe: state persisted in alert_rule_states
 *
 * Per AGENTS.md rule 12: production feature with validation, logging, tests.
 * Per notification plan Phase 2: brief noise does not notify.
 */
@Service
public class AlertGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AlertGenerationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertRuleStateRepository alertRuleStateRepository;
    private final CanonicalAlertService canonicalAlertService;
    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;

    public AlertGenerationService(JdbcTemplate jdbcTemplate,
                                   AlertRepository alertRepository,
                                   AlertRuleRepository alertRuleRepository,
                                   AlertRuleStateRepository alertRuleStateRepository,
                                   CanonicalAlertService canonicalAlertService,
                                   ObjectMapper objectMapper,
                                   TimeProvider timeProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertRuleStateRepository = alertRuleStateRepository;
        this.canonicalAlertService = canonicalAlertService;
        this.objectMapper = objectMapper;
        this.timeProvider = timeProvider;
    }

    /**
     * Evaluates all active alert rules for a given organization and machine.
     * Called after telemetry ingestion or via scheduled task.
     */
    @Transactional
    public void evaluateAlertsForMachine(UUID organizationId, UUID machineId) {
        List<AlertRule> rules = alertRuleRepository.findActiveRulesForMachine(organizationId, machineId);
        if (rules.isEmpty()) {
            return;
        }

        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule, organizationId, machineId);
            } catch (Exception e) {
                log.error("Failed to evaluate alert rule id={}: {}", rule.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Evaluates a single rule with hysteresis and lifecycle management.
     *
     * Lifecycle:
     * 1. Condition met + no existing violation state -> record violation_started_at
     * 2. Condition persists for sustain_minutes -> open incident via CanonicalAlertService
     * 3. Condition continues -> update occurrence count, check escalation
     * 4. Condition clears -> wait recovery_minutes -> resolve incident
     * 5. Brief noise (condition met then clears before sustain) -> no incident
     */
    private void evaluateRule(AlertRule rule, UUID organizationId, UUID machineId) throws Exception {
        JsonNode config = objectMapper.readTree(rule.getConditionConfig());
        String metric = config.path("metric").asText("voltage");
        String operator = config.path("operator").asText("<");
        double threshold = config.path("threshold").asDouble();
        int windowMinutes = config.path("windowMinutes").asInt(5);

        LocalDateTime now = timeProvider.now();
        LocalDateTime from = now.minusMinutes(windowMinutes);

        Double latestValue = queryLatestTelemetryValue(organizationId, machineId, metric, from, now);
        if (latestValue == null) {
            return; // no telemetry data in window
        }

        boolean conditionMet = evaluateCondition(latestValue, operator, threshold);

        // Get or create restart-safe state
        AlertRuleState state = getOrCreateState(rule, organizationId, machineId);
        state.setLastValue(latestValue);
        state.setLastEvaluatedAt(now);

        if (conditionMet) {
            handleConditionMet(rule, state, organizationId, machineId, latestValue, threshold, metric, operator, now);
        } else {
            handleConditionCleared(rule, state, organizationId, machineId, now);
        }

        alertRuleStateRepository.save(state);
    }

    /**
     * Handles the case where the condition is currently met.
     * - First observation: record violation_started_at
     * - After sustain window: open incident via CanonicalAlertService
     * - Already open: update occurrence count, check escalation
     */
    private void handleConditionMet(AlertRule rule, AlertRuleState state,
                                     UUID orgId, UUID machineId,
                                     double actualValue, double threshold,
                                     String metric, String operator,
                                     LocalDateTime now) {
        if (state.getViolationStartedAt() == null) {
            // First observation of violation
            state.setViolationStartedAt(now);
            state.setIsViolating(true);
            log.debug("Rule {} violation started at {} for machine {} (value={} {} {})",
                    rule.getId(), now, machineId, metric, operator, threshold);
            return;
        }

        // Check if sustain window has elapsed
        int sustainMinutes = rule.getSustainMinutes() != null ? rule.getSustainMinutes() : 0;
        LocalDateTime sustainEnd = state.getViolationStartedAt().plusMinutes(sustainMinutes);

        if (now.isBefore(sustainEnd)) {
            // Still within sustain window — don't open incident yet
            log.debug("Rule {} sustaining for machine {} ({} of {} minutes)",
                    rule.getId(), machineId,
                    java.time.Duration.between(state.getViolationStartedAt(), now).toMinutes(),
                    sustainMinutes);
            return;
        }

        // Sustain window elapsed — check if incident is already open
        Optional<Alert> openIncident = alertRepository.findOpenIncident(orgId, machineId, rule.getAlertType());

        if (openIncident.isEmpty()) {
            // Open new incident
            String message = String.format("%s: %s %.1f %s %.1f (rule: %s)",
                    rule.getAlertType(), metric, actualValue, operator, threshold, rule.getName());
            String severity = rule.getSeverity();

            // Check escalation
            if (rule.getEscalationMinutes() != null && rule.getEscalationSeverity() != null) {
                LocalDateTime escalationEnd = state.getViolationStartedAt().plusMinutes(rule.getEscalationMinutes());
                if (now.isAfter(escalationEnd)) {
                    severity = rule.getEscalationSeverity();
                    log.info("Rule {} escalated to {} after {} minutes for machine {}",
                            rule.getId(), severity, rule.getEscalationMinutes(), machineId);
                }
            }

            canonicalAlertService.processAlertEvent(
                    machineId,
                    rule.getAlertType(),
                    severity,
                    message,
                    now.toInstant(ZoneOffset.UTC),
                    actualValue,
                    metric,
                    null // rule-evaluation source — no external source event ID
            );
            log.info("Opened incident for rule {} type={} machine={} after {}min sustain",
                    rule.getId(), rule.getAlertType(), machineId, sustainMinutes);
        } else {
            // Incident already open — update via CanonicalAlertService (increments occurrence)
            Alert existing = openIncident.get();

            // Check escalation on existing incident
            if (rule.getEscalationMinutes() != null && rule.getEscalationSeverity() != null) {
                LocalDateTime escalationEnd = state.getViolationStartedAt().plusMinutes(rule.getEscalationMinutes());
                if (now.isAfter(escalationEnd) && !rule.getEscalationSeverity().equals(existing.getSeverity())) {
                    String message = String.format("%s: %s %.1f %s %.1f (rule: %s, ESCALATED)",
                            rule.getAlertType(), metric, actualValue, operator, threshold, rule.getName());
                    canonicalAlertService.escalateIncident(
                            machineId,
                            rule.getAlertType(),
                            rule.getEscalationSeverity(),
                            message,
                            now.toInstant(ZoneOffset.UTC),
                            actualValue,
                            metric
                    );
                    log.info("Escalated existing incident for rule {} to {}", rule.getId(), rule.getEscalationSeverity());
                }
            }
        }
    }

    /**
     * Handles the case where the condition has cleared.
     * - Not violating: nothing to do
     * - Violating but no open incident: clear violation state (brief noise, no incident)
     * - Violating with open incident: wait recovery_minutes, then resolve
     */
    private void handleConditionCleared(AlertRule rule, AlertRuleState state,
                                          UUID orgId, UUID machineId,
                                          LocalDateTime now) {
        if (!state.getIsViolating()) {
            return; // not violating, nothing to clear
        }

        Optional<Alert> openIncident = alertRepository.findOpenIncident(orgId, machineId, rule.getAlertType());

        if (openIncident.isEmpty()) {
            // Brief noise: condition was met but never sustained long enough to open an incident.
            // Clear violation state without creating any alert.
            state.setViolationStartedAt(null);
            state.setIsViolating(false);
            log.debug("Rule {} violation cleared without incident for machine {} (brief noise)",
                    rule.getId(), machineId);
            return;
        }

        // Incident is open — check recovery window
        int recoveryMinutes = rule.getRecoveryMinutes() != null ? rule.getRecoveryMinutes() : 5;
        Alert incident = openIncident.get();
        LocalDateTime lastObserved = incident.getLastObservedAt() != null
                ? incident.getLastObservedAt()
                : incident.getTriggeredAt();
        LocalDateTime recoveryEnd = lastObserved.plusMinutes(recoveryMinutes);

        if (now.isBefore(recoveryEnd)) {
            // Still within recovery window — don't resolve yet
            log.debug("Rule {} recovering for machine {} ({} of {} minutes)",
                    rule.getId(), machineId,
                    java.time.Duration.between(lastObserved, now).toMinutes(),
                    recoveryMinutes);
            return;
        }

        // Recovery window elapsed — resolve incident
        String resolutionMessage = String.format("%s: condition cleared (rule: %s)",
                rule.getAlertType(), rule.getName());
        canonicalAlertService.resolveIncident(orgId, machineId, rule.getAlertType(), resolutionMessage);

        // Clear violation state
        state.setViolationStartedAt(null);
        state.setIsViolating(false);
        log.info("Resolved incident for rule {} type={} machine={} after {}min recovery",
                rule.getId(), rule.getAlertType(), machineId, recoveryMinutes);
    }

    private boolean evaluateCondition(double value, String operator, double threshold) {
        // Support both named operators (GT, LT, GTE, LTE, EQ) and legacy
        // symbolic operators (<, >, <=, >=, ==) for backwards compatibility
        return switch (operator.toUpperCase()) {
            case "<", "LT" -> value < threshold;
            case ">", "GT" -> value > threshold;
            case "<=", "LTE" -> value <= threshold;
            case ">=", "GTE" -> value >= threshold;
            case "==", "EQ" -> value == threshold;
            default -> false;
        };
    }

    private AlertRuleState getOrCreateState(AlertRule rule, UUID orgId, UUID machineId) {
        return alertRuleStateRepository.findByRuleIdAndMachineId(rule.getId(), machineId)
                .orElseGet(() -> {
                    AlertRuleState newState = new AlertRuleState();
                    newState.setId(UUID.randomUUID());
                    newState.setOrganizationId(orgId);
                    newState.setRuleId(rule.getId());
                    newState.setMachineId(machineId);
                    newState.setIsViolating(false);
                    newState.setCreatedAt(timeProvider.now());
                    return newState;
                });
    }

    private Double queryLatestTelemetryValue(UUID orgId, UUID machineId, String metric,
                                              LocalDateTime from, LocalDateTime to) {
        String table = switch (metric) {
            case "voltage" -> "voltage_readings";
            case "battery" -> "battery_readings";
            case "gsm_signal" -> "gsm_readings";
            default -> null;
        };
        if (table == null) {
            return null;
        }

        String valueColumn = switch (metric) {
            case "voltage" -> "voltage";
            case "battery" -> "battery_pct";
            case "gsm_signal" -> "gsm_signal";
            default -> null;
        };
        if (valueColumn == null) {
            return null;
        }

        try {
            String sql = "SELECT " + valueColumn + " FROM " + table +
                    " WHERE organization_id = ? AND machine_id = ? " +
                    "AND recorded_at BETWEEN ? AND ? " +
                    "ORDER BY recorded_at DESC LIMIT 1";
            return jdbcTemplate.queryForObject(sql, Double.class, orgId, machineId, from, to);
        } catch (Exception e) {
            log.debug("No telemetry data found for metric={} machineId={}: {}", metric, machineId, e.getMessage());
            return null;
        }
    }
}
