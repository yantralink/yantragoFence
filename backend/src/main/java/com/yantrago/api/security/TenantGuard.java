package com.yantrago.api.security;

import com.yantrago.api.service.OwnerContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
 *
 * Audit SEC-07: previously, a null tenant context bypassed the check entirely
 * (assumed super_admin). Now the bypass is gated on an explicit ROLE_super_admin
 * authority in the SecurityContext. A signed non-admin token that lacks the
 * organization claim, or a context-propagation failure, will fail closed.
 */
@Component
public class TenantGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantGuard.class);
    private static final String ROLE_SUPER_ADMIN = "ROLE_super_admin";

    private final OwnerContextService ownerContextService;

    public TenantGuard(OwnerContextService ownerContextService) {
        this.ownerContextService = ownerContextService;
    }

    /**
     * Validates that the given resource organization_id matches the current tenant.
     * Throws SecurityException if there is a mismatch.
     *
     * Super_admin (ROLE_super_admin authority in SecurityContext) bypasses the check.
     * A null tenant context without ROLE_super_admin is denied (fail-closed).
     */
    public void validateTenantAccess(UUID resourceOrganizationId) {
        if (resourceOrganizationId == null) {
            throw new SecurityException("Resource has no organization_id — cannot validate tenant access");
        }
        UUID currentOrg = ownerContextService.getOrganizationIdOrNull();
        if (currentOrg == null) {
            // Null context — only bypass if the user explicitly has ROLE_super_admin
            if (!isSuperAdmin()) {
                log.warn("Tenant access denied: null context without ROLE_super_admin");
                throw new SecurityException(
                        "Tenant access denied: no organization context and user is not super_admin");
            }
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
            // Null context — only allow if explicitly super_admin
            return isSuperAdmin();
        }
        return currentOrg.equals(resourceOrganizationId);
    }

    /**
     * Checks the SecurityContext for the ROLE_super_admin authority.
     * Does NOT rely on null tenant context — that was the SEC-07 fail-open bug.
     */
    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_SUPER_ADMIN::equals);
    }
}
