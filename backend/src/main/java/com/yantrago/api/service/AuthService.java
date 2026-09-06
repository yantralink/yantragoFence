package com.yantrago.api.service;

import com.yantrago.api.dto.auth.LoginRequest;
import com.yantrago.api.dto.auth.LoginResponse;
import com.yantrago.api.dto.auth.RefreshTokenRequest;
import com.yantrago.api.dto.auth.TokenResponse;
import com.yantrago.api.model.RefreshToken;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.RefreshTokenRepository;
import com.yantrago.api.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authentication service: login, refresh-token rotation, logout.
 * Refresh tokens are one-time-use: each refresh rotates to a new token,
 * revoking the old one and linking it to the replacement (rotation chain).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }
        if (user.getIsLocked()) {
            throw new IllegalStateException("Account is locked");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email={}", request.getEmail());
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getOrganizationId(), null);
        String refreshTokenStr = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getOrganizationId());

        // Persist refresh token hash
        persistRefreshToken(refreshTokenStr, user);

        long expiresIn = jwtService.getAccessTtlSeconds();
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationId() != null ? user.getOrganizationId().toString() : null
        );

        log.info("User {} logged in successfully", user.getEmail());
        return new LoginResponse(accessToken, refreshTokenStr, "Bearer", expiresIn, userInfo);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        if (!jwtService.validateRefreshToken(rawToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        Claims claims = jwtService.parseRefreshToken(rawToken);
        String tokenHash = jwtService.hashToken(rawToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (stored.getRevokedAt() != null) {
            log.warn("Attempted reuse of revoked refresh token for user={}", stored.getUserId());
            // Revoke the entire chain — possible token theft
            refreshTokenRepository.revokeAllByUserId(stored.getUserId(), LocalDateTime.now());
            throw new IllegalStateException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }

        UUID userId = stored.getUserId();
        UUID orgId = stored.getOrganizationId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Rotate: revoke old token, issue new one
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        String newAccessToken = jwtService.generateAccessToken(userId, user.getEmail(), orgId, null);
        String newRefreshToken = jwtService.generateRefreshToken(userId, user.getEmail(), orgId);

        RefreshToken newStored = persistRefreshToken(newRefreshToken, user);
        // Link rotation chain
        stored.setReplacedBy(newStored.getId());
        refreshTokenRepository.save(stored);

        return new TokenResponse(newAccessToken, newRefreshToken, "Bearer", jwtService.getAccessTtlSeconds());
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            return;
        }
        String tokenHash = jwtService.hashToken(refreshTokenStr);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            log.info("User {} logged out", token.getUserId());
        });
    }

    private RefreshToken persistRefreshToken(String rawToken, User user) {
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setOrganizationId(user.getOrganizationId() != null ? user.getOrganizationId() : UUID.randomUUID());
        // For super_admin (null org), we still need a non-null org_id due to the NOT NULL constraint.
        // This is a known limitation — super_admin refresh tokens use a placeholder org_id.
        // TODO: consider making refresh_tokens.organization_id nullable for super_admin support.
        token.setTokenHash(jwtService.hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTtlSeconds()));
        return refreshTokenRepository.save(token);
    }
}
