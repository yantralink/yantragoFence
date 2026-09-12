package com.yantrago.api.service;

import com.yantrago.api.dto.push.DeviceTokenDto;
import com.yantrago.api.dto.push.DeviceTokenRequest;
import com.yantrago.api.model.UserDeviceToken;
import com.yantrago.api.repository.UserDeviceTokenRepository;
import com.yantrago.api.security.PermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user device push tokens.
 *
 * Per notification plan Phase 5:
 * - Authenticated lifecycle APIs (register/unregister)
 * - Protected token storage
 * - Token rotation on re-registration (deactivate old, activate new)
 * - Logout deactivates all tokens for the user
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 */
@Service
public class DeviceTokenService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenService.class);

    private final UserDeviceTokenRepository tokenRepository;
    private final OwnerContextService ownerContextService;
    private final PermissionEvaluator permissionEvaluator;
    private final TokenEncryptionService encryptionService;

    public DeviceTokenService(UserDeviceTokenRepository tokenRepository,
                                OwnerContextService ownerContextService,
                                PermissionEvaluator permissionEvaluator,
                                TokenEncryptionService encryptionService) {
        this.tokenRepository = tokenRepository;
        this.ownerContextService = ownerContextService;
        this.permissionEvaluator = permissionEvaluator;
        this.encryptionService = encryptionService;
    }

    /**
     * Registers (or re-activates) a device token for the current user.
     * If the token already exists (active or inactive), it is reactivated.
     */
    @Transactional
    public DeviceTokenDto registerToken(DeviceTokenRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID userId = permissionEvaluator.getCurrentUserId();

        // Phase 5 fix: compute fingerprint for lookup, encrypt token for storage
        String fingerprint = encryptionService.fingerprint(request.getToken());
        String encryptedToken = encryptionService.encrypt(request.getToken());

        // Check if token already exists for this user (by fingerprint)
        Optional<UserDeviceToken> existing = tokenRepository
                .findByUserIdAndTokenFingerprintAndIsActiveTrue(userId, fingerprint);

        if (existing.isPresent()) {
            // Token already active — update metadata
            UserDeviceToken token = existing.get();
            token.setPlatform(request.getPlatform());
            token.setDeviceLabel(request.getDeviceLabel());
            token.setAppVersion(request.getAppVersion());
            token.setUpdatedAt(LocalDateTime.now());
            token = tokenRepository.save(token);
            log.info("Updated existing device token id={} for user={}", token.getId(), userId);
            log.info("AUDIT: token_registered user={} tokenId={} action=update", userId, token.getId());
            return toDto(token);
        }

        // Also check for inactive token with same fingerprint (re-registration after logout)
        Optional<UserDeviceToken> inactive = tokenRepository
                .findByUserIdAndTokenFingerprint(userId, fingerprint);
        if (inactive.isPresent() && !inactive.get().getIsActive()) {
            UserDeviceToken t = inactive.get();
            t.setIsActive(true);
            t.setToken(encryptedToken); // re-encrypt in case key changed
            t.setTokenFingerprint(fingerprint);
            t.setPlatform(request.getPlatform());
            t.setDeviceLabel(request.getDeviceLabel());
            t.setAppVersion(request.getAppVersion());
            t.setInvalidatedAt(null);
            t.setInvalidationReason(null);
            t.setUpdatedAt(LocalDateTime.now());
            t = tokenRepository.save(t);
            log.info("Re-activated device token id={} for user={}", t.getId(), userId);
            log.info("AUDIT: token_registered user={} tokenId={} action=reactivate", userId, t.getId());
            return toDto(t);
        }

        // New token
        UserDeviceToken token = new UserDeviceToken();
        token.setOrganizationId(orgId);
        token.setUserId(userId);
        token.setToken(encryptedToken);
        token.setTokenFingerprint(fingerprint);
        token.setPlatform(request.getPlatform());
        token.setDeviceLabel(request.getDeviceLabel());
        token.setAppVersion(request.getAppVersion());
        token.setIsActive(true);
        token = tokenRepository.save(token);
        log.info("Registered new device token id={} for user={} platform={}",
                token.getId(), userId, request.getPlatform());
        log.info("AUDIT: token_registered user={} tokenId={} action=new platform={}",
                userId, token.getId(), request.getPlatform());
        return toDto(token);
    }

    /**
     * Unregisters a specific device token (e.g. user removes a device).
     */
    @Transactional
    public void unregisterToken(UUID tokenId) {
        UUID userId = permissionEvaluator.getCurrentUserId();
        UserDeviceToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Device token not found: " + tokenId));

        // Security: user can only unregister their own tokens
        if (!token.getUserId().equals(userId)) {
            throw new SecurityException("Cannot unregister another user's device token");
        }

        token.setIsActive(false);
        token.setUpdatedAt(LocalDateTime.now());
        tokenRepository.save(token);
        log.info("Unregistered device token id={} for user={}", tokenId, userId);
        log.info("AUDIT: token_unregistered user={} tokenId={}", userId, tokenId);
    }

    /**
     * Deactivates all tokens for the current user (on logout).
     */
    @Transactional
    public void deactivateAllForUser() {
        UUID userId = permissionEvaluator.getCurrentUserId();
        int count = tokenRepository.deactivateAllForUser(userId, LocalDateTime.now());
        log.info("Deactivated {} device tokens for user={} on logout", count, userId);
    }

    /**
     * Lists all active tokens for the current user.
     */
    @Transactional(readOnly = true)
    public List<DeviceTokenDto> listMyTokens() {
        UUID userId = permissionEvaluator.getCurrentUserId();
        return tokenRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Marks a token as invalid (called by PushDeliveryService when provider reports invalid token).
     */
    @Transactional
    public void invalidateToken(UUID tokenId, String reason) {
        int updated = tokenRepository.invalidateToken(tokenId, LocalDateTime.now(), reason);
        if (updated > 0) {
            log.info("Invalidated device token id={} reason={}", tokenId, reason);
            log.info("AUDIT: token_invalidated tokenId={} reason={}", tokenId, reason);
        }
    }

    /**
     * Gets all active tokens for a user (for push delivery).
     */
    @Transactional(readOnly = true)
    public List<UserDeviceToken> getActiveTokensForUser(UUID userId) {
        return tokenRepository.findByUserIdAndIsActiveTrue(userId);
    }

    private DeviceTokenDto toDto(UserDeviceToken t) {
        return new DeviceTokenDto(
                t.getId(),
                t.getPlatform(),
                t.getDeviceLabel(),
                t.getAppVersion(),
                t.getIsActive(),
                t.getLastUsedAt(),
                t.getCreatedAt()
        );
    }
}
