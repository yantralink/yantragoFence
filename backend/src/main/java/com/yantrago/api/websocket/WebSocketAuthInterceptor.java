package com.yantrago.api.websocket;

import com.yantrago.api.service.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

/**
 * JWT authentication interceptor for WebSocket connections.
 *
 * SEC-09: Authentication is performed via the Authorization header
 * (Bearer token), NOT via URL query parameters. URL tokens leak through
 * proxy logs, server access logs, and browser history. The query-parameter
 * transport has been removed.
 *
 * Native clients (Flutter/mobile) send the Authorization header in the
 * WebSocket upgrade request. SockJS browser clients that cannot set
 * headers on the upgrade request must use the STOMP CONNECT frame
 * (handled by StompChannelInterceptor's CONNECT validation).
 *
 * On successful authentication, the userId, organizationId, roles,
 * permissions, and isSuperAdmin flag are stored in the session attributes
 * for use by downstream handlers and security checks.
 *
 * Per AGENTS.md rule 9: all sensitive operations require authorization.
 * Per AGENTS.md rule 12: security checks required for production features.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("WebSocket handshake rejected: not a servlet request");
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        // SEC-09: reject URL query-parameter tokens. They leak through logs.
        String queryToken = httpRequest.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            log.warn("SEC-09: WebSocket handshake rejected: token passed as URL " +
                    "query parameter from {} — use Authorization header instead",
                    httpRequest.getRemoteAddr());
            return false;
        }

        // SEC-09: primary transport is the Authorization header.
        String token = extractBearerToken(httpRequest);

        if (token == null) {
            log.warn("WebSocket handshake rejected: no Authorization header from {}",
                    httpRequest.getRemoteAddr());
            return false;
        }

        if (!jwtService.validateAccessToken(token)) {
            log.warn("WebSocket handshake rejected: invalid token from {}",
                    httpRequest.getRemoteAddr());
            return false;
        }

        try {
            Claims claims = jwtService.parseAccessToken(token);
            UUID userId = jwtService.extractUserId(claims);
            UUID organizationId = jwtService.extractOrganizationId(claims);
            String roles = jwtService.extractRoles(claims);
            String permissions = jwtService.extractPermissions(claims);

            attributes.put("userId", userId);
            attributes.put("organizationId", organizationId);
            attributes.put("roles", roles);
            attributes.put("permissions", permissions);
            attributes.put("isSuperAdmin", isSuperAdmin(roles));
            // SEC-09: store token expiration for server-side expiry enforcement
            // on active WebSocket connections (checked by StompChannelInterceptor).
            attributes.put("tokenExpiration", claims.getExpiration());

            log.info("WebSocket handshake authenticated: userId={} orgId={}",
                    userId, organizationId);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: token parse failed: {}", e.getMessage());
            return false;
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private boolean isSuperAdmin(String roles) {
        if (roles == null || roles.isBlank()) {
            return false;
        }
        // Roles are stored as a comma-separated string in the JWT.
        for (String role : roles.split(",")) {
            if ("super_admin".equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No post-handshake action needed
    }
}
