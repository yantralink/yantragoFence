package com.yantrago.api.service;

import com.yantrago.api.repository.RefreshTokenRepository;
import com.yantrago.api.websocket.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SEC-04 / SEC-05: Session revocation service.
 *
 * All methods run in a NEW transaction so that revocation commits
 * independently of the caller's transaction. This is critical for
 * SEC-05: when a replayed refresh token is detected, the refresh
 * method throws and its own transaction rolls back — but the family
 * revocation performed here must still commit, otherwise the replay
 * revocation is undone and the stolen token remains usable.
 *
 * Also used by SEC-04: password change, deactivation, locking, and
 * tenant/role changes call {@link #revokeAllSessions} to invalidate
 * all outstanding refresh tokens for the affected user. Access tokens
 * are short-lived (default 15 min) and stateless, so the exposure
 * window after revocation is bounded by the access-token TTL.
 *
 * SEC-03: In addition to revoking refresh tokens, this service also
 * closes all of the user's active WebSocket connections via
 * {@link WebSocketSessionRegistry}, so revocation takes effect on
 * existing subscriptions immediately rather than only rejecting new
 * subscription attempts.
 */
@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public SessionRevocationService(RefreshTokenRepository refreshTokenRepository,
                                    WebSocketSessionRegistry webSocketSessionRegistry) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    /**
     * Revokes every outstanding (non-expired, non-revoked) refresh token
     * for the given user. Called on password change, deactivation,
     * locking, and tenant/role changes (SEC-04), and on explicit
     * "logout all" requests.
     *
     * SEC-03: Also closes all active WebSocket connections for the user
     * so that existing subscriptions are terminated immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessions(UUID userId) {
        if (userId == null) {
            return;
        }
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
        int closedSockets = webSocketSessionRegistry.closeSessionsForUser(userId);
        log.info("Revoked all refresh tokens for user={} (closed {} WebSocket session(s))",
                userId, closedSockets);
    }

    /**
     * SEC-05: Revokes the entire refresh-token family for a user when a
     * replayed (already-revoked) token is presented — a strong signal of
     * token theft. Runs in a new transaction so the revocation commits
     * even though the calling refresh() will throw and roll back.
     *
     * SEC-03: Also closes all active WebSocket connections for the user.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamilyOnReplay(UUID userId, UUID replayedTokenId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
        int closedSockets = webSocketSessionRegistry.closeSessionsForUser(userId);
        log.warn("SEC-05: Refresh token replay detected — revoked all sessions for user={}, " +
                        "replayedTokenId={} (closed {} WebSocket session(s))",
                userId, replayedTokenId, closedSockets);
    }
}
