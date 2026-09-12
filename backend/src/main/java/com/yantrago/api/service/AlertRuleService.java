package com.yantrago.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.dto.alert.AlertRuleDto;
import com.yantrago.api.dto.alert.AlertRuleRequest;
import com.yantrago.api.model.AlertRule;
import com.yantrago.api.repository.AlertRuleRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Alert rule CRUD service with audit history.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 12: validation, logging, security checks.
 *
 * Per notification plan Phase 2: typed rule configuration with narrow RBAC/validation
 * and audit history. Leave unsupported metrics and unverified voltage/expiry
 * semantics disabled.
 */
@Service
public class AlertRuleService {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleService.class);

    // Supported alert types for rule creation. Offline and expiry are system-managed.
    private static final java.util.Set<String> SUPPORTED_RULE_TYPES = java.util.Set.of(
            "LOW_BATTERY", "VOLTAGE_DROP", "GSM_SIGNAL_LOW"
    );

    private static final java.util.Set<String> VALID_SEVERITIES = java.util.Set.of(
            "INFO", "WARNING", "CRITICAL"
    );

    private static final java.util.Set<String> SUPPORTED_METRICS = java.util.Set.of(
            "voltage", "battery", "gsm_signal"
    );

    // Valid operators in conditionConfig (case-insensitive). Also accepts the
    // symbolic forms (<, >, <=, >=, ==) for backwards compatibility with
    // existing rules, but new rules should use the named forms.
    private static final java.util.Set<String> VALID_OPERATORS = java.util.Set.of(
            "GT", "LT", "GTE", "LTE", "EQ",
            "<", ">", "<=", ">=", "=="
    );

    private final AlertRuleRepository alertRuleRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AlertRuleService(AlertRuleRepository alertRuleRepository,
                             OwnerContextService ownerContextService,
                             TenantGuard tenantGuard,
                             PermissionEvaluator permissionEvaluator,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper) {
        this.alertRuleRepository = alertRuleRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<AlertRuleDto> listRules(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return alertRuleRepository.findByOrganizationId(orgId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public AlertRuleDto getRule(UUID id) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
        tenantGuard.validateTenantAccess(rule.getOrganizationId());
        return toDto(rule);
    }

    @Transactional
    public AlertRuleDto createRule(AlertRuleRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();
        validateRequest(request);

        AlertRule rule = new AlertRule();
        rule.setOrganizationId(orgId);
        rule.setMachineId(request.getMachineId());
        rule.setName(request.getName());
        rule.setAlertType(request.getAlertType());
        rule.setConditionConfig(request.getConditionConfig());
        rule.setSeverity(request.getSeverity());
        rule.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        rule.setSustainMinutes(request.getSustainMinutes() != null ? request.getSustainMinutes() : 0);
        rule.setRecoveryMinutes(request.getRecoveryMinutes() != null ? request.getRecoveryMinutes() : 5);
        rule.setEscalationMinutes(request.getEscalationMinutes());
        rule.setEscalationSeverity(request.getEscalationSeverity());
        rule.setRuleVersion(1);
        rule.setUpdatedBy(permissionEvaluator.getCurrentUserId());

        rule = alertRuleRepository.save(rule);
        writeAudit(rule.getId(), orgId, "CREATE", null, request.getConditionConfig(),
                null, 1, permissionEvaluator.getCurrentUserId());

        log.info("Created alert rule id={} type={} org={}", rule.getId(), rule.getAlertType(), orgId);
        return toDto(rule);
    }

    @Transactional
    public AlertRuleDto updateRule(UUID id, AlertRuleRequest request) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
        tenantGuard.validateTenantAccess(rule.getOrganizationId());
        validateRequest(request);

        String oldConfig = rule.getConditionConfig();
        int oldVersion = rule.getRuleVersion();

        rule.setMachineId(request.getMachineId());
        rule.setName(request.getName());
        rule.setAlertType(request.getAlertType());
        rule.setConditionConfig(request.getConditionConfig());
        rule.setSeverity(request.getSeverity());
        rule.setIsActive(request.getIsActive() != null ? request.getIsActive() : rule.getIsActive());
        rule.setSustainMinutes(request.getSustainMinutes() != null ? request.getSustainMinutes() : rule.getSustainMinutes());
        rule.setRecoveryMinutes(request.getRecoveryMinutes() != null ? request.getRecoveryMinutes() : rule.getRecoveryMinutes());
        rule.setEscalationMinutes(request.getEscalationMinutes());
        rule.setEscalationSeverity(request.getEscalationSeverity());
        rule.setRuleVersion(oldVersion + 1);
        rule.setUpdatedBy(permissionEvaluator.getCurrentUserId());

        rule = alertRuleRepository.save(rule);
        writeAudit(rule.getId(), rule.getOrganizationId(), "UPDATE", oldConfig, request.getConditionConfig(),
                oldVersion, oldVersion + 1, permissionEvaluator.getCurrentUserId());

        log.info("Updated alert rule id={} version={}", rule.getId(), rule.getRuleVersion());
        return toDto(rule);
    }

    @Transactional
    public void deleteRule(UUID id) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
        tenantGuard.validateTenantAccess(rule.getOrganizationId());

        writeAudit(rule.getId(), rule.getOrganizationId(), "DELETE", rule.getConditionConfig(), null,
                rule.getRuleVersion(), null, permissionEvaluator.getCurrentUserId());

        alertRuleRepository.delete(rule);
        log.info("Deleted alert rule id={}", id);
    }

    @Transactional
    public AlertRuleDto toggleRule(UUID id, boolean active) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule not found: " + id));
        tenantGuard.validateTenantAccess(rule.getOrganizationId());

        int oldVersion = rule.getRuleVersion();
        rule.setIsActive(active);
        rule.setRuleVersion(oldVersion + 1);
        rule.setUpdatedBy(permissionEvaluator.getCurrentUserId());

        rule = alertRuleRepository.save(rule);
        writeAudit(rule.getId(), rule.getOrganizationId(), active ? "ACTIVATE" : "DEACTIVATE",
                rule.getConditionConfig(), rule.getConditionConfig(),
                oldVersion, oldVersion + 1, permissionEvaluator.getCurrentUserId());

        log.info("Toggled alert rule id={} active={}", id, active);
        return toDto(rule);
    }

    /**
     * Validates the rule request. Only supported alert types and metrics are allowed.
     * System-managed types (DEVICE_OFFLINE, SIM_EXPIRY) cannot be created via API.
     */
    private void validateRequest(AlertRuleRequest request) {
        if (!SUPPORTED_RULE_TYPES.contains(request.getAlertType())) {
            throw new IllegalArgumentException(
                    "Unsupported alert type: " + request.getAlertType() +
                    ". Supported: " + SUPPORTED_RULE_TYPES +
                    ". DEVICE_OFFLINE and SIM_EXPIRY are system-managed.");
        }

        if (!VALID_SEVERITIES.contains(request.getSeverity())) {
            throw new IllegalArgumentException("Invalid severity: " + request.getSeverity());
        }

        if (request.getEscalationSeverity() != null && !VALID_SEVERITIES.contains(request.getEscalationSeverity())) {
            throw new IllegalArgumentException("Invalid escalation severity: " + request.getEscalationSeverity());
        }

        // Validate condition_config JSON internals
        try {
            JsonNode config = objectMapper.readTree(request.getConditionConfig());

            // Validate metric
            String metric = config.path("metric").asText("");
            if (!metric.isEmpty() && !SUPPORTED_METRICS.contains(metric)) {
                throw new IllegalArgumentException("Unsupported metric: " + metric);
            }

            // Validate operator (must be one of GT, LT, GTE, LTE, EQ — case-insensitive)
            String operator = config.path("operator").asText("");
            if (operator.isEmpty()) {
                throw new IllegalArgumentException("conditionConfig.operator is required");
            }
            if (!VALID_OPERATORS.contains(operator.toUpperCase())) {
                throw new IllegalArgumentException(
                        "Invalid operator: " + operator + ". Supported: " + VALID_OPERATORS);
            }

            // Validate threshold (must be a valid number)
            JsonNode thresholdNode = config.path("threshold");
            if (thresholdNode.isMissingNode() || !thresholdNode.isNumber()) {
                throw new IllegalArgumentException(
                        "conditionConfig.threshold must be a valid number");
            }

            // Validate windowMinutes (optional, defaults to 5; must be >= 1 and <= 1440)
            JsonNode windowMinutesNode = config.path("windowMinutes");
            if (!windowMinutesNode.isMissingNode()) {
                if (!windowMinutesNode.canConvertToInt()) {
                    throw new IllegalArgumentException(
                            "conditionConfig.windowMinutes must be an integer");
                }
                int windowMinutes = windowMinutesNode.asInt();
                if (windowMinutes < 1 || windowMinutes > 1440) {
                    throw new IllegalArgumentException(
                            "conditionConfig.windowMinutes must be between 1 and 1440 (1 day max)");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e; // re-throw validation errors with their original message
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid condition_config JSON: " + e.getMessage());
        }
    }

    private void writeAudit(UUID ruleId, UUID orgId, String action,
                             String oldConfig, String newConfig,
                             Integer oldVersion, Integer newVersion,
                             UUID changedBy) {
        jdbcTemplate.update(
                "INSERT INTO alert_rule_audit (rule_id, organization_id, action, changed_by, " +
                        "old_config, new_config, old_version, new_version) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
                ruleId, orgId, action, changedBy, oldConfig, newConfig, oldVersion, newVersion
        );
    }

    private AlertRuleDto toDto(AlertRule r) {
        return new AlertRuleDto(
                r.getId(),
                r.getOrganizationId(),
                r.getMachineId(),
                r.getName(),
                r.getAlertType(),
                r.getConditionConfig(),
                r.getSeverity(),
                r.getIsActive(),
                r.getSustainMinutes(),
                r.getRecoveryMinutes(),
                r.getEscalationMinutes(),
                r.getEscalationSeverity(),
                r.getRuleVersion(),
                r.getUpdatedBy(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
