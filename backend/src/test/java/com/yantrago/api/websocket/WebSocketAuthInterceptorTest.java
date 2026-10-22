package com.yantrago.api.websocket;

import com.yantrago.api.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SEC-09: Regression tests for WebSocketAuthInterceptor.
 *
 * Covers:
 * - Authorization header (Bearer token) accepted
 * - URL query parameter token rejected (SEC-09)
 * - No token rejected
 * - Invalid token rejected
 * - Session attributes populated correctly (userId, organizationId, isSuperAdmin, tokenExpiration)
 * - Super admin role detection
 */
class WebSocketAuthInterceptorTest {

    private JwtService jwtService;
    private WebSocketAuthInterceptor interceptor;
    private WebSocketHandler wsHandler;
    private ServerHttpResponse response;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final String TOKEN = "valid.jwt.token";
    private static final Date EXPIRY = new Date(System.currentTimeMillis() + 600_000);

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        interceptor = new WebSocketAuthInterceptor(jwtService);
        wsHandler = mock(WebSocketHandler.class);
        response = mock(ServerHttpResponse.class);
    }

    private ServletServerHttpRequest mockRequest(String authHeader, String queryToken) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getParameter("token")).thenReturn(queryToken);
        when(servletRequest.getHeader("Authorization")).thenReturn(authHeader);

        ServletServerHttpRequest serverRequest = mock(ServletServerHttpRequest.class);
        when(serverRequest.getServletRequest()).thenReturn(servletRequest);
        return serverRequest;
    }

    private void stubValidToken() {
        when(jwtService.validateAccessToken(TOKEN)).thenReturn(true);
        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(EXPIRY);
        when(jwtService.parseAccessToken(TOKEN)).thenReturn(claims);
        when(jwtService.extractUserId(claims)).thenReturn(USER_ID);
        when(jwtService.extractOrganizationId(claims)).thenReturn(ORG_ID);
        when(jwtService.extractRoles(claims)).thenReturn("customer");
        when(jwtService.extractPermissions(claims)).thenReturn("read");
    }

    @Test
    @DisplayName("SEC-09: Authorization header (Bearer token) accepted")
    void handshake_withBearerHeader_accepted() {
        stubValidToken();
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                mockRequest("Bearer " + TOKEN, null),
                response, wsHandler, attrs);

        assertTrue(result);
        assertEquals(USER_ID, attrs.get("userId"));
        assertEquals(ORG_ID, attrs.get("organizationId"));
        assertEquals("customer", attrs.get("roles"));
        assertEquals(false, attrs.get("isSuperAdmin"));
        assertEquals(EXPIRY, attrs.get("tokenExpiration"));
    }

    @Test
    @DisplayName("SEC-09: URL query parameter token rejected")
    void handshake_withUrlToken_rejected() {
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                mockRequest("Bearer " + TOKEN, TOKEN),
                response, wsHandler, attrs);

        assertFalse(result, "URL query parameter tokens must be rejected (SEC-09)");
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("SEC-09: No token rejected")
    void handshake_noToken_rejected() {
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                mockRequest(null, null),
                response, wsHandler, attrs);

        assertFalse(result);
    }

    @Test
    @DisplayName("SEC-09: Invalid token rejected")
    void handshake_invalidToken_rejected() {
        when(jwtService.validateAccessToken(TOKEN)).thenReturn(false);
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                mockRequest("Bearer " + TOKEN, null),
                response, wsHandler, attrs);

        assertFalse(result);
    }

    @Test
    @DisplayName("SEC-09: super_admin role detected correctly")
    void handshake_superAdmin_isSuperAdminTrue() {
        stubValidToken();
        Claims claims = jwtService.parseAccessToken(TOKEN);
        when(jwtService.extractRoles(claims)).thenReturn("super_admin");

        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                mockRequest("Bearer " + TOKEN, null),
                response, wsHandler, attrs);

        assertTrue(result);
        assertEquals(true, attrs.get("isSuperAdmin"));
    }

    @Test
    @DisplayName("SEC-09: super_admin among multiple roles detected")
    void handshake_superAdminAmongRoles_isSuperAdminTrue() {
        stubValidToken();
        Claims claims = jwtService.parseAccessToken(TOKEN);
        when(jwtService.extractRoles(claims)).thenReturn("admin,super_admin,customer");

        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                mockRequest("Bearer " + TOKEN, null),
                response, wsHandler, attrs);

        assertTrue(result);
        assertEquals(true, attrs.get("isSuperAdmin"));
    }

    @Test
    @DisplayName("SEC-09: Non-servlet request rejected")
    void handshake_nonServletRequest_rejected() {
        ServerHttpRequest nonServlet = mock(ServerHttpRequest.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                nonServlet, response, wsHandler, attrs);

        assertFalse(result);
    }

    @Test
    @DisplayName("SEC-09: Token parse failure rejected")
    void handshake_tokenParseFailure_rejected() {
        when(jwtService.validateAccessToken(TOKEN)).thenReturn(true);
        when(jwtService.parseAccessToken(TOKEN)).thenThrow(new RuntimeException("parse error"));

        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                mockRequest("Bearer " + TOKEN, null),
                response, wsHandler, attrs);

        assertFalse(result);
    }
}
