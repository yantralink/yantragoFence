package com.yantrago.api.service;

import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TrustedProxyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PLAY-06 unit tests for AccountService deletion-request recording.
 *
 * Verifies: the request is audited under the authenticated user's id with
 * trusted IP, the privacy mailbox is notified only when configured, and a
 * missing principal fails instead of auditing an unidentifiable request.
 */
class AccountServiceTest {

    private AuditLogService auditLogService;
    private EmailService emailService;
    private PermissionEvaluator permissionEvaluator;
    private OwnerContextService ownerContextService;
    private TrustedProxyResolver trustedProxyResolver;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        emailService = mock(EmailService.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        ownerContextService = mock(OwnerContextService.class);
        trustedProxyResolver = mock(TrustedProxyResolver.class);

        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgId);
        when(trustedProxyResolver.resolveClientIp(any())).thenReturn("203.0.113.10");
    }

    private AccountService service(String notifyEmail) {
        return new AccountService(auditLogService, emailService,
                permissionEvaluator, ownerContextService, trustedProxyResolver,
                notifyEmail);
    }

    @Test
    @DisplayName("Request is audited under the authenticated user with trusted IP")
    void record_auditsWithJwtIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "YantraGO/1.0 Android");

        service("privacy@yantrago.com").recordDeletionRequest(request);

        verify(auditLogService).recordAuditLog(
                eq("ACCOUNT_DELETION_REQUEST"), eq("USER"), eq(userId),
                eq(orgId), eq(userId), eq("203.0.113.10"),
                eq("YantraGO/1.0 Android"), anyString());
    }

    @Test
    @DisplayName("Configured privacy mailbox receives a notification")
    void record_notifiesMailbox() {
        service("privacy@yantrago.com")
                .recordDeletionRequest(new MockHttpServletRequest());

        verify(emailService).sendEmail(eq("privacy@yantrago.com"),
                anyString(), anyString());
    }

    @Test
    @DisplayName("Empty notify email still audits but sends no mail")
    void record_noMailbox_stillAudits() {
        service("").recordDeletionRequest(new MockHttpServletRequest());

        verify(auditLogService).recordAuditLog(anyString(), anyString(),
                eq(userId), eq(orgId), eq(userId), anyString(), isNull(),
                anyString());
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Null principal fails fast — nothing is audited")
    void record_nullPrincipal_throws() {
        when(permissionEvaluator.getCurrentUserId()).thenReturn(null);

        assertThatThrownBy(() -> service("")
                .recordDeletionRequest(new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class);

        verify(auditLogService, never()).recordAuditLog(
                anyString(), anyString(), any(), any(), any(),
                any(), any(), anyString());
    }
}
