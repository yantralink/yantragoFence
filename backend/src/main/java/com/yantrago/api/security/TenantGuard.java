package com.yantrago.api.security;

import com.yantrago.api.service.OwnerContextService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Prevents cross-tenant access by validating that a resource's organization_id
 * matches the current tenant context from the JWT.
 *
 * Usage in services:
 *   tenantGuard.validateTenantAccess(resource.getOrganizationId());
 *
 * Per AGENTS.md rule 7: all APIs must enforce tenant isolation.
 * Per AGENTS.md rule 8: never trust tenant_id/organization_id from the frontend.
 */
@Component
public class TenantGuard {

    private final OwnerContextService ownerContextService;

    public TenantGuard(OwnerContextService ownerContextService) {
        this.ownerContextService = ownerContextService;
    }

    /**
     * Validates that the given resource organization_id matches the current tenant.
     * Throws SecurityException if there is a mismatch.
     * Super_admin (null tenant context) bypasses the check.
     */
    public void validateTenantAccess(UUID resourceOrganizationId) {
        if (resourceOrganizationId == null) {
            throw new SecurityException("Resource has no organization_id — cannot validate tenant access");
        }
        UUID currentOrg = ownerContextService.getOrganizationIdOrNull();
        if (currentOrg == null) {
            // Super_admin — bypass
            return;
        }
        if (!currentOrg.equals(resourceOrganizationId)) {
            throw new SecurityException("Cross-tenant access denied: resource does not belong to the current organization");
        }
    }

    /**
     * Returns true if the current context can access the given organization's resources.
     */
    public boolean canAccess(UUID resourceOrganizationId) {
        if (resourceOrganizationId == null) {
            return false;
        }
        UUID currentOrg = ownerContextService.getOrganizationIdOrNull();
        if (currentOrg == null) {
            return true; // super_admin
        }
        return currentOrg.equals(resourceOrganizationId);
    }
}
