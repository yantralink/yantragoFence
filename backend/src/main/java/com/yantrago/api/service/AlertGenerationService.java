package com.yantrago.api.service;

import com.yantrago.api.model.Alert;
import com.yantrago.api.model.AlertRule;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.repository.TelemetryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates alert rules against telemetry data and generates alerts.
 *
 * Alert rules are stored in alert_rules with a condition_config JSON field
 * that defines the threshold conditions. This service evaluates those rules
 * against the latest telemetry readings and creates Alert records when
 * conditions are met.
 *
 * Condition config format (JSON):
 * {
 *   "metric": "voltage" | "battery" | "gsm_signal",
 *   "operator": "<" | ">" | "<=" | ">=" | "==",
 *   "threshold": <number>,
 *   "windowMinutes": <number>  // optional, defaults to 5
 * }
 */
@Service
public class AlertGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AlertGenerationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public AlertGenerationService(JdbcTemplate jdbcTemplate,
                                   AlertRepository alertRepository,
                                   ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates all active alert rules for a given organization and machine.
     * Called periodically (e.g. via scheduled task) or after telemetry ingestion.
     */
    @Transactional
    public void evaluateAlertsForMachine(UUID organizationId, UUID machineId) {
        List<AlertRule> rules = findActiveRules(organizationId, machineId);
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

    private void evaluateRule(AlertRule rule, UUID organizationId, UUID machineId) throws Exception {
        JsonNode config = objectMapper.readTree(rule.getConditionConfig());
        String metric = config.path("metric").asText("voltage");
        String operator = config.path("operator").asText("<");
        double threshold = config.path("threshold").asDouble();
        int windowMinutes = config.path("windowMinutes").asInt(5);

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusMinutes(windowMinutes);

        // Query latest telemetry value for the metric
        Double latestValue = queryLatestTelemetryValue(organizationId, machineId, metric, from, to);
        if (latestValue == null) {
            return; // no telemetry data in window
        }

        boolean conditionMet = evaluateCondition(latestValue, operator, threshold);
        if (conditionMet) {
            // Check if there's already an unacknowledged alert of this type for this machine
            boolean existing = alertRepository
                    .findByOrganizationIdAndMachineId(organizationId, machineId, org.springframework.data.domain.PageRequest.of(0, 1))
                    .stream()
                    .anyMatch(a -> rule.getAlertType().equals(a.getAlertType()) && !a.getIsAcknowledged());

            if (!existing) {
                createAlert(rule, organizationId, machineId, latestValue, threshold, metric, operator);
            }
        }
    }

    private boolean evaluateCondition(double value, String operator, double threshold) {
        return switch (operator) {
            case "<" -> value < threshold;
            case ">" -> value > threshold;
            case "<=" -> value <= threshold;
            case ">=" -> value >= threshold;
            case "==" -> value == threshold;
            default -> false;
        };
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

    private void createAlert(AlertRule rule, UUID orgId, UUID machineId,
                             double actualValue, double threshold,
                             String metric, String operator) {
        Alert alert = new Alert();
        alert.setOrganizationId(orgId);
        alert.setAlertRuleId(rule.getId());
        alert.setMachineId(machineId);
        alert.setAlertType(rule.getAlertType());
        alert.setSeverity(rule.getSeverity());
        alert.setMessage(String.format("%s: %s %.1f %s %.1f (rule: %s)",
                rule.getAlertType(), metric, actualValue, operator, threshold, rule.getName()));
        alert.setIsAcknowledged(false);
        alert.setTriggeredAt(LocalDateTime.now());

        alert = alertRepository.save(alert);
        log.warn("Generated alert id={} type={} severity={} machineId={} value={} threshold={}",
                alert.getId(), alert.getAlertType(), alert.getSeverity(), machineId, actualValue, threshold);
    }

    @SuppressWarnings("unchecked")
    private List<AlertRule> findActiveRules(UUID orgId, UUID machineId) {
        // Query alert_rules via JdbcTemplate since there's no dedicated repository
        String sql = "SELECT * FROM alert_rules WHERE organization_id = ? AND is_active = true " +
                "AND (machine_id = ? OR machine_id IS NULL)";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AlertRule rule = new AlertRule();
            rule.setId(UUID.fromString(rs.getString("id")));
            rule.setOrganizationId(UUID.fromString(rs.getString("organization_id")));
            String mid = rs.getString("machine_id");
            rule.setMachineId(mid != null ? UUID.fromString(mid) : null);
            rule.setName(rs.getString("name"));
            rule.setAlertType(rs.getString("alert_type"));
            rule.setConditionConfig(rs.getString("condition_config"));
            rule.setSeverity(rs.getString("severity"));
            rule.setIsActive(rs.getBoolean("is_active"));
            return rule;
        }, orgId, machineId);
    }
}
