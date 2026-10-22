package com.yantrago.api.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SEC-03: Regression tests for WebSocketSessionRegistry.
 *
 * Covers:
 * - Register/unregister sessions
 * - closeSessionsForUser closes all of a user's active sessions
 * - closeSessionsForUser with no active sessions is a no-op
 * - closeSessionsForUser with null userId is a no-op
 * - Session count tracking
 */
class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    private WebSocketSession mockSession(UUID userId, String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) {
            attrs.put("userId", userId);
        }
        when(session.getAttributes()).thenReturn(attrs);
        return session;
    }

    @Test
    @DisplayName("register adds session to registry")
    void register_addsSession() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mockSession(userId, "sess-1");

        registry.register(userId, session);

        assertEquals(1, registry.sessionCountForUser(userId));
    }

    @Test
    @DisplayName("register multiple sessions for same user")
    void register_multipleSessions() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession s1 = mockSession(userId, "sess-1");
        WebSocketSession s2 = mockSession(userId, "sess-2");

        registry.register(userId, s1);
        registry.register(userId, s2);

        assertEquals(2, registry.sessionCountForUser(userId));
    }

    @Test
    @DisplayName("unregister removes session from registry")
    void unregister_removesSession() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mockSession(userId, "sess-1");

        registry.register(userId, session);
        assertEquals(1, registry.sessionCountForUser(userId));

        registry.unregister(userId, session);
        assertEquals(0, registry.sessionCountForUser(userId));
    }

    @Test
    @DisplayName("SEC-03: closeSessionsForUser closes all active sessions for the user")
    void closeSessionsForUser_closesAll() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession s1 = mockSession(userId, "sess-1");
        WebSocketSession s2 = mockSession(userId, "sess-2");

        registry.register(userId, s1);
        registry.register(userId, s2);

        int closed = registry.closeSessionsForUser(userId);

        assertEquals(2, closed);
        verify(s1).close(WebSocketSessionRegistry.SESSION_REVOKED);
        verify(s2).close(WebSocketSessionRegistry.SESSION_REVOKED);
        assertEquals(0, registry.sessionCountForUser(userId));
    }

    @Test
    @DisplayName("SEC-03: closeSessionsForUser returns 0 when no active sessions")
    void closeSessionsForUser_noSessions() throws Exception {
        UUID userId = UUID.randomUUID();
        int closed = registry.closeSessionsForUser(userId);
        assertEquals(0, closed);
    }

    @Test
    @DisplayName("SEC-03: closeSessionsForUser with null userId returns 0")
    void closeSessionsForUser_nullUser() throws Exception {
        int closed = registry.closeSessionsForUser(null);
        assertEquals(0, closed);
    }

    @Test
    @DisplayName("SEC-03: closeSessionsForUser only closes the specified user's sessions")
    void closeSessionsForUser_onlyClosesTargetUser() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        WebSocketSession s1 = mockSession(user1, "sess-1");
        WebSocketSession s2 = mockSession(user2, "sess-2");

        registry.register(user1, s1);
        registry.register(user2, s2);

        int closed = registry.closeSessionsForUser(user1);

        assertEquals(1, closed);
        verify(s1).close(WebSocketSessionRegistry.SESSION_REVOKED);
        verify(s2, never()).close(any(CloseStatus.class));
        assertEquals(1, registry.sessionCountForUser(user2));
    }

    @Test
    @DisplayName("SEC-03: closeSessionsForUser handles already-closed sessions gracefully")
    void closeSessionsForUser_handlesClosedSession() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mockSession(userId, "sess-1");
        // Session is already closed
        when(session.isOpen()).thenReturn(false);

        registry.register(userId, session);

        int closed = registry.closeSessionsForUser(userId);

        assertEquals(0, closed, "Already-closed sessions should not be counted");
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("SEC-03: closeSessionsForUser handles close exception gracefully")
    void closeSessionsForUser_handlesCloseException() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mockSession(userId, "sess-1");
        doThrow(new RuntimeException("Connection reset")).when(session)
                .close(any(CloseStatus.class));

        registry.register(userId, session);

        int closed = registry.closeSessionsForUser(userId);

        assertEquals(1, closed, "Session should be counted even if close throws");
    }

    // --- SEC-03: Token expiry scheduled cleanup ---

    private WebSocketSession mockSessionWithExpiry(UUID userId, String sessionId, Date expiry) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) {
            attrs.put("userId", userId);
        }
        if (expiry != null) {
            attrs.put("tokenExpiration", expiry);
        }
        when(session.getAttributes()).thenReturn(attrs);
        return session;
    }

    @Test
    @DisplayName("SEC-03: closeExpiredSessions closes sessions with expired tokens")
    void closeExpiredSessions_closesExpired() throws Exception {
        UUID userId = UUID.randomUUID();
        Date expired = new Date(System.currentTimeMillis() - 60_000);
        WebSocketSession session = mockSessionWithExpiry(userId, "sess-1", expired);

        registry.register(userId, session);
        registry.closeExpiredSessions();

        verify(session).close(WebSocketSessionRegistry.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("SEC-03: closeExpiredSessions does not close sessions with valid tokens")
    void closeExpiredSessions_keepsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        Date valid = new Date(System.currentTimeMillis() + 600_000);
        WebSocketSession session = mockSessionWithExpiry(userId, "sess-1", valid);

        registry.register(userId, session);
        registry.closeExpiredSessions();

        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("SEC-03: closeExpiredSessions handles sessions without token expiration gracefully")
    void closeExpiredSessions_noExpiration_keepsSession() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mockSession(userId, "sess-1"); // no tokenExpiration in attrs

        registry.register(userId, session);
        registry.closeExpiredSessions();

        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("SEC-03: closeExpiredSessions is a no-op when registry is empty")
    void closeExpiredSessions_emptyRegistry_noOp() {
        // Should not throw
        registry.closeExpiredSessions();
    }
}
