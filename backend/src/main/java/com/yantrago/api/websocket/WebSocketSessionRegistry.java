package com.yantrago.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active WebSocket sessions keyed by authenticated userId.
 *
 * SEC-03 / SEC-09: When a user's sessions are revoked (Phase D — password
 * change, deactivation, locking, deletion, logout-all, or refresh-token
 * replay), the registry's {@link #closeSessionsForUser(UUID)} method closes
 * all of that user's active WebSocket connections immediately. This ensures
 * that revocation takes effect on existing subscriptions, not only on new
 * subscription attempts.
 *
 * SEC-03: A scheduled task ({@link #closeExpiredSessions}) periodically closes
 * WebSocket connections whose access tokens have expired, so that token
 * expiry also closes existing subscriptions — not only rejecting new frames.
 *
 * Thread-safe: uses ConcurrentHashMap for the userId → sessions map and
 * ConcurrentHashMap.newKeySet() for the per-user session sets, since
 * WebSocket connect/disconnect events can fire concurrently from multiple
 * worker threads.
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    /**
     * Close status code 4000-4999 are application-defined (RFC 6455).
     * 4401 = "authentication revoked" (custom).
     */
    public static final CloseStatus SESSION_REVOKED = new CloseStatus(4401, "Session revoked");

    /**
     * Close status for expired access tokens (custom).
     */
    public static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4402, "Token expired");

    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> sessionsByUser =
            new ConcurrentHashMap<>();

    /**
     * Registers a WebSocket session for the given user. Called when a
     * WebSocket connection is established (after the handshake interceptor
     * has authenticated the user and stored userId in the session attributes).
     */
    public void register(UUID userId, WebSocketSession session) {
        sessionsByUser
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
        log.debug("WebSocket session registered: user={} sessionId={}",
                userId, session.getId());
    }

    /**
     * Unregisters a WebSocket session. Called when the connection closes
     * (either naturally or via {@link #closeSessionsForUser}).
     */
    public void unregister(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId, sessions);
            }
        }
        log.debug("WebSocket session unregistered: user={} sessionId={}",
                userId, session.getId());
    }

    /**
     * Closes all active WebSocket sessions for the given user. Called by
     * {@link com.yantrago.api.service.SessionRevocationService} when a user's
     * refresh tokens are revoked (password change, deactivation, locking,
     * deletion, logout-all, or refresh-token replay).
     *
     * @return the number of sessions closed
     */
    public int closeSessionsForUser(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Set<WebSocketSession> sessions = sessionsByUser.remove(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                closed++;
                try {
                    session.close(SESSION_REVOKED);
                } catch (Exception e) {
                    log.warn("Failed to close WebSocket session for user={}: {}",
                            userId, e.getMessage());
                }
            }
        }
        log.info("Closed {} WebSocket session(s) for revoked user={}", closed, userId);
        return closed;
    }

    /**
     * SEC-03: Periodically closes WebSocket connections whose access tokens
     * have expired. Runs every 60 seconds. This ensures that token expiry
     * closes existing subscriptions, not only rejects new frames — as
     * required by SEC-03.
     *
     * The access token expiration is stored in the session attributes by
     * {@link WebSocketAuthInterceptor} at handshake time.
     */
    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredSessions() {
        if (sessionsByUser.isEmpty()) {
            return;
        }
        int closed = 0;
        for (Map.Entry<UUID, Set<WebSocketSession>> entry : sessionsByUser.entrySet()) {
            UUID userId = entry.getKey();
            Set<WebSocketSession> sessions = entry.getValue();
            for (WebSocketSession session : sessions) {
                if (session.isOpen() && isTokenExpired(session)) {
                    closed++;
                    try {
                        session.close(TOKEN_EXPIRED);
                    } catch (Exception e) {
                        log.warn("Failed to close expired WebSocket session for user={}: {}",
                                userId, e.getMessage());
                    }
                }
            }
        }
        if (closed > 0) {
            log.info("SEC-03: Closed {} WebSocket session(s) with expired access tokens", closed);
        }
    }

    private boolean isTokenExpired(WebSocketSession session) {
        Object expiration = session.getAttributes().get("tokenExpiration");
        if (expiration instanceof Date expiry) {
            return expiry.before(new Date());
        }
        return false;
    }

    /**
     * Returns the number of active sessions for a user (for testing/diagnostics).
     */
    public int sessionCountForUser(UUID userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }
}
