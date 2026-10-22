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
 * SEC-06: Post-authentication per-user rate limiting.
 *
 * This filter runs AFTER JwtAuthFilter in the security chain, so the
 * SecurityContext is populated and the authenticated user's UUID is
 * available. It enforces a per-user request rate limit that is
 * independent of the client IP.
 *
 * The pre-auth IP limit and login/refresh limits are handled by
 * {@link RateLimitFilter}, which runs before JwtAuthFilter.
 *
 * Bounded expiring state: entries not accessed for 10 minutes are
 * evicted to prevent memory exhaustion.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 12)
public class PostAuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PostAuthRateLimitFilter.class);

    private static final int USER_LIMIT_PER_MINUTE = 60;
    private static final Duration BUCKET_TTL = Duration.ofMinutes(10);
    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private final ConcurrentMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> userBucketLastAccess = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        evictExpiredBuckets();

        String userId = getUserId(request);
        if (userId != null) {
            Bucket userBucket = userBuckets.computeIfAbsent(
                    "user:" + userId, k -> createBucket());
            userBucketLastAccess.put("user:" + userId, System.currentTimeMillis());

            ConsumptionProbe probe = userBucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                log.warn("SEC-06: User rate limit exceeded for user={}", userId);
                sendTooManyRequests(response, probe);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(USER_LIMIT_PER_MINUTE,
                Refill.intervally(USER_LIMIT_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getUserId(HttpServletRequest request) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof java.util.UUID uuid) {
            return uuid.toString();
        }
        return null;
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

    private void evictExpiredBuckets() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanup = now;
        long cutoff = now - BUCKET_TTL.toMillis();

        for (Map.Entry<String, Long> entry : userBucketLastAccess.entrySet()) {
            if (entry.getValue() < cutoff) {
                String key = entry.getKey();
                userBuckets.remove(key);
                userBucketLastAccess.remove(key);
            }
        }
    }
}
