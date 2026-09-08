package com.yantrago.api.service;

import com.yantrago.api.dto.auth.LoginRequest;
import com.yantrago.api.dto.auth.LoginResponse;
import com.yantrago.api.dto.auth.RefreshTokenRequest;
import com.yantrago.api.dto.auth.TokenResponse;
import com.yantrago.api.model.Organization;
import com.yantrago.api.model.RefreshToken;
import com.yantrago.api.model.User;
import com.yantrago.api.repository.OrganizationRepository;
import com.yantrago.api.repository.RefreshTokenRepository;
import com.yantrago.api.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authentication service: login, refresh-token rotation, logout.
 * Refresh tokens are one-time-use: each refresh rotates to a new token,
 * revoking the old one and linking it to the replacement (rotation chain).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jdbcTemplate = jdbcTemplate;
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
        // Check organization is active (if user belongs to one) and fetch name for branding
        String organizationName = null;
        if (user.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(user.getOrganizationId()).orElse(null);
            if (org != null && !org.getIsActive()) {
                throw new IllegalStateException("Organization is deactivated. Contact your platform administrator.");
            }
            if (org != null) {
                organizationName = org.getName();
            }
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email={}", request.getEmail());
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Fetch user roles for JWT claims
        String roles = jdbcTemplate.queryForList(
                "SELECT r.name FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, user.getId()
        ).stream().collect(Collectors.joining(","));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getOrganizationId(), roles);
        String refreshTokenStr = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getOrganizationId());

        // Persist refresh token hash
        persistRefreshToken(refreshTokenStr, user);

        long expiresIn = jwtService.getAccessTtlSeconds();
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationId() != null ? user.getOrganizationId().toString() : null,
                organizationName
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

        // Fetch user roles for new access token
        String roles = jdbcTemplate.queryForList(
                "SELECT r.name FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId
        ).stream().collect(Collectors.joining(","));

        String newAccessToken = jwtService.generateAccessToken(userId, user.getEmail(), orgId, roles);
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
        token.setOrganizationId(user.getOrganizationId());
        // organization_id is nullable for super_admin (platform-level, no organization).
        token.setTokenHash(jwtService.hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTtlSeconds()));
        return refreshTokenRepository.save(token);
    }
}
