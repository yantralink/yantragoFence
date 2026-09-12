package com.yantrago.api.service;

import com.yantrago.api.dto.push.DeviceTokenRequest;
import com.yantrago.api.model.UserDeviceToken;
import com.yantrago.api.repository.UserDeviceTokenRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.service.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for DeviceTokenService.
 *
 * Verifies Phase 5 acceptance criteria:
 * - Authenticated lifecycle APIs (register/unregister)
 * - Token rotation on re-registration
 * - Logout deactivates all tokens
 * - User can only unregister their own tokens
 * - Invalid token removal
 *
 * Per notification plan Phase 5: protected token storage, token rotation/logout.
 */
class DeviceTokenServiceTest {

    private UserDeviceTokenRepository tokenRepository;
    private OwnerContextService ownerContextService;
    private PermissionEvaluator permissionEvaluator;
    private TokenEncryptionService tokenEncryptionService;

    private DeviceTokenService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tokenRepository = mock(UserDeviceTokenRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        tokenEncryptionService = new TokenEncryptionService("");

        service = new DeviceTokenService(tokenRepository, ownerContextService, permissionEvaluator, tokenEncryptionService);

        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
    }

    private DeviceTokenRequest createRequest() {
        DeviceTokenRequest req = new DeviceTokenRequest();
        req.setToken("fcm-token-123");
        req.setPlatform("ANDROID");
        req.setDeviceLabel("Pixel 7");
        req.setAppVersion("1.0.0");
        return req;
    }

    private UserDeviceToken createToken() {
        UserDeviceToken token = new UserDeviceToken();
        token.setId(tokenId);
        token.setOrganizationId(orgId);
        token.setUserId(userId);
        token.setToken("fcm-token-123");
        token.setTokenFingerprint(tokenEncryptionService.fingerprint("fcm-token-123"));
        token.setPlatform("ANDROID");
        token.setIsActive(true);
        return token;
    }

    @Test
    @DisplayName("registerToken: creates new token when none exists")
    void registerToken_createsNewToken() {
        String fingerprint = tokenEncryptionService.fingerprint("fcm-token-123");
        when(tokenRepository.findByUserIdAndTokenFingerprintAndIsActiveTrue(userId, fingerprint))
                .thenReturn(Optional.empty());
        when(tokenRepository.findByUserIdAndTokenFingerprint(userId, fingerprint))
                .thenReturn(Optional.empty());
        when(tokenRepository.save(any())).thenAnswer(inv -> {
            UserDeviceToken t = inv.getArgument(0);
            t.setId(tokenId);
            return t;
        });

        var result = service.registerToken(createRequest());

        assertNotNull(result);
        assertEquals("ANDROID", result.platform());
        assertEquals("Pixel 7", result.deviceLabel());
        assertTrue(result.isActive());
        verify(tokenRepository).save(any(UserDeviceToken.class));
    }

    @Test
    @DisplayName("registerToken: updates metadata when token already active")
    void registerToken_updatesExistingActiveToken() {
        UserDeviceToken existing = createToken();
        existing.setDeviceLabel("Old Label");
        String fingerprint = tokenEncryptionService.fingerprint("fcm-token-123");
        when(tokenRepository.findByUserIdAndTokenFingerprintAndIsActiveTrue(userId, fingerprint))
                .thenReturn(Optional.of(existing));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.registerToken(createRequest());

        assertEquals("Pixel 7", result.deviceLabel());
        assertEquals("1.0.0", result.appVersion());
        verify(tokenRepository).save(existing);
    }

    @Test
    @DisplayName("unregisterToken: deactivates own token")
    void unregisterToken_deactivatesOwnToken() {
        UserDeviceToken token = createToken();
        when(tokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.unregisterToken(tokenId);

        assertFalse(token.getIsActive());
        verify(tokenRepository).save(token);
    }

    @Test
    @DisplayName("unregisterToken: rejects if token belongs to another user")
    void unregisterToken_rejectsOtherUserToken() {
        UserDeviceToken token = createToken();
        token.setUserId(UUID.randomUUID()); // different user
        when(tokenRepository.findById(tokenId)).thenReturn(Optional.of(token));

        assertThrows(SecurityException.class, () -> service.unregisterToken(tokenId));
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("unregisterToken: rejects if token not found")
    void unregisterToken_rejectsNotFound() {
        when(tokenRepository.findById(tokenId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.unregisterToken(tokenId));
    }

    @Test
    @DisplayName("deactivateAllForUser: deactivates all tokens on logout")
    void deactivateAllForUser_deactivatesAll() {
        when(tokenRepository.deactivateAllForUser(eq(userId), any()))
                .thenReturn(3);

        service.deactivateAllForUser();

        verify(tokenRepository).deactivateAllForUser(eq(userId), any());
    }

    @Test
    @DisplayName("listMyTokens: returns active tokens for current user")
    void listMyTokens_returnsActiveTokens() {
        UserDeviceToken t1 = createToken();
        t1.setId(UUID.randomUUID());
        UserDeviceToken t2 = createToken();
        t2.setId(UUID.randomUUID());
        t2.setToken("token-2");
        when(tokenRepository.findByUserIdAndIsActiveTrue(userId))
                .thenReturn(List.of(t1, t2));

        var result = service.listMyTokens();

        assertEquals(2, result.size());
        assertTrue(result.get(0).isActive());
    }

    @Test
    @DisplayName("invalidateToken: calls repository invalidate")
    void invalidateToken_callsRepository() {
        when(tokenRepository.invalidateToken(eq(tokenId), any(), eq("UNREGISTERED")))
                .thenReturn(1);

        service.invalidateToken(tokenId, "UNREGISTERED");

        verify(tokenRepository).invalidateToken(eq(tokenId), any(), eq("UNREGISTERED"));
    }

    @Test
    @DisplayName("getActiveTokensForUser: returns tokens for push delivery")
    void getActiveTokensForUser_returnsTokens() {
        UserDeviceToken token = createToken();
        when(tokenRepository.findByUserIdAndIsActiveTrue(userId))
                .thenReturn(List.of(token));

        List<UserDeviceToken> result = service.getActiveTokensForUser(userId);

        assertEquals(1, result.size());
        assertEquals("fcm-token-123", result.get(0).getToken());
    }
}
