package com.yantrago.api.service;

import com.yantrago.api.dto.alert.AlertDto;
import com.yantrago.api.model.Alert;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Alert CRUD service — list, get, acknowledge alerts.
 * All queries filter by organization_id from OwnerContextService.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final RecipientResolutionService recipientResolutionService;

    public AlertService(AlertRepository alertRepository,
                        OwnerContextService ownerContextService,
                        TenantGuard tenantGuard,
                        PermissionEvaluator permissionEvaluator,
                        RecipientResolutionService recipientResolutionService) {
        this.alertRepository = alertRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.recipientResolutionService = recipientResolutionService;
    }

    @Transactional(readOnly = true)
    public Page<AlertDto> listAlerts(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return alertRepository.findByOrganizationId(orgId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<AlertDto> listUnacknowledgedAlerts(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return alertRepository.findByOrganizationIdAndIsAcknowledgedFalse(orgId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<AlertDto> listAlertsByMachine(UUID machineId, Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return alertRepository.findByOrganizationIdAndMachineId(orgId, machineId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public AlertDto getAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        tenantGuard.validateTenantAccess(alert.getOrganizationId());
        // Phase 1 fix: customers can only read alerts for machines assigned to them
        validateAssignmentAccess(alert);
        return toDto(alert);
    }

    /**
     * Acknowledges an alert — sets is_acknowledged=true, acknowledged_by, acknowledged_at.
     */
    @Transactional
    public AlertDto acknowledgeAlert(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));
        tenantGuard.validateTenantAccess(alert.getOrganizationId());
        // Phase 1 fix: customers can only acknowledge alerts for machines assigned to them
        validateAssignmentAccess(alert);

        alert.setIsAcknowledged(true);
        alert.setAcknowledgedBy(permissionEvaluator.getCurrentUserId());
        alert.setAcknowledgedAt(LocalDateTime.now());

        alert = alertRepository.save(alert);
        log.info("Acknowledged alert id={} by user={}", alert.getId(), alert.getAcknowledgedBy());
        return toDto(alert);
    }

    /**
     * Phase 1 fix: validates that the current user has assignment-based access
     * to the alert's machine. Admins (admin, org_admin, super_admin) bypass
     * this check and can read/acknowledge any alert in their organization.
     */
    private void validateAssignmentAccess(Alert alert) {
        if (permissionEvaluator.hasAnyRole("admin", "org_admin", "super_admin")) {
            return;
        }
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (alert.getMachineId() == null) {
            return;
        }
        if (!recipientResolutionService.revalidateAccess(
                alert.getOrganizationId(), alert.getMachineId(), userId)) {
            log.warn("Assignment access denied: user={} alert={} machine={}",
                    userId, alert.getId(), alert.getMachineId());
            throw new SecurityException("Access denied: alert is not for a machine assigned to you");
        }
    }

    private AlertDto toDto(Alert a) {
        return new AlertDto(
                a.getId(),
                a.getOrganizationId(),
                a.getAlertRuleId(),
                a.getMachineId(),
                a.getDeviceId(),
                a.getAlertType(),
                a.getSeverity(),
                a.getMessage(),
                a.getIsAcknowledged(),
                a.getAcknowledgedBy(),
                a.getAcknowledgedAt(),
                a.getTriggeredAt(),
                a.getCreatedAt()
        );
    }
}
