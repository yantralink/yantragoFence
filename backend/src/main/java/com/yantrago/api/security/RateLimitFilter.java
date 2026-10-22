package com.yantrago.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SEC-06: Per-IP and per-user rate limiting using Bucket4j with bounded
 * expiring state.
 *
 * Fixes applied:
 * - Client IP resolved only through trusted proxies (TrustedProxyResolver).
 *   Spoofed X-Forwarded-For headers from untrusted sources are ignored.
 * - Loopback exemption removed: the loopback address is now a trusted
 *   proxy, so the real client IP is resolved from X-Forwarded-For. This
 *   means all external clients (arriving via nginx) are individually
 *   rate-limited by their real IP, not collectively skipped.
 * - Bounded bucket maps with TTL eviction to prevent memory exhaustion
 *   under attack.
 * - Separate pre-auth IP limit and post-auth user limit.
 * - Stricter login/refresh endpoint limits to prevent brute-force.
 * - 429 responses include Retry-After header.
 *
 * Filter ordering:
 * - This filter runs BEFORE JwtAuthFilter for the pre-auth IP limit and
 *   login/refresh brute-force limits.
 * - The post-auth per-user limit is handled by {@link PostAuthRateLimitFilter},
 *   which runs AFTER JwtAuthFilter so the SecurityContext is populated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Pre-auth IP limits (apply to all requests)
    private static final int IP_LIMIT_PER_MINUTE = 100;

    // Post-auth user limits (apply only to authenticated requests)
    // Handled by PostAuthRateLimitFilter which runs after JwtAuthFilter.

    // Login/refresh endpoint limits (stricter, per IP)
    private static final int LOGIN_LIMIT_PER_MINUTE = 10;
    private static final int REFRESH_LIMIT_PER_MINUTE = 20;

    // Bucket TTL: entries not accessed for this duration are evicted
    private static final Duration BUCKET_TTL = Duration.ofMinutes(10);
    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private final ConcurrentMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> ipBucketLastAccess = new ConcurrentHashMap<>();

    private final TrustedProxyResolver proxyResolver;
    private volatile long lastCleanup = System.currentTimeMillis();

    public RateLimitFilter(TrustedProxyResolver proxyResolver) {
        this.proxyResolver = proxyResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        evictExpiredBuckets();

        String clientIp = proxyResolver.resolveClientIp(request);
        String path = request.getRequestURI();

        // SEC-06: Stricter limits for login/refresh endpoints (brute-force protection)
        if (isLoginEndpoint(path)) {
            Bucket loginBucket = ipBuckets.computeIfAbsent(
                    "login:" + clientIp, k -> createBucket(LOGIN_LIMIT_PER_MINUTE));
            touch(ipBucketLastAccess, "login:" + clientIp);
            ConsumptionProbe probe = loginBucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                log.warn("SEC-06: Login rate limit exceeded for IP={}", clientIp);
                sendTooManyRequests(response, probe);
                return;
            }
        } else if (isRefreshEndpoint(path)) {
            Bucket refreshBucket = ipBuckets.computeIfAbsent(
                    "refresh:" + clientIp, k -> createBucket(REFRESH_LIMIT_PER_MINUTE));
            touch(ipBucketLastAccess, "refresh:" + clientIp);
            ConsumptionProbe probe = refreshBucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                log.warn("SEC-06: Refresh rate limit exceeded for IP={}", clientIp);
                sendTooManyRequests(response, probe);
                return;
            }
        }

        // SEC-06: Pre-auth per-IP limit (applies to all requests)
        Bucket ipBucket = ipBuckets.computeIfAbsent(
                "ip:" + clientIp, k -> createBucket(IP_LIMIT_PER_MINUTE));
        touch(ipBucketLastAccess, "ip:" + clientIp);
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        if (!ipProbe.isConsumed()) {
            log.warn("SEC-06: IP rate limit exceeded for IP={}", clientIp);
            sendTooManyRequests(response, ipProbe);
            return;
        }

        // SEC-06: Post-auth per-user limit is handled by PostAuthRateLimitFilter,
        // which runs after JwtAuthFilter so the SecurityContext is populated.

        filterChain.doFilter(request, response);
    }

    private boolean isLoginEndpoint(String path) {
        return "/api/v1/auth/login".equals(path);
    }

    private boolean isRefreshEndpoint(String path) {
        return "/api/v1/auth/refresh".equals(path);
    }

    private Bucket createBucket(int limitPerMinute) {
        Bandwidth limit = Bandwidth.classic(limitPerMinute,
                Refill.intervally(limitPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private void sendTooManyRequests(HttpServletResponse response, ConsumptionProbe probe)
            throws IOException {
        long retryAfterSeconds = Math.max(1,
                Duration.ofNanos(probe.getNanosToWaitForRefill()).getSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(
                "{\"error\":\"Rate limit exceeded. Try again later.\"}");
    }

    /**
     * SEC-06: Evicts bucket entries not accessed within BUCKET_TTL to
     * prevent unbounded memory growth under attack.
     */
    private void evictExpiredBuckets() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanup = now;
        long cutoff = now - BUCKET_TTL.toMillis();

        for (Map.Entry<String, Long> entry : ipBucketLastAccess.entrySet()) {
            if (entry.getValue() < cutoff) {
                String key = entry.getKey();
                ipBuckets.remove(key);
                ipBucketLastAccess.remove(key);
            }
        }
    }

    private void touch(ConcurrentMap<String, Long> lastAccess, String key) {
        lastAccess.put(key, System.currentTimeMillis());
    }
}
