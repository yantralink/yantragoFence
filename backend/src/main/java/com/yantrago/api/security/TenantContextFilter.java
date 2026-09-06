package com.yantrago.api.security;

import com.yantrago.api.service.OwnerContextService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves organization_id from the JWT claims (set by JwtAuthFilter) and
 * sets it in the OwnerContextService (ThreadLocal) for the duration of the request.
 *
 * Per AGENTS.md rule 7: organization_id always comes from JWT, never from request body.
 * Per AGENTS.md rule 8: never trust tenant_id/organization_id supplied by the frontend.
 *
 * Runs after JwtAuthFilter. Clears the context after the request completes.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final OwnerContextService ownerContextService;

    public TenantContextFilter(OwnerContextService ownerContextService) {
        this.ownerContextService = ownerContextService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Claims claims = (Claims) request.getAttribute("jwt.claims");
            if (claims != null) {
                java.util.UUID orgId = extractOrgId(claims);
                ownerContextService.setOrganizationId(orgId);
            }
            filterChain.doFilter(request, response);
        } finally {
            ownerContextService.clear();
        }
    }

    /**
     * Extract organizationId from claims. Returns null for super_admin (platform-level).
     */
    private java.util.UUID extractOrgId(Claims claims) {
        String orgId = claims.get("organizationId", String.class);
        return orgId != null ? java.util.UUID.fromString(orgId) : null;
    }
}
