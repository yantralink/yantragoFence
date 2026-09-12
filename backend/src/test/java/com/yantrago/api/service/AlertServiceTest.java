package com.yantrago.api.service;

import com.yantrago.api.dto.alert.AlertDto;
import com.yantrago.api.model.Alert;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertService.
 *
 * Per AGENTS.md rule 7: all queries filter by organization_id from OwnerContextService.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 */
class AlertServiceTest {

    private AlertRepository alertRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private PermissionEvaluator permissionEvaluator;
    private RecipientResolutionService recipientResolutionService;
    private AlertService alertService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID alertId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        recipientResolutionService = mock(RecipientResolutionService.class);
        alertService = new AlertService(alertRepository, ownerContextService, tenantGuard,
                permissionEvaluator, recipientResolutionService);
    }

    private Alert createAlert(UUID org, boolean acknowledged) {
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setOrganizationId(org);
        alert.setMachineId(machineId);
        alert.setAlertType("LOW_BATTERY");
        alert.setSeverity("WARNING");
        alert.setMessage("Battery below 20%");
        alert.setIsAcknowledged(acknowledged);
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setCreatedAt(LocalDateTime.now());
        return alert;
    }

    @Test
    @DisplayName("listAlerts should filter by organization_id")
    void listAlerts_shouldFilterByOrgId() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        Alert alert = createAlert(orgId, false);
        Page<Alert> page = new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1);
        when(alertRepository.findByOrganizationId(orgId, PageRequest.of(0, 20))).thenReturn(page);

        Page<AlertDto> result = alertService.listAlerts(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("LOW_BATTERY", result.getContent().get(0).getAlertType());
        verify(alertRepository).findByOrganizationId(orgId, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("listUnacknowledgedAlerts should filter by org and isAcknowledgedFalse")
    void listUnacknowledgedAlerts_shouldFilterCorrectly() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        Alert alert = createAlert(orgId, false);
        Page<Alert> page = new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1);
        when(alertRepository.findByOrganizationIdAndIsAcknowledgedFalse(orgId, PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AlertDto> result = alertService.listUnacknowledgedAlerts(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().get(0).getIsAcknowledged());
    }

    @Test
    @DisplayName("listAlertsByMachine should filter by org and machineId")
    void listAlertsByMachine_shouldFilterCorrectly() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        Alert alert = createAlert(orgId, false);
        Page<Alert> page = new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1);
        when(alertRepository.findByOrganizationIdAndMachineId(orgId, machineId, PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AlertDto> result = alertService.listAlertsByMachine(machineId, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(machineId, result.getContent().get(0).getMachineId());
    }

    @Test
    @DisplayName("getAlert should throw when alert not found")
    void getAlert_shouldThrowWhenNotFound() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> alertService.getAlert(alertId));
    }

    @Test
    @DisplayName("getAlert should validate tenant access")
    void getAlert_shouldValidateTenantAccess() {
        Alert alert = createAlert(orgId, false);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(permissionEvaluator.hasAnyRole("admin", "org_admin", "super_admin")).thenReturn(true);

        alertService.getAlert(alertId);

        verify(tenantGuard).validateTenantAccess(orgId);
    }

    @Test
    @DisplayName("acknowledgeAlert should set isAcknowledged=true, acknowledgedBy, acknowledgedAt")
    void acknowledgeAlert_shouldSetAcknowledgedFields() {
        Alert alert = createAlert(orgId, false);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(permissionEvaluator.hasAnyRole("admin", "org_admin", "super_admin")).thenReturn(true);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertDto result = alertService.acknowledgeAlert(alertId);

        assertTrue(result.getIsAcknowledged());
        assertEquals(userId, result.getAcknowledgedBy());
        assertNotNull(result.getAcknowledgedAt());
        verify(tenantGuard).validateTenantAccess(orgId);
    }

    @Test
    @DisplayName("acknowledgeAlert should throw when alert not found")
    void acknowledgeAlert_shouldThrowWhenNotFound() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> alertService.acknowledgeAlert(alertId));
    }

    @Test
    @DisplayName("acknowledgeAlert should validate tenant access before acknowledging")
    void acknowledgeAlert_shouldValidateTenantAccess() {
        Alert alert = createAlert(orgId, false);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(permissionEvaluator.hasAnyRole("admin", "org_admin", "super_admin")).thenReturn(true);
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simulate cross-tenant access denied
        doThrow(new SecurityException("Cross-tenant access denied"))
                .when(tenantGuard).validateTenantAccess(orgId);

        assertThrows(SecurityException.class, () -> alertService.acknowledgeAlert(alertId));
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("acknowledgeAlert should reject customer without assignment access")
    void acknowledgeAlert_shouldRejectCustomerWithoutAssignment() {
        Alert alert = createAlert(orgId, false);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(permissionEvaluator.hasAnyRole("admin", "org_admin", "super_admin")).thenReturn(false);
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId)).thenReturn(false);

        assertThrows(SecurityException.class, () -> alertService.acknowledgeAlert(alertId));
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAlert should allow customer with assignment access")
    void getAlert_shouldAllowCustomerWithAssignment() {
        Alert alert = createAlert(orgId, false);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(permissionEvaluator.hasAnyRole("admin", "org_admin", "super_admin")).thenReturn(false);
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId)).thenReturn(true);

        AlertDto result = alertService.getAlert(alertId);

        assertNotNull(result);
        assertEquals(alertId, result.getId());
    }
}
