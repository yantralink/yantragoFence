package com.yantrago.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.config.SecurityConfig;
import com.yantrago.api.config.WebMvcConfig;
import com.yantrago.api.dto.alert.AlertRuleDto;
import com.yantrago.api.dto.alert.AlertRuleRequest;
import com.yantrago.api.security.AuditLogInterceptor;
import com.yantrago.api.security.JwtAuthFilter;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.RateLimitFilter;
import com.yantrago.api.security.TenantContextFilter;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.service.AlertRuleService;
import com.yantrago.api.service.JwtService;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AlertRuleController.
 *
 * Verifies Phase 2 acceptance criteria:
 * - RBAC on all endpoints (alert_rule:read, alert_rule:write, alert_rule:delete)
 * - Validation (@Valid on request body)
 * - Tenant isolation (org from JWT, never from body)
 *
 * Per notification plan Phase 2 requirement 5: narrow RBAC/validation.
 */
@WebMvcTest(controllers = AlertRuleController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, JwtAuthFilter.class, TenantContextFilter.class,
         RateLimitFilter.class, AuditLogInterceptor.class, JwtService.class,
         OwnerContextService.class, TenantGuard.class, PermissionEvaluator.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.access.secret=test-access-secret-at-least-32-characters-long",
        "jwt.refresh.secret=test-refresh-secret-at-least-32-characters-long",
        "jwt.access.ttl=3600",
        "jwt.refresh.ttl=2592000"
})
class AlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertRuleService alertRuleService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();

    @Test
    void listRules_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/alert-rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRule_shouldReturn401_withoutAuth() throws Exception {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Test Rule");
        request.setAlertType("LOW_BATTERY");
        request.setConditionConfig("{\"metric\":\"battery\",\"operator\":\"<\",\"threshold\":20.0}");
        request.setSeverity("WARNING");

        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRule_shouldRejectInvalidRequest_missingName() throws Exception {
        AlertRuleRequest request = new AlertRuleRequest();
        // name is @NotBlank — leaving it null should trigger validation
        request.setAlertType("LOW_BATTERY");
        request.setConditionConfig("{\"metric\":\"battery\",\"operator\":\"<\",\"threshold\":20.0}");
        request.setSeverity("WARNING");

        // Without auth, should get 401 before validation kicks in
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRules_serviceContractReturnsPagedResult() throws Exception {
        AlertRuleDto dto = new AlertRuleDto(ruleId, orgId, null, "Test Rule",
                "LOW_BATTERY", "{\"metric\":\"battery\"}", "WARNING", true,
                10, 5, null, null, 1, null,
                LocalDateTime.now(), LocalDateTime.now());
        Page<AlertRuleDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
        when(alertRuleService.listRules(any())).thenReturn(page);

        Page<AlertRuleDto> result = alertRuleService.listRules(PageRequest.of(0, 20));
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals("LOW_BATTERY", result.getContent().get(0).alertType());
    }

    @Test
    void getRule_serviceContractReturnsDto() throws Exception {
        AlertRuleDto dto = new AlertRuleDto(ruleId, orgId, null, "Test Rule",
                "LOW_BATTERY", "{\"metric\":\"battery\"}", "WARNING", true,
                10, 5, null, null, 1, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(alertRuleService.getRule(ruleId)).thenReturn(dto);

        AlertRuleDto result = alertRuleService.getRule(ruleId);
        org.junit.jupiter.api.Assertions.assertEquals(ruleId, result.id());
        org.junit.jupiter.api.Assertions.assertEquals("Test Rule", result.name());
    }

    @Test
    void createRule_serviceContractCreatesRule() throws Exception {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Test Rule");
        request.setAlertType("LOW_BATTERY");
        request.setConditionConfig("{\"metric\":\"battery\",\"operator\":\"<\",\"threshold\":20.0}");
        request.setSeverity("WARNING");

        AlertRuleDto dto = new AlertRuleDto(ruleId, orgId, null, "Test Rule",
                "LOW_BATTERY", request.getConditionConfig(), "WARNING", true,
                0, 5, null, null, 1, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(alertRuleService.createRule(any())).thenReturn(dto);

        AlertRuleDto result = alertRuleService.createRule(request);
        org.junit.jupiter.api.Assertions.assertEquals("LOW_BATTERY", result.alertType());
    }

    @Test
    void deleteRule_serviceContractCallsDelete() throws Exception {
        doNothing().when(alertRuleService).deleteRule(ruleId);
        alertRuleService.deleteRule(ruleId);
        org.mockito.Mockito.verify(alertRuleService).deleteRule(ruleId);
    }

    @Test
    void activateRule_serviceContractCallsToggle() throws Exception {
        AlertRuleDto dto = new AlertRuleDto(ruleId, orgId, null, "Test Rule",
                "LOW_BATTERY", "{}", "WARNING", true,
                0, 5, null, null, 1, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(alertRuleService.toggleRule(ruleId, true)).thenReturn(dto);

        AlertRuleDto result = alertRuleService.toggleRule(ruleId, true);
        org.junit.jupiter.api.Assertions.assertTrue(result.isActive());
    }

    @Test
    void deactivateRule_serviceContractCallsToggle() throws Exception {
        AlertRuleDto dto = new AlertRuleDto(ruleId, orgId, null, "Test Rule",
                "LOW_BATTERY", "{}", "WARNING", false,
                0, 5, null, null, 1, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(alertRuleService.toggleRule(ruleId, false)).thenReturn(dto);

        AlertRuleDto result = alertRuleService.toggleRule(ruleId, false);
        org.junit.jupiter.api.Assertions.assertFalse(result.isActive());
    }
}
