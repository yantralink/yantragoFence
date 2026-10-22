package com.yantrago.api.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Holds the current tenant (organization_id) resolved from the JWT.
 * Set by TenantContextFilter on each request, cleared after the request completes.
 * All tenant-scoped service queries must use getOrganizationId() — never trust
 * a tenant_id supplied by the frontend (AGENTS.md rule 8).
 *
 * Audit SEC-07: isSuperAdmin() previously returned true whenever the ThreadLocal
 * was null, which conflated "platform-level user" with "context propagation
 * failed". Now it checks the SecurityContext for the explicit ROLE_super_admin
 * authority.
 */
@Service
public class OwnerContextService {

    private static final String ROLE_SUPER_ADMIN = "ROLE_super_admin";

    private static final ThreadLocal<UUID> TENANT_CONTEXT = new ThreadLocal<>();

    public void setOrganizationId(UUID organizationId) {
        TENANT_CONTEXT.set(organizationId);
    }

    public UUID getOrganizationId() {
        UUID orgId = TENANT_CONTEXT.get();
        if (orgId == null) {
            throw new IllegalStateException("No tenant context set for this request. Ensure TenantContextFilter is configured.");
        }
        return orgId;
    }

    /**
     * Returns the organization_id or null if not set (e.g. super_admin context).
     * Use this when the caller may be a platform-level user with no tenant.
     */
    public UUID getOrganizationIdOrNull() {
        return TENANT_CONTEXT.get();
    }

    /**
     * Returns true if the current user has the ROLE_super_admin authority.
     *
     * Audit SEC-07: previously this returned true whenever the ThreadLocal
     * tenant context was null. That conflated "platform-level user" with
     * "context propagation failed or claim missing". Now it checks the
     * SecurityContext explicitly.
     */
    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_SUPER_ADMIN::equals);
    }

    public void clear() {
        TENANT_CONTEXT.remove();
    }
}
