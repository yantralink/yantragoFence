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
 * Authentication service: login, refresh-token rotation, logout, logout-all.
 *
 * Refresh tokens are one-time-use: each refresh rotates to a new token,
 * revoking the old one and linking it to the replacement (rotation chain).
 *
 * SEC-04: refresh validates the current user/org state (active, not locked,
 * org active) and uses the user's current organization — not the stale
 * token's org. Security-state changes (password change, deactivation,
 * locking, tenant/role changes) revoke all refresh tokens via
 * {@link SessionRevocationService}.
 *
 * SEC-05: refresh rotation is atomic. The old token is consumed via a
 * conditional UPDATE (compare-and-set on revokedAt IS NULL) so concurrent
 * refreshes cannot both win. Replay-triggered family revocation commits in
 * a separate transaction (REQUIRES_NEW) so it survives the rollback of the
 * rejected refresh.
 *
 * Access tokens are short-lived (default 15 min) and stateless; revocation
 * is enforced at the refresh-token level, bounding the exposure window
 * after a security-state change to the access-token TTL.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final SessionRevocationService sessionRevocationService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       SessionRevocationService sessionRevocationService,
                       JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // SEC-06: Check password BEFORE account-state checks so that
        // wrong-password attempts always return "Invalid email or password"
        // regardless of account state. This prevents account enumeration
        // (an attacker cannot distinguish "user not found" from "user
        // exists but is deactivated/locked" without knowing the password).
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email={}", request.getEmail());
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Account-state checks only revealed after password verification succeeds.
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

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Fetch user roles for JWT claims
        String roles = jdbcTemplate.queryForList(
                "SELECT r.name FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, user.getId()
        ).stream().collect(Collectors.joining(","));

        // Fetch user permissions for JWT claims (from role_permissions -> permissions)
        String permissions = jdbcTemplate.queryForList(
                "SELECT DISTINCT p.name FROM user_roles ur " +
                        "JOIN role_permissions rp ON rp.role_id = ur.role_id " +
                        "JOIN permissions p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ?",
                String.class, user.getId()
        ).stream().collect(Collectors.joining(","));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getOrganizationId(), roles, permissions);
        String refreshTokenStr = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getOrganizationId());

        // Persist refresh token hash
        persistRefreshToken(refreshTokenStr, user);

        long expiresIn = jwtService.getAccessTtlSeconds();
        String primaryRole = roles.isEmpty() ? "viewer" : roles.split(",")[0];
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationId() != null ? user.getOrganizationId().toString() : null,
                organizationName,
                primaryRole,
                user.getIsActive(),
                user.getPreferredLocale(),
                user.getPhone()
        );

        log.info("User {} logged in successfully", user.getEmail());
        return new LoginResponse(accessToken, refreshTokenStr, "Bearer", expiresIn, userInfo);
    }

    /**
     * Updates the authenticated user's preferred notification language.
     *
     * Per Multilingual Plan Phase 4: self-service only — the target user is
     * always resolved from the JWT principal (never from the request body),
     * so a user can change only their own preference. Validation of the
     * locale code happens via Bean Validation on the request DTO; the
     * database CHECK constraint (V51) is defense in depth. Idempotent:
     * re-sending the current locale is a no-op.
     */
    @Transactional
    public User updatePreferredLocale(UUID userId, String locale) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String previous = user.getPreferredLocale();
        if (locale.equals(previous)) {
            return user; // idempotent — no write needed
        }
        user.setPreferredLocale(locale);
        User saved = userRepository.save(user);
        log.info("Preferred locale updated: userId={}, {} -> {}", userId, previous, locale);
        return saved;
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

        // SEC-05: atomic consume — conditional UPDATE on revokedAt IS NULL.
        // Returns 1 if this call revoked the token (won the race), 0 if it
        // was already revoked (lost the race, logout, or replay).
        int consumed = refreshTokenRepository.revokeIfNotAlready(stored.getId(), LocalDateTime.now());
        if (consumed == 0) {
            // Already revoked — replay or concurrent loser. Revoke the entire
            // family in a NEW transaction so it commits even though we throw
            // and roll back this refresh transaction (SEC-05).
            sessionRevocationService.revokeFamilyOnReplay(stored.getUserId(), stored.getId());
            throw new IllegalStateException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Consumed but expired — no new token. (The consume above already
            // marked it revoked, which is harmless for an expired token.)
            throw new IllegalArgumentException("Refresh token expired");
        }

        UUID userId = stored.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // SEC-04: validate current account/org state on refresh — a disabled
        // or locked user, or a user whose organization was deactivated, must
        // not be able to obtain new access tokens via refresh.
        validateAccountState(user);

        // SEC-04: use the user's CURRENT organization for the new tokens, not
        // the stale org captured when the refresh token was issued. If the
        // user was moved between orgs, the new token reflects the new tenant.
        UUID currentOrgId = user.getOrganizationId();

        // Fetch user roles for new access token
        String roles = jdbcTemplate.queryForList(
                "SELECT r.name FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId
        ).stream().collect(Collectors.joining(","));

        // Fetch user permissions for new access token
        String permissions = jdbcTemplate.queryForList(
                "SELECT DISTINCT p.name FROM user_roles ur " +
                        "JOIN role_permissions rp ON rp.role_id = ur.role_id " +
                        "JOIN permissions p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ?",
                String.class, userId
        ).stream().collect(Collectors.joining(","));

        String newAccessToken = jwtService.generateAccessToken(userId, user.getEmail(), currentOrgId, roles, permissions);
        String newRefreshToken = jwtService.generateRefreshToken(userId, user.getEmail(), currentOrgId);

        RefreshToken newStored = persistRefreshToken(newRefreshToken, user);
        // Link rotation chain
        stored.setReplacedBy(newStored.getId());
        refreshTokenRepository.save(stored);

        return new TokenResponse(newAccessToken, newRefreshToken, "Bearer", jwtService.getAccessTtlSeconds());
    }

    /**
     * SEC-04: validates that the user is active, not locked, and (if they
     * belong to an org) the org is active. Mirrors the checks performed at
     * login so that a disabled/locked user cannot keep refreshing.
     */
    private void validateAccountState(User user) {
        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }
        if (user.getIsLocked()) {
            throw new IllegalStateException("Account is locked");
        }
        if (user.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(user.getOrganizationId()).orElse(null);
            if (org != null && !org.getIsActive()) {
                throw new IllegalStateException("Organization is deactivated. Contact your platform administrator.");
            }
        }
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

    /**
     * SEC-04: revokes all outstanding refresh tokens for the given user
     * ("logout all devices"). Access tokens remain valid until they expire
     * (bounded by the access-token TTL, default 15 min).
     */
    public void logoutAll(UUID userId) {
        sessionRevocationService.revokeAllSessions(userId);
        log.info("User {} logged out of all devices", userId);
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
