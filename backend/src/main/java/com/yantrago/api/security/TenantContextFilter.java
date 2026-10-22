package com.yantrago.api.security;

import com.yantrago.api.service.OwnerContextService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
 *
 * Audit SEC-07: previously, a null organizationId claim was silently accepted
 * for any authenticated user, which meant non-admin tokens without the claim
 * bypassed tenant isolation downstream. Now, if the claim is absent, the filter
 * asserts that the user has ROLE_super_admin; otherwise it rejects the request.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
    private static final String ROLE_SUPER_ADMIN = "ROLE_super_admin";

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
                if (orgId == null) {
                    // Null org claim — only allowed for super_admin
                    if (!isSuperAdmin()) {
                        log.warn("Authenticated non-super-admin user has no organization_id claim — rejecting request from {}",
                                request.getRemoteAddr());
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "Missing organization context");
                        return;
                    }
                }
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

    /**
     * Checks the SecurityContext for the ROLE_super_admin authority.
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
