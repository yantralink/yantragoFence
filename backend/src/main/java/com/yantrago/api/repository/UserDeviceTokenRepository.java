package com.yantrago.api.repository;

import com.yantrago.api.model.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user_device_tokens.
 *
 * Per notification plan Phase 5: authenticated lifecycle APIs, protected token storage.
 */
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

    /**
     * Finds all active tokens for a user (multi-device support).
     */
    List<UserDeviceToken> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Finds all active tokens for a user in an organization.
     */
    List<UserDeviceToken> findByOrganizationIdAndUserIdAndIsActiveTrue(UUID organizationId, UUID userId);

    /**
     * Finds an active token by user and token value (for dedup on re-registration).
     */
    Optional<UserDeviceToken> findByUserIdAndTokenAndIsActiveTrue(UUID userId, String token);

    /**
     * Phase 5 fix: finds an active token by user and token fingerprint (encrypted storage).
     */
    Optional<UserDeviceToken> findByUserIdAndTokenFingerprintAndIsActiveTrue(UUID userId, String fingerprint);

    /**
     * Phase 5 fix: finds any token (active or inactive) by user and fingerprint.
     */
    Optional<UserDeviceToken> findByUserIdAndTokenFingerprint(UUID userId, String fingerprint);

    /**
     * Deactivates all tokens for a user (on logout).
     */
    @Modifying
    @Query("UPDATE UserDeviceToken t SET t.isActive = false, t.updatedAt = :now WHERE t.userId = :userId AND t.isActive = true")
    int deactivateAllForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Marks a token as invalidated (provider reported invalid).
     */
    @Modifying
    @Query("UPDATE UserDeviceToken t SET t.isActive = false, t.invalidatedAt = :now, " +
            "t.invalidationReason = :reason, t.updatedAt = :now WHERE t.id = :tokenId")
    int invalidateToken(@Param("tokenId") UUID tokenId, @Param("now") LocalDateTime now,
                       @Param("reason") String reason);
}
