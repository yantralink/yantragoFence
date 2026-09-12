package com.yantrago.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RBAC permission evaluator.
 * Checks whether the current user has the required permission.
 *
 * Permissions follow the format "resource:action" (e.g. "machine:read", "command:write").
 * Super_admin (ROLE_super_admin) bypasses all checks.
 *
 * In a full implementation, this would query the role_permissions + user_roles tables
 * via JdbcTemplate. For now, it checks authorities from the SecurityContext.
 */
@Component
public class PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PermissionEvaluator.class);
    private static final String SUPER_ADMIN_ROLE = "ROLE_super_admin";

    /**
     * Checks if the current user has the given permission.
     * @param permission e.g. "machine:read"
     * @return true if authorized
     */
    public boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        // Super_admin bypasses
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (SUPER_ADMIN_ROLE.equals(ga.getAuthority())) {
                return true;
            }
        }

        // Check if any authority matches the permission
        // In a full implementation, this would query role_permissions for the user's roles.
        // For now, we check if the permission is in the authorities list.
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (permission.equals(ga.getAuthority())) {
                return true;
            }
        }

        log.debug("Permission denied: user={} lacks permission={}", auth.getName(), permission);
        return false;
    }

    /**
     * Returns the current user's ID from the SecurityContext.
     */
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    /**
     * Checks if the current user has any of the given roles.
     * Role names are matched as "ROLE_<name>" authorities.
     * @param roleNames role names to check (e.g. "admin", "org_admin")
     * @return true if the user has any of the roles
     */
    public boolean hasAnyRole(String... roleNames) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (String role : roleNames) {
            String roleAuthority = "ROLE_" + role;
            for (GrantedAuthority ga : auth.getAuthorities()) {
                if (roleAuthority.equals(ga.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
