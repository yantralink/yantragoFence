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
 * Validates JWT tokens passed as query parameters during the WebSocket handshake.
 * Clients connect with: ws://host/ws?token={jwt_access_token}
 *
 * On successful authentication, the userId and organizationId are stored in
 * the session attributes for use by downstream handlers and security checks.
 *
 * Per AGENTS.md rule 9: all sensitive operations require authorization.
 * Per AGENTS.md rule 12: security checks required for production features.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String token = httpRequest.getParameter("token");

            if (token == null || token.isBlank()) {
                log.warn("WebSocket handshake rejected: no token provided from {}",
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

                attributes.put("userId", userId);
                attributes.put("organizationId", organizationId);
                attributes.put("roles", roles);

                log.info("WebSocket handshake authenticated: userId={} orgId={}", userId, organizationId);
                return true;
            } catch (Exception e) {
                log.warn("WebSocket handshake rejected: token parse failed: {}", e.getMessage());
                return false;
            }
        }

        log.warn("WebSocket handshake rejected: not a servlet request");
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
