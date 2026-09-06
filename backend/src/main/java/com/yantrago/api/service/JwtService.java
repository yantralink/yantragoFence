package com.yantrago.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT sign/verify service using HS256 (jjwt 0.11.5 API).
 * Generates access tokens (short-lived) and refresh tokens (long-lived).
 * Claims: sub (email), userId, organizationId (nullable for super_admin), roles (comma-separated).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.access.secret}")
    private String accessSecret;

    @Value("${jwt.refresh.secret}")
    private String refreshSecret;

    @Value("${jwt.access.ttl:900}")
    private long accessTtlSeconds;

    @Value("${jwt.refresh.ttl:2592000}")
    private long refreshTtlSeconds;

    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void init() {
        if (accessSecret == null || accessSecret.isBlank() || accessSecret.length() < 32) {
            throw new IllegalStateException("jwt.access.secret must be set and at least 32 characters. Set via JWT_ACCESS_SECRET env var.");
        }
        if (refreshSecret == null || refreshSecret.isBlank() || refreshSecret.length() < 32) {
            throw new IllegalStateException("jwt.refresh.secret must be set and at least 32 characters. Set via JWT_REFRESH_SECRET env var.");
        }
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, UUID organizationId, String roles) {
        return buildToken(userId, email, organizationId, roles, accessKey, accessTtlSeconds);
    }

    public String generateRefreshToken(UUID userId, String email, UUID organizationId) {
        return buildToken(userId, email, organizationId, null, refreshKey, refreshTtlSeconds);
    }

    private String buildToken(UUID userId, String email, UUID organizationId, String roles,
                              SecretKey key, long ttlSeconds) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        if (organizationId != null) {
            claims.put("organizationId", organizationId.toString());
        }
        if (roles != null) {
            claims.put("roles", roles);
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return parseToken(token, accessKey);
    }

    public Claims parseRefreshToken(String token) {
        return parseToken(token, refreshKey);
    }

    private Claims parseToken(String token, SecretKey key) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, accessKey);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, refreshKey);
    }

    private boolean validateToken(String token, SecretKey key) {
        try {
            Claims claims = parseToken(token, key);
            return claims.getExpiration().after(Date.from(Instant.now()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.get("userId", String.class));
    }

    public UUID extractOrganizationId(Claims claims) {
        String orgId = claims.get("organizationId", String.class);
        return orgId != null ? UUID.fromString(orgId) : null;
    }

    public String extractRoles(Claims claims) {
        return claims.get("roles", String.class);
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    /**
     * SHA-256 hash of a refresh token for storage in refresh_tokens table.
     * We never store raw refresh tokens — only their hash.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
