package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.dto.alert.AlertRuleDto;
import com.yantrago.api.dto.alert.AlertRuleRequest;
import com.yantrago.api.model.AlertRule;
import com.yantrago.api.repository.AlertRuleRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for AlertRuleService.
 *
 * Verifies Phase 2 acceptance criteria:
 * - Typed rule configuration with narrow RBAC/validation
 * - Audit history on create/update/delete
 * - Unsupported metrics and unverified voltage/expiry semantics disabled
 * - Tenant isolation
 *
 * Per notification plan Phase 2 requirement 5.
 */
class AlertRuleServiceTest {

    private AlertRuleRepository alertRuleRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private PermissionEvaluator permissionEvaluator;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;

    private AlertRuleService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        alertRuleRepository = mock(AlertRuleRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();

        service = new AlertRuleService(alertRuleRepository, ownerContextService,
                tenantGuard, permissionEvaluator, jdbcTemplate, objectMapper);

        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
    }

    private AlertRuleRequest createValidRequest() {
        AlertRuleRequest req = new AlertRuleRequest();
        req.setName("Low Battery Rule");
        req.setAlertType("LOW_BATTERY");
        req.setConditionConfig("{\"metric\":\"battery\",\"operator\":\"<\",\"threshold\":20.0}");
        req.setSeverity("WARNING");
        req.setSustainMinutes(10);
        req.setRecoveryMinutes(5);
        req.setIsActive(true);
        return req;
    }

    private AlertRule createRule() {
        AlertRule rule = new AlertRule();
        rule.setId(ruleId);
        rule.setOrganizationId(orgId);
        rule.setName("Low Battery Rule");
        rule.setAlertType("LOW_BATTERY");
        rule.setConditionConfig("{\"metric\":\"battery\",\"operator\":\"<\",\"threshold\":20.0}");
        rule.setSeverity("WARNING");
        rule.setIsActive(true);
        rule.setSustainMinutes(10);
        rule.setRecoveryMinutes(5);
        rule.setRuleVersion(1);
        rule.setUpdatedBy(userId);
        return rule;
    }

