package com.yantrago.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.UUID;

/**
 * Logs sensitive actions to the audit_logs table (via AuditLogService in later phases).
 * For now, logs to SLF4J. In Phase 9, this will be wired to AuditLogService.
 *
 * Sensitive actions: POST/PUT/DELETE on /api/v1/commands, /api/v1/users, /api/v1/settings,
 * /api/v1/auth/login, /api/v1/auth/logout.
 */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditLogInterceptor.class);

    private static final Set<String> SENSITIVE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> SENSITIVE_PATHS = Set.of(
            "/api/v1/commands",
            "/api/v1/users",
            "/api/v1/settings",
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/refresh"
    );

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (!SENSITIVE_METHODS.contains(method)) {
            return;
        }

        boolean isSensitive = SENSITIVE_PATHS.stream().anyMatch(path::startsWith);
        if (!isSensitive) {
            return;
        }

        int status = response.getStatus();
        UUID userId = getCurrentUserId();
        String ip = getClientIp(request);

        // In Phase 9, this will call AuditLogService.log(...)
        log.info("AUDIT action={} path={} method={} status={} userId={} ip={}",
                deriveAction(method, path), path, method, status, userId, ip);
    }

    private String deriveAction(String method, String path) {
        if (path.contains("/auth/login")) return "LOGIN";
        if (path.contains("/auth/logout")) return "LOGOUT";
        if (path.contains("/auth/refresh")) return "TOKEN_REFRESH";
        if (path.contains("/commands")) return "COMMAND_ISSUE";
        if (path.contains("/users")) return "USER_MANAGEMENT";
        if (path.contains("/settings")) return "SETTINGS_UPDATE";
        return method + "_" + path.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
    }

    private UUID getCurrentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
