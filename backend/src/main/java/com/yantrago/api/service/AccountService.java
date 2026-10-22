package com.yantrago.api.service;

import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TrustedProxyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * PLAY-06: self-service account operations.
 *
 * Currently: account deletion requests. Accounts are admin-provisioned
 * (no self-signup), so erasure is performed by an org/super admin — this
 * service records the request durably in audit_logs (visible on the
 * super-admin audit page) and optionally emails the configured privacy
 * contact so requests are operationally actionable, not just logged.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final PermissionEvaluator permissionEvaluator;
    private final OwnerContextService ownerContextService;
    private final TrustedProxyResolver trustedProxyResolver;
    private final String privacyNotifyEmail;

    public AccountService(AuditLogService auditLogService,
                          EmailService emailService,
                          PermissionEvaluator permissionEvaluator,
                          OwnerContextService ownerContextService,
                          TrustedProxyResolver trustedProxyResolver,
                          @Value("${privacy.notify-email:}") String privacyNotifyEmail) {
        this.auditLogService = auditLogService;
        this.emailService = emailService;
        this.permissionEvaluator = permissionEvaluator;
        this.ownerContextService = ownerContextService;
        this.trustedProxyResolver = trustedProxyResolver;
        this.privacyNotifyEmail = privacyNotifyEmail;
    }

    /**
     * Records an account-deletion request for the authenticated caller.
     * Never fails the request because notification delivery failed — the
     * audit record is the authoritative trail.
     */
    public void recordDeletionRequest(HttpServletRequest request) {
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            // Endpoint is authenticated, but never audit a request whose
            // subject cannot be established.
            throw new IllegalStateException("Authenticated principal has no user id");
        }
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        String ip = trustedProxyResolver.resolveClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > USER_AGENT_MAX_LENGTH) {
            userAgent = userAgent.substring(0, USER_AGENT_MAX_LENGTH);
        }

        // Only identifiers — never email/phone/name in the audit detail.
        String details = "{\"outcome\":\"REQUESTED\""
                + ",\"note\":\"user-initiated deletion request\"}";
        auditLogService.recordAuditLog("ACCOUNT_DELETION_REQUEST", "USER", userId,
                orgId, userId, ip, userAgent, details);

        log.info("PLAY-06: account deletion request recorded for user={} org={}", userId, orgId);

        if (privacyNotifyEmail != null && !privacyNotifyEmail.isBlank()) {
            emailService.sendEmail(privacyNotifyEmail,
                    "YantraGO account deletion request",
                    "An account deletion request was recorded.\n\n"
                            + "userId: " + userId + "\n"
                            + "organizationId: " + orgId + "\n\n"
                            + "Process: admin console → customers → delete "
                            + "(performs full PII erasure).");
        }
    }
}
