package com.yantrago.api.security;

import com.yantrago.api.service.AuditLogService;
import com.yantrago.api.service.OwnerContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.UUID;

/**
 * Logs sensitive actions to the audit_logs table via AuditLogService.
 *
 * SEC-14: persists audit records for POST/PUT/DELETE on sensitive paths,
 * including denied/failed outcomes. Captures actor, tenant, IP, and outcome.
 * Never stores request bodies, passwords, tokens, or secrets.
 *
 * Context (userId, orgId) is captured in preHandle before TenantContextFilter
 * clears it in its finally block. afterCompletion uses the stored attributes.
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

    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final OwnerContextService ownerContextService;
    private final PermissionEvaluator permissionEvaluator;
    private final TrustedProxyResolver trustedProxyResolver;

    public AuditLogInterceptor(ObjectProvider<AuditLogService> auditLogServiceProvider,
                               OwnerContextService ownerContextService,
                               PermissionEvaluator permissionEvaluator,
                               TrustedProxyResolver trustedProxyResolver) {
        this.auditLogServiceProvider = auditLogServiceProvider;
        this.ownerContextService = ownerContextService;
        this.permissionEvaluator = permissionEvaluator;
        this.trustedProxyResolver = trustedProxyResolver;
    }

    /**
     * Returns true if the request is on a sensitive path with a mutating method.
     * Shared with SecurityConfig so filter-layer denials (401/403 before the
     * request reaches the interceptor) can be audited with the same criteria.
     */
    public static boolean isSensitiveRequest(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        return SENSITIVE_METHODS.contains(method)
                && SENSITIVE_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (!isSensitiveRequest(request.getMethod(), request.getRequestURI())) {
            return true;
        }
        // Capture user/org context before TenantContextFilter clears it in finally.
        // Stored as request attributes for afterCompletion.
        UUID userId = permissionEvaluator.getCurrentUserId();
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        request.setAttribute("audit.userId", userId);
        request.setAttribute("audit.organizationId", orgId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (!isSensitiveRequest(method, path)) {
            return;
        }

        int status = response.getStatus();
        UUID userId = (UUID) request.getAttribute("audit.userId");
        UUID orgId = (UUID) request.getAttribute("audit.organizationId");
        String ip = trustedProxyResolver.resolveClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String correlationId = request.getHeader("X-Request-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        // Return the correlation ID so clients can reference it in support requests.
        response.setHeader("X-Request-ID", correlationId);

        String action = deriveAction(method, path);
        String outcome = deriveOutcome(status, ex);
        String resourceType = deriveResourceType(path);

        // Build details JSON — never store request body, passwords, or tokens
        StringBuilder details = new StringBuilder("{");
        details.append("\"method\":\"").append(escapeJson(method)).append("\"");
        details.append(",\"path\":\"").append(escapeJson(path)).append("\"");
        details.append(",\"status\":").append(status);
        details.append(",\"outcome\":\"").append(outcome).append("\"");
        details.append(",\"correlationId\":\"").append(escapeJson(correlationId)).append("\"");
        if (ex != null) {
            details.append(",\"exception\":\"").append(escapeJson(ex.getClass().getSimpleName())).append("\"");
        }
        details.append("}");

        AuditLogService svc = auditLogServiceProvider.getIfAvailable();
        if (svc != null) {
            try {
                svc.recordAuditLog(action, resourceType, null, orgId, userId,
                        ip, truncate(userAgent, 512), details.toString());
            } catch (Exception e) {
                // SEC-14: audit persistence failure must not break the request,
                // but must be logged so operators can investigate.
                log.error("Failed to persist audit log: action={} path={} userId={} orgId={}",
                        action, path, userId, orgId, e);
            }
        }

        log.info("AUDIT action={} path={} method={} status={} outcome={} userId={} ip={}",
                action, path, method, status, outcome, userId, ip);
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

    private String deriveOutcome(int status, Exception ex) {
        if (ex != null) return "ERROR";
        if (status == 401 || status == 403) return "DENIED";
        if (status >= 400) return "FAILURE";
        return "SUCCESS";
    }

    private String deriveResourceType(String path) {
        if (path.contains("/commands")) return "COMMAND";
        if (path.contains("/users")) return "USER";
        if (path.contains("/settings")) return "SETTING";
        if (path.contains("/auth")) return "AUTH";
        return "API";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
