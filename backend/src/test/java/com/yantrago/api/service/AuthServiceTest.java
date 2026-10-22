package com.yantrago.api.service;

import com.yantrago.api.dto.auth.LoginRequest;
import com.yantrago.api.dto.auth.RefreshTokenRequest;
import com.yantrago.api.dto.auth.TokenResponse;
import com.yantrago.api.model.Organization;
import com.yantrago.api.model.RefreshToken;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.OrganizationRepository;
import com.yantrago.api.repository.RefreshTokenRepository;
import com.yantrago.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression tests for Phase D — atomic token lifecycle & revocation.
 *
 * SEC-05: refresh rotation must be atomic (compare-and-set consume).
 *   - Replay of a revoked token triggers family revocation in a separate
 *     transaction (commits even though refresh throws and rolls back).
 *   - Concurrent refreshes: only one wins the consume; the other is denied.
 *
 * SEC-04: refresh validates current account/org state.
 *   - Deactivated user cannot refresh.
 *   - Locked user cannot refresh.
 *   - User whose org was deactivated cannot refresh.
 *   - New tokens use the user's CURRENT organization, not the stale token's.
 *   - logoutAll revokes all sessions for the user.
 *
 * Per AGENTS.md rule 12: tests for new logic.
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private OrganizationRepository organizationRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private JwtService jwtService;
    private SessionRevocationService sessionRevocationService;
    private JdbcTemplate jdbcTemplate;
    private AuthService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();
    private final String rawRefreshToken = "refresh-token-string";
    private final String tokenHash = "hash-of-refresh-token";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtService = mock(JwtService.class);
        sessionRevocationService = mock(SessionRevocationService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        authService = new AuthService(userRepository, organizationRepository,
                refreshTokenRepository, jwtService, sessionRevocationService, jdbcTemplate);

        // Default stubs for a successful refresh
        when(jwtService.validateRefreshToken(rawRefreshToken)).thenReturn(true);
        when(jwtService.hashToken(rawRefreshToken)).thenReturn(tokenHash);
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTtlSeconds()).thenReturn(2592000L);
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any())).thenReturn("new-access");
        when(jwtService.generateRefreshToken(any(), any(), any())).thenReturn("new-refresh");
    }

    private RefreshToken storedToken(UUID orgId, LocalDateTime expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setId(tokenId);
        t.setUserId(userId);
        t.setOrganizationId(orgId);
        t.setTokenHash(tokenHash);
        t.setExpiresAt(expiresAt);
        return t;
    }

    private User activeUser(UUID orgId) {
        User u = new User();
        u.setId(userId);
        u.setEmail("user@example.com");
        u.setOrganizationId(orgId);
        u.setIsActive(true);
        u.setIsLocked(false);
        return u;
    }

    // Note: jdbcTemplate.queryForList is NOT stubbed — Mockito returns an empty
    // list by default for List-returning methods, which is fine for refresh
    // (empty roles/permissions). This avoids varargs matcher ambiguity.

    // --- SEC-05: atomic consume ---

    @Test
    @DisplayName("SEC-05: successful refresh atomically consumes the old token")
    void refresh_success_consumesAtomically() {
        RefreshToken stored = storedToken(orgId, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfNotAlready(eq(tokenId), any())).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(orgId)));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse result = authService.refresh(refreshRequest());

        assertEquals("new-access", result.getAccessToken());
        assertEquals("new-refresh", result.getRefreshToken());
        // Atomic consume was called
        verify(refreshTokenRepository).revokeIfNotAlready(eq(tokenId), any());
        // Family revocation was NOT called (no replay)
        verify(sessionRevocationService, never()).revokeFamilyOnReplay(any(), any());
    }

    @Test
    @DisplayName("SEC-05: replay of revoked token triggers family revocation in a separate transaction")
    void refresh_replay_revokesFamilyInSeparateTransaction() {
        RefreshToken stored = storedToken(orgId, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        // Token was already revoked → consume returns 0
        when(refreshTokenRepository.revokeIfNotAlready(eq(tokenId), any())).thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.refresh(refreshRequest()));
        assertTrue(ex.getMessage().contains("revoked"));

        // SEC-05: family revocation must be called via SessionRevocationService
        // (REQUIRES_NEW) so it commits independently of the rolled-back refresh.
        verify(sessionRevocationService).revokeFamilyOnReplay(userId, tokenId);
    }

    @Test
    @DisplayName("SEC-05: expired token is rejected even if consume succeeds")
    void refresh_expired_throws() {
        RefreshToken stored = storedToken(orgId, LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfNotAlready(eq(tokenId), any())).thenReturn(1);

        assertThrows(IllegalArgumentException.class,
                () -> authService.refresh(refreshRequest()));
    }

    // --- SEC-04: account state validation on refresh ---

    @Test
    @DisplayName("SEC-04: deactivated user cannot refresh")
    void refresh_deactivatedUser_throws() {
        RefreshToken stored = storedToken(orgId, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfNotAlready(eq(tokenId), any())).thenReturn(1);
        User deactivated = activeUser(orgId);
        deactivated.setIsActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(deactivated));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.refresh(refreshRequest()));
        assertTrue(ex.getMessage().contains("deactivated"));
        // No new tokens issued
        verify(jwtService, never()).generateAccessToken(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SEC-04: locked user cannot refresh")
    void refresh_lockedUser_throws() {
        RefreshToken stored = storedToken(orgId, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfNotAlready(eq(tokenId), any())).thenReturn(1);
        User locked = activeUser(orgId);
        locked.setIsLocked(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(locked));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.refresh(refreshRequest()));
        assertTrue(ex.getMessage().contains("locked"));
    }

    @Test
    @DisplayName("SEC-04: user whose org was deactivated cannot refresh")
    void refresh_orgDeactivated_throws() {
        RefreshToken stored = storedToken(orgId, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfNotAlready(eq(tokenId), any())).thenReturn(1);
        User user = activeUser(orgId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Organization org = new Organization();
        org.setId(orgId);
        org.setIsActive(false);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.refresh(refreshRequest()));
        assertTrue(ex.getMessage().contains("Organization is deactivated"));
    }

    @Test
    @DisplayName("SEC-04: new tokens use the user's CURRENT org, not the stale token's org")
    void refresh_usesCurrentOrg() {
        UUID staleOrgId = UUID.randomUUID();
        UUID currentOrgId = UUID.randomUUID();
        RefreshToken stored = storedToken(staleOrgId, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfNotAlready(eq(tokenId), any())).thenReturn(1);
        // User has since been moved to a different org
        User user = activeUser(currentOrgId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.refresh(refreshRequest());

        // New access token must use currentOrgId, not staleOrgId
        verify(jwtService).generateAccessToken(eq(userId), any(), eq(currentOrgId), any(), any());
        verify(jwtService).generateRefreshToken(eq(userId), any(), eq(currentOrgId));
    }

    // --- SEC-04: logoutAll ---

    @Test
    @DisplayName("SEC-04: logoutAll revokes all sessions for the user")
    void logoutAll_revokesAllSessions() {
        authService.logoutAll(userId);
        verify(sessionRevocationService).revokeAllSessions(userId);
    }

    @Test
    @DisplayName("SEC-04: logoutAll with null userId delegates (SessionRevocationService no-ops on null)")
    void logoutAll_nullUser_delegatesSafely() {
        authService.logoutAll(null);
        // AuthService delegates unconditionally; SessionRevocationService
        // contains the null guard. Verifying the delegation confirms wiring.
        verify(sessionRevocationService).revokeAllSessions(null);
    }

    // --- SEC-04: logout (single) ---

    @Test
    @DisplayName("logout revokes the single presented refresh token")
    void logout_revokesSingleToken() {
        RefreshToken stored = storedToken(orgId, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.logout(rawRefreshToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertNotNull(captor.getValue().getRevokedAt());
    }

    @Test
    @DisplayName("logout with blank token is a no-op")
    void logout_blankToken_noOp() {
        authService.logout("");
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    // --- SEC-06: Uniform login failures (no account enumeration) ---

    private static final BCryptPasswordEncoder TEST_ENCODER = new BCryptPasswordEncoder();
    private static final String CORRECT_PASSWORD = "correct-password";
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final String CORRECT_HASH = TEST_ENCODER.encode(CORRECT_PASSWORD);

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    @Test
    @DisplayName("SEC-06: wrong password for deactivated user returns 'Invalid email or password' (not 'Account is deactivated')")
    void login_wrongPasswordForDeactivatedUser_uniformError() {
        User deactivated = activeUser(orgId);
        deactivated.setIsActive(false);
        deactivated.setPasswordHash(CORRECT_HASH);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(deactivated));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginRequest("user@example.com", WRONG_PASSWORD)));
        assertEquals("Invalid email or password", ex.getMessage(),
                "Wrong password must return uniform error regardless of account state");
    }

    @Test
    @DisplayName("SEC-06: wrong password for locked user returns 'Invalid email or password' (not 'Account is locked')")
    void login_wrongPasswordForLockedUser_uniformError() {
        User locked = activeUser(orgId);
        locked.setIsLocked(true);
        locked.setPasswordHash(CORRECT_HASH);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(locked));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginRequest("user@example.com", WRONG_PASSWORD)));
        assertEquals("Invalid email or password", ex.getMessage(),
                "Wrong password must return uniform error regardless of account state");
    }

    @Test
    @DisplayName("SEC-06: correct password for deactivated user returns 'Account is deactivated'")
    void login_correctPasswordForDeactivatedUser_showsDeactivated() {
        User deactivated = activeUser(orgId);
        deactivated.setIsActive(false);
        deactivated.setPasswordHash(CORRECT_HASH);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(deactivated));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.login(loginRequest("user@example.com", CORRECT_PASSWORD)));
        assertTrue(ex.getMessage().contains("deactivated"),
                "Correct password for deactivated user should reveal the account state");
    }

    @Test
    @DisplayName("SEC-06: correct password for locked user returns 'Account is locked'")
    void login_correctPasswordForLockedUser_showsLocked() {
        User locked = activeUser(orgId);
        locked.setIsLocked(true);
        locked.setPasswordHash(CORRECT_HASH);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(locked));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.login(loginRequest("user@example.com", CORRECT_PASSWORD)));
        assertTrue(ex.getMessage().contains("locked"),
                "Correct password for locked user should reveal the account state");
    }

    @Test
    @DisplayName("SEC-06: user not found returns 'Invalid email or password'")
    void login_userNotFound_uniformError() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(loginRequest("unknown@example.com", WRONG_PASSWORD)));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    // --- Helper ---

    private RefreshTokenRequest refreshRequest() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(rawRefreshToken);
        return req;
    }
}