    @Test
    @DisplayName("createRule: creates rule and writes audit")
    void createRule_createsRuleAndWritesAudit() {
        AlertRuleRequest req = createValidRequest();
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRule r = inv.getArgument(0);
            r.setId(ruleId);
            return r;
        });

        AlertRuleDto result = service.createRule(req);

        assertNotNull(result);
        assertEquals("LOW_BATTERY", result.alertType());
        assertEquals(orgId, result.organizationId());
        verify(alertRuleRepository).save(any(AlertRule.class));
        verify(jdbcTemplate).update(
                anyString(), eq(ruleId), eq(orgId), eq("CREATE"),
                eq(userId), eq(null), eq("{\"metric\":\"battery\",\"operator\":\"<\",\"threshold\":20.0}"), eq(null), eq(1));
    }

    @Test
    @DisplayName("createRule: rejects unsupported alert type (DEVICE_OFFLINE is system-managed)")
    void createRule_rejectsSystemManagedType() {
        AlertRuleRequest req = createValidRequest();
        req.setAlertType("DEVICE_OFFLINE");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createRule(req));
        assertTrue(ex.getMessage().contains("Unsupported alert type"));
        assertTrue(ex.getMessage().contains("system-managed"));
    }

    @Test
    @DisplayName("createRule: rejects SIM_EXPIRY (system-managed)")
    void createRule_rejectsSimExpiry() {
        AlertRuleRequest req = createValidRequest();
        req.setAlertType("SIM_EXPIRY");

        assertThrows(IllegalArgumentException.class, () -> service.createRule(req));
    }

    @Test
    @DisplayName("createRule: rejects invalid severity")
    void createRule_rejectsInvalidSeverity() {
        AlertRuleRequest req = createValidRequest();
        req.setSeverity("URGENT");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createRule(req));
        assertTrue(ex.getMessage().contains("Invalid severity"));
    }

    @Test
    @DisplayName("createRule: rejects unsupported metric")
    void createRule_rejectsUnsupportedMetric() {
        AlertRuleRequest req = createValidRequest();
        req.setConditionConfig("{\"metric\":\"temperature\",\"operator\":\">\",\"threshold\":50.0}");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createRule(req));
        assertTrue(ex.getMessage().contains("Unsupported metric"));
    }

    @Test
    @DisplayName("createRule: rejects invalid JSON condition config")
    void createRule_rejectsInvalidJson() {
        AlertRuleRequest req = createValidRequest();
        req.setConditionConfig("not valid json {{{");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createRule(req));
        assertTrue(ex.getMessage().contains("Invalid condition_config JSON"));
    }

    @Test
    @DisplayName("updateRule: updates rule, increments version, writes audit")
    void updateRule_updatesAndWritesAudit() {
        AlertRule existing = createRule();
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRuleRequest req = createValidRequest();
        req.setName("Updated Rule");
        req.setSeverity("CRITICAL");

        AlertRuleDto result = service.updateRule(ruleId, req);

        assertEquals("Updated Rule", result.name());
        assertEquals("CRITICAL", result.severity());
        assertEquals(2, result.ruleVersion());
        verify(tenantGuard).validateTenantAccess(orgId);
        verify(jdbcTemplate).update(
                anyString(), eq(ruleId), eq(orgId), eq("UPDATE"),
                eq(userId), anyString(), anyString(), eq(1), eq(2));
    }

    @Test
    @DisplayName("updateRule: rejects when rule not found")
    void updateRule_rejectsWhenNotFound() {
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateRule(ruleId, createValidRequest()));
    }

    @Test
    @DisplayName("updateRule: tenant guard validates access")
    void updateRule_tenantGuardValidates() {
        AlertRule existing = createRule();
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));
        doThrow(new SecurityException("Tenant access denied"))
                .when(tenantGuard).validateTenantAccess(orgId);

        assertThrows(SecurityException.class,
                () -> service.updateRule(ruleId, createValidRequest()));
    }

    @Test
    @DisplayName("deleteRule: deletes and writes audit")
    void deleteRule_deletesAndWritesAudit() {
        AlertRule existing = createRule();
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

        service.deleteRule(ruleId);

        verify(alertRuleRepository).delete(existing);
        verify(tenantGuard).validateTenantAccess(orgId);
        verify(jdbcTemplate).update(
                anyString(), eq(ruleId), eq(orgId), eq("DELETE"),
                eq(userId), any(), any(), eq(1), any());
    }

    @Test
    @DisplayName("deleteRule: rejects when rule not found")
    void deleteRule_rejectsWhenNotFound() {
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.deleteRule(ruleId));
    }

    @Test
    @DisplayName("getRule: returns DTO and validates tenant access")
    void getRule_returnsDtoAndValidatesTenant() {
        AlertRule existing = createRule();
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

        AlertRuleDto result = service.getRule(ruleId);

        assertNotNull(result);
        assertEquals(ruleId, result.id());
        verify(tenantGuard).validateTenantAccess(orgId);
    }

    @Test
    @DisplayName("getRule: rejects when not found")
    void getRule_rejectsWhenNotFound() {
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getRule(ruleId));
    }

    @Test
    @DisplayName("listRules: returns paged rules for organization")
    void listRules_returnsPagedRules() {
        AlertRule rule = createRule();
        Page<AlertRule> page = new PageImpl<>(List.of(rule), PageRequest.of(0, 20), 1);
        when(alertRuleRepository.findByOrganizationId(orgId, PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AlertRuleDto> result = service.listRules(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("LOW_BATTERY", result.getContent().get(0).alertType());
    }

    @Test
    @DisplayName("toggleRule: activates rule")
    void toggleRule_activatesRule() {
        AlertRule existing = createRule();
        existing.setIsActive(false);
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRuleDto result = service.toggleRule(ruleId, true);

        assertTrue(result.isActive());
        verify(tenantGuard).validateTenantAccess(orgId);
    }

    @Test
    @DisplayName("toggleRule: deactivates rule")
    void toggleRule_deactivatesRule() {
        AlertRule existing = createRule();
        existing.setIsActive(true);
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));
        when(alertRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRuleDto result = service.toggleRule(ruleId, false);

        assertFalse(result.isActive());
    }

    @Test
    @DisplayName("createRule: defaults sustainMinutes to 0 when null")
    void createRule_defaultsSustainMinutesToZero() {
        AlertRuleRequest req = createValidRequest();
        req.setSustainMinutes(null);
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRule r = inv.getArgument(0);
            r.setId(ruleId);
            return r;
        });

        AlertRuleDto result = service.createRule(req);

        assertEquals(0, result.sustainMinutes());
    }

    @Test
    @DisplayName("createRule: defaults recoveryMinutes to 5 when null")
    void createRule_defaultsRecoveryMinutesToFive() {
        AlertRuleRequest req = createValidRequest();
        req.setRecoveryMinutes(null);
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRule r = inv.getArgument(0);
            r.setId(ruleId);
            return r;
        });

        AlertRuleDto result = service.createRule(req);

        assertEquals(5, result.recoveryMinutes());
    }

    @Test
    @DisplayName("createRule: defaults isActive to true when null")
    void createRule_defaultsIsActiveToTrue() {
        AlertRuleRequest req = createValidRequest();
        req.setIsActive(null);
        when(alertRuleRepository.save(any())).thenAnswer(inv -> {
            AlertRule r = inv.getArgument(0);
            r.setId(ruleId);
            return r;
        });

        AlertRuleDto result = service.createRule(req);

        assertTrue(result.isActive());
    }
}
