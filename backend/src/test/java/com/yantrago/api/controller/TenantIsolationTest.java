package com.yantrago.api.controller;

import com.yantrago.api.model.Alert;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineCommand;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that cross-tenant access is blocked by TenantGuard.
 *
 * Per AGENTS.md rule 7: all APIs must enforce tenant isolation (organization_id from JWT).
 * Per AGENTS.md rule 8: never trust tenant_id/organization_id supplied by the frontend.
 */
class TenantIsolationTest {

    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = new TenantGuard(ownerContextService);
    }

    @Test
    @DisplayName("validateTenantAccess should throw SecurityException for cross-tenant access")
    void validateTenantAccess_shouldThrowForCrossTenant() {
        // Current tenant is orgA
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);

        // Resource belongs to orgB
        SecurityException ex = assertThrows(SecurityException.class,
                () -> tenantGuard.validateTenantAccess(orgB));
        assertTrue(ex.getMessage().contains("Cross-tenant access denied"));
    }

    @Test
    @DisplayName("validateTenantAccess should pass for same-tenant access")
    void validateTenantAccess_shouldPassForSameTenant() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);

        assertDoesNotThrow(() -> tenantGuard.validateTenantAccess(orgA));
    }

    @Test
    @DisplayName("validateTenantAccess should throw when resource has null organization_id")
    void validateTenantAccess_shouldThrowForNullOrgId() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);

        assertThrows(SecurityException.class,
                () -> tenantGuard.validateTenantAccess(null));
    }

    @Test
    @DisplayName("super_admin (null tenant context) should bypass tenant check")
    void validateTenantAccess_shouldBypassForSuperAdmin() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(null);

        assertDoesNotThrow(() -> tenantGuard.validateTenantAccess(orgB));
    }

    @Test
    @DisplayName("canAccess should return false for cross-tenant")
    void canAccess_shouldReturnFalseForCrossTenant() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);

        assertFalse(tenantGuard.canAccess(orgB));
    }

    @Test
    @DisplayName("canAccess should return true for same-tenant")
    void canAccess_shouldReturnTrueForSameTenant() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);

        assertTrue(tenantGuard.canAccess(orgA));
    }

    @Test
    @DisplayName("canAccess should return true for super_admin")
    void canAccess_shouldReturnTrueForSuperAdmin() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(null);

        assertTrue(tenantGuard.canAccess(orgB));
    }

    @Test
    @DisplayName("canAccess should return false for null resource org")
    void canAccess_shouldReturnFalseForNullResource() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);

        assertFalse(tenantGuard.canAccess(null));
    }

    @Test
    @DisplayName("Machine from orgA should not be accessible by orgB tenant context")
    void machineFromOrgA_shouldNotBeAccessibleByOrgB() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgB);

        Machine machine = new Machine();
        machine.setOrganizationId(orgA);

        assertThrows(SecurityException.class,
                () -> tenantGuard.validateTenantAccess(machine.getOrganizationId()));
    }

    @Test
    @DisplayName("MachineCommand from orgA should not be accessible by orgB tenant context")
    void commandFromOrgA_shouldNotBeAccessibleByOrgB() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgB);

        MachineCommand command = new MachineCommand();
        command.setOrganizationId(orgA);

        assertThrows(SecurityException.class,
                () -> tenantGuard.validateTenantAccess(command.getOrganizationId()));
    }

    @Test
    @DisplayName("Alert from orgA should not be accessible by orgB tenant context")
    void alertFromOrgA_shouldNotBeAccessibleByOrgB() {
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgB);

        Alert alert = new Alert();
        alert.setOrganizationId(orgA);

        assertThrows(SecurityException.class,
                () -> tenantGuard.validateTenantAccess(alert.getOrganizationId()));
    }
}
