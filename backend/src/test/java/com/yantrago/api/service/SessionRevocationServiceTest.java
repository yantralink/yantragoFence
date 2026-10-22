package com.yantrago.api.service;

import com.yantrago.api.repository.RefreshTokenRepository;
import com.yantrago.api.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SEC-04 / SEC-05: SessionRevocationService delegates to RefreshTokenRepository
 * with REQUIRES_NEW propagation so revocation commits independently of the
 * caller's transaction. These tests verify the delegation and that null
 * userId is handled safely.
 *
 * SEC-03: Also verifies that WebSocket sessions are closed on revocation.
 */
class SessionRevocationServiceTest {

    private RefreshTokenRepository refreshTokenRepository;
    private WebSocketSessionRegistry webSocketSessionRegistry;
    private SessionRevocationService service;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
        service = new SessionRevocationService(refreshTokenRepository, webSocketSessionRegistry);
    }

    @Test
    @DisplayName("revokeAllSessions delegates to revokeAllByUserId")
    void revokeAllSessions_delegates() {
        UUID userId = UUID.randomUUID();
        service.revokeAllSessions(userId);
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("revokeAllSessions with null userId is a no-op")
    void revokeAllSessions_nullUser_noOp() {
        service.revokeAllSessions(null);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("SEC-03: revokeAllSessions closes active WebSocket sessions for the user")
    void revokeAllSessions_closesWebSocketSessions() {
        UUID userId = UUID.randomUUID();
        service.revokeAllSessions(userId);
        verify(webSocketSessionRegistry).closeSessionsForUser(userId);
    }

    @Test
    @DisplayName("SEC-05: revokeFamilyOnReplay revokes all sessions for the user")
    void revokeFamilyOnReplay_revokesAll() {
        UUID userId = UUID.randomUUID();
        UUID replayedTokenId = UUID.randomUUID();
        service.revokeFamilyOnReplay(userId, replayedTokenId);
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("SEC-03: revokeFamilyOnReplay closes active WebSocket sessions for the user")
    void revokeFamilyOnReplay_closesWebSocketSessions() {
        UUID userId = UUID.randomUUID();
        UUID replayedTokenId = UUID.randomUUID();
        service.revokeFamilyOnReplay(userId, replayedTokenId);
        verify(webSocketSessionRegistry).closeSessionsForUser(userId);
    }
}
