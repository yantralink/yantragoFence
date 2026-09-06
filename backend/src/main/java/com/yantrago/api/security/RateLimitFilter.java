package com.yantrago.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP and per-user rate limiting using Bucket4j.
 * Limits: 100 requests per minute per IP, 60 requests per minute per authenticated user.
 * Returns HTTP 429 (Too Many Requests) when the limit is exceeded.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int IP_LIMIT_PER_MINUTE = 100;
    private static final int USER_LIMIT_PER_MINUTE = 60;

    private final ConcurrentMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = getClientIp(request);

        Bucket ipBucket = ipBuckets.computeIfAbsent(ip, k -> createBucket(IP_LIMIT_PER_MINUTE));
        if (!ipBucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP={}", ip);
            sendTooManyRequests(response, "Rate limit exceeded. Try again later.");
            return;
        }

        // Per-user limit (if authenticated)
        String userId = getUserId(request);
        if (userId != null) {
            Bucket userBucket = userBuckets.computeIfAbsent(userId, k -> createBucket(USER_LIMIT_PER_MINUTE));
            if (!userBucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for user={}", userId);
                sendTooManyRequests(response, "Rate limit exceeded. Try again later.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createBucket(int limitPerMinute) {
        Bandwidth limit = Bandwidth.classic(limitPerMinute, Refill.intervally(limitPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getUserId(HttpServletRequest request) {
        // The JwtAuthFilter sets the principal as UUID; we can read from SecurityContext
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof java.util.UUID uuid) {
            return uuid.toString();
        }
        return null;
    }

    private void sendTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
