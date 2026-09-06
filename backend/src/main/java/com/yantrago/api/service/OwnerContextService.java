package com.yantrago.api.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Holds the current tenant (organization_id) resolved from the JWT.
 * Set by TenantContextFilter on each request, cleared after the request completes.
 * All tenant-scoped service queries must use getOrganizationId() — never trust
 * a tenant_id supplied by the frontend (AGENTS.md rule 8).
 */
@Service
public class OwnerContextService {

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

    public boolean isSuperAdmin() {
        return TENANT_CONTEXT.get() == null;
    }

    public void clear() {
        TENANT_CONTEXT.remove();
    }
}
