package com.yantrago.api.security;

import com.yantrago.api.service.AuditLogService;
import com.yantrago.api.service.OwnerContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-14: verifies AuditLogInterceptor persists audit records for
 * sensitive paths with correct outcome values, captures context in
 * preHandle, and returns the correlation ID to the client.
 */
class AuditLogInterceptorTest {

    private AuditLogService auditLogService;
    private OwnerContextService ownerContextService;
    private PermissionEvaluator permissionEvaluator;
    private TrustedProxyResolver trustedProxyResolver;
    private AuditLogInterceptor interceptor;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        ownerContextService = mock(OwnerContextService.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        trustedProxyResolver = mock(TrustedProxyResolver.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<AuditLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(auditLogService);

        interceptor = new AuditLogInterceptor(provider, ownerContextService,
                permissionEvaluator, trustedProxyResolver);
    }

    private HttpServletRequest mockRequest(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    @Test
    void preHandle_capturesUserAndOrgContext_forSensitiveRequests() {
        HttpServletRequest request = mockRequest("POST", "/api/v1/auth/login");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgId);

        interceptor.preHandle(request, response, new Object());

        verify(request).setAttribute("audit.userId", userId);
        verify(request).setAttribute("audit.organizationId", orgId);
    }

    @Test
    void preHandle_skipsContextCapture_forNonSensitiveRequests() {
        HttpServletRequest request = mockRequest("GET", "/api/v1/machines");
        HttpServletResponse response = mock(HttpServletResponse.class);

        interceptor.preHandle(request, response, new Object());

        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void afterCompletion_recordsSuccess_forOkResponse() {
        HttpServletRequest request = mockRequest("POST", "/api/v1/auth/login");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(trustedProxyResolver.resolveClientIp(request)).thenReturn("10.0.0.1");

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordAuditLog(eq("LOGIN"), eq("AUTH"), isNull(),
                isNull(), isNull(), eq("10.0.0.1"), any(), detailsCaptor.capture());
        assertTrue(detailsCaptor.getValue().contains("\"outcome\":\"SUCCESS\""));
        assertTrue(detailsCaptor.getValue().contains("\"correlationId\":\"req-123\""));
    }

    @Test
    void afterCompletion_recordsDenied_forForbiddenResponse() {
        HttpServletRequest request = mockRequest("DELETE", "/api/v1/users/123");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(403);
        when(trustedProxyResolver.resolveClientIp(request)).thenReturn("10.0.0.1");

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordAuditLog(eq("USER_MANAGEMENT"), eq("USER"), isNull(),
                isNull(), isNull(), eq("10.0.0.1"), any(), detailsCaptor.capture());
        assertTrue(detailsCaptor.getValue().contains("\"outcome\":\"DENIED\""));
    }

    @Test
    void afterCompletion_recordsFailure_forServerError() {
        HttpServletRequest request = mockRequest("POST", "/api/v1/commands");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(500);
        when(trustedProxyResolver.resolveClientIp(request)).thenReturn("10.0.0.1");

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordAuditLog(eq("COMMAND_ISSUE"), eq("COMMAND"), isNull(),
                isNull(), isNull(), eq("10.0.0.1"), any(), detailsCaptor.capture());
        assertTrue(detailsCaptor.getValue().contains("\"outcome\":\"FAILURE\""));
    }

    @Test
    void afterCompletion_recordsError_forException() {
        HttpServletRequest request = mockRequest("POST", "/api/v1/commands");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(trustedProxyResolver.resolveClientIp(request)).thenReturn("10.0.0.1");

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordAuditLog(anyString(), anyString(), isNull(),
                isNull(), isNull(), anyString(), any(), detailsCaptor.capture());
        assertTrue(detailsCaptor.getValue().contains("\"outcome\":\"ERROR\""));
        assertTrue(detailsCaptor.getValue().contains("\"exception\":\"RuntimeException\""));
    }

    @Test
    void afterCompletion_skipsNonSensitivePath() {
        HttpServletRequest request = mockRequest("POST", "/api/v1/machines");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditLogService, never()).recordAuditLog(anyString(), anyString(), any(),
                any(), any(), anyString(), any(), anyString());
    }

    @Test
    void afterCompletion_skipsGetRequests() {
        HttpServletRequest request = mockRequest("GET", "/api/v1/users");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditLogService, never()).recordAuditLog(anyString(), anyString(), any(),
                any(), any(), anyString(), any(), anyString());
    }

    @Test
    void afterCompletion_setsRequestIdHeaderOnResponse() {
        HttpServletRequest request = mockRequest("POST", "/api/v1/auth/login");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(trustedProxyResolver.resolveClientIp(request)).thenReturn("10.0.0.1");

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(response).setHeader(eq("X-Request-ID"), anyString());
    }

    @Test
    void afterCompletion_survivesAuditServiceFailure() {
        HttpServletRequest request = mockRequest("POST", "/api/v1/auth/login");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(trustedProxyResolver.resolveClientIp(request)).thenReturn("10.0.0.1");
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(auditLogService).recordAuditLog(anyString(), anyString(), any(),
                        any(), any(), anyString(), any(), anyString());

        // Must not propagate — the request completes normally.
        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    void isSensitiveRequest_matchesExpectedPaths() {
        assertTrue(AuditLogInterceptor.isSensitiveRequest("POST", "/api/v1/auth/login"));
        assertTrue(AuditLogInterceptor.isSensitiveRequest("DELETE", "/api/v1/users/abc"));
        assertTrue(AuditLogInterceptor.isSensitiveRequest("PUT", "/api/v1/commands/xyz"));
        assertFalse(AuditLogInterceptor.isSensitiveRequest("GET", "/api/v1/auth/login"));
        assertFalse(AuditLogInterceptor.isSensitiveRequest("POST", "/api/v1/machines"));
        assertFalse(AuditLogInterceptor.isSensitiveRequest(null, "/api/v1/auth/login"));
        assertFalse(AuditLogInterceptor.isSensitiveRequest("POST", null));
    }
}
