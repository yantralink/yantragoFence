package com.yantrago.api.controller;

import com.yantrago.api.dto.alert.AlertRuleDto;
import com.yantrago.api.dto.alert.AlertRuleRequest;
import com.yantrago.api.service.AlertRuleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Alert rule management endpoints — CRUD for alert rules with RBAC and audit.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 12: validation, logging, security checks.
 *
 * Only user-configurable rule types (LOW_BATTERY, VOLTAGE_DROP, GSM_SIGNAL_LOW)
 * are accepted. System-managed types (DEVICE_OFFLINE, SIM_EXPIRY) are handled
 * by schedulers and cannot be created via this API.
 */
@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('alert_rule:read') or hasRole('super_admin')")
    public ResponseEntity<Page<AlertRuleDto>> listRules(Pageable pageable) {
        return ResponseEntity.ok(alertRuleService.listRules(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('alert_rule:read') or hasRole('super_admin')")
    public ResponseEntity<AlertRuleDto> getRule(@PathVariable UUID id) {
        return ResponseEntity.ok(alertRuleService.getRule(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('alert_rule:write') or hasRole('super_admin')")
    public ResponseEntity<AlertRuleDto> createRule(@Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.ok(alertRuleService.createRule(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('alert_rule:write') or hasRole('super_admin')")
    public ResponseEntity<AlertRuleDto> updateRule(@PathVariable UUID id,
                                                    @Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.ok(alertRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('alert_rule:delete') or hasRole('super_admin')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        alertRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('alert_rule:write') or hasRole('super_admin')")
    public ResponseEntity<AlertRuleDto> activateRule(@PathVariable UUID id) {
        return ResponseEntity.ok(alertRuleService.toggleRule(id, true));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('alert_rule:write') or hasRole('super_admin')")
    public ResponseEntity<AlertRuleDto> deactivateRule(@PathVariable UUID id) {
        return ResponseEntity.ok(alertRuleService.toggleRule(id, false));
    }
}
