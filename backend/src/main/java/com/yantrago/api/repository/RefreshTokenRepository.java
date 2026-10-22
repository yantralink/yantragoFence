package com.yantrago.api.repository;

import com.yantrago.api.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    void revokeAllByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * SEC-05: Atomic compare-and-set consume of a refresh token.
     * Returns 1 if this call revoked the token (caller won the race),
     * 0 if it was already revoked (caller lost the race or it was
     * revoked by logout/an earlier replay). The atomic UPDATE avoids
     * two concurrent refreshes both observing an un-revoked token.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.id = :id AND t.revokedAt IS NULL")
    int revokeIfNotAlready(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :before AND t.revokedAt IS NOT NULL")
    void deleteExpiredAndRevokedBefore(@Param("before") LocalDateTime before);
}
