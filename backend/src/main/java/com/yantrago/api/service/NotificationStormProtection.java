package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Notification storm protection — per-user rate limiting using Redis sliding window.
 *
 * Per notification plan Phase 6 / N11:
 * - "Exercise ... a notification storm"
 * - "bounded backlog/retries"
 *
 * This prevents a single user from being flooded with notifications when
 * many alerts fire simultaneously (e.g. fleet-wide power outage).
 *
 * Rate limits:
 * - Per-user: max N notifications per minute (default 10)
 * - Per-user per-alert-type: max M notifications per hour (default 5)
 *
 * When rate limited, the inbox item is still created (so the user sees it
 * when they open the app) but push delivery is suppressed for that user.
 *
 * Per AGENTS.md rule 12: production feature with logging.
 */
@Service
public class NotificationStormProtection {

    private static final Logger log = LoggerFactory.getLogger(NotificationStormProtection.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxPerMinute;
    private final int maxPerAlertTypePerHour;

    // Redis key prefixes
    private static final String PER_MINUTE_KEY = "notify:rate:user:min:";
    private static final String PER_TYPE_KEY = "notify:rate:user:type:";

    public NotificationStormProtection(StringRedisTemplate redisTemplate,
                                        @Value("${notification.storm.max-per-minute:10}") int maxPerMinute,
                                        @Value("${notification.storm.max-per-type-per-hour:5}") int maxPerAlertTypePerHour) {
        this.redisTemplate = redisTemplate;
        this.maxPerMinute = maxPerMinute;
        this.maxPerAlertTypePerHour = maxPerAlertTypePerHour;
    }

    /**
     * Checks if a notification for the given user should be allowed to send push.
     * Returns true if push is allowed, false if rate-limited.
     *
     * Note: inbox item creation is never rate-limited — only push delivery is.
     */
    public boolean allowPushForUser(UUID userId, String alertType) {
        try {
            String minuteKey = PER_MINUTE_KEY + userId;
            Long minuteCount = redisTemplate.opsForValue().increment(minuteKey);
            if (minuteCount != null && minuteCount == 1) {
                redisTemplate.expire(minuteKey, Duration.ofMinutes(1));
            }
            if (minuteCount != null && minuteCount > maxPerMinute) {
                log.info("Push rate-limited for user={} (per-minute: {}/{}) alertType={}",
                        userId, minuteCount, maxPerMinute, alertType);
                return false;
            }

            if (alertType != null) {
                String typeKey = PER_TYPE_KEY + userId + ":" + alertType;
                Long typeCount = redisTemplate.opsForValue().increment(typeKey);
                if (typeCount != null && typeCount == 1) {
                    redisTemplate.expire(typeKey, Duration.ofHours(1));
                }
                if (typeCount != null && typeCount > maxPerAlertTypePerHour) {
                    log.info("Push rate-limited for user={} (per-type: {}/{}) alertType={}",
                            userId, typeCount, maxPerAlertTypePerHour, alertType);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            // If Redis is down, allow push (fail-open for push, fail-closed for inbox)
            log.warn("Storm protection Redis check failed — allowing push: {}", e.getMessage());
            return true;
        }
    }
}
