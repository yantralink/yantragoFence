package com.yantrago.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for NotificationStormProtection (Phase 6).
 *
 * Per notification plan Phase 6 / N11:
 * - "Exercise ... a notification storm"
 * - "bounded backlog/retries"
 */
@ExtendWith(MockitoExtension.class)
class NotificationStormProtectionTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private NotificationStormProtection protection;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        protection = new NotificationStormProtection(redisTemplate, 10, 5);
    }

    @Test
    @DisplayName("First notification for user — allowed")
    void firstNotification_allowed() {
        UUID userId = UUID.randomUUID();
        when(valueOps.increment(anyString())).thenReturn(1L);

        boolean allowed = protection.allowPushForUser(userId, "LOW_BATTERY");

        assertTrue(allowed);
        verify(valueOps, times(2)).increment(anyString()); // per-minute + per-type
    }

    @Test
    @DisplayName("Per-minute limit exceeded — rate-limited")
    void perMinuteLimitExceeded_rateLimited() {
        UUID userId = UUID.randomUUID();
        when(valueOps.increment(anyString())).thenReturn(11L); // exceeds limit of 10

        boolean allowed = protection.allowPushForUser(userId, "LOW_BATTERY");

        assertFalse(allowed);
    }

    @Test
    @DisplayName("Per-type limit exceeded — rate-limited")
    void perTypeLimitExceeded_rateLimited() {
        UUID userId = UUID.randomUUID();
        // First call (per-minute) returns 1, second call (per-type) returns 6 (exceeds 5)
        when(valueOps.increment(anyString())).thenReturn(1L, 6L);

        boolean allowed = protection.allowPushForUser(userId, "LOW_BATTERY");

        assertFalse(allowed);
    }

    @Test
    @DisplayName("Redis down — fails open (allows push)")
    void redisDown_failsOpen() {
        UUID userId = UUID.randomUUID();
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis down"));

        boolean allowed = protection.allowPushForUser(userId, "LOW_BATTERY");

        assertTrue(allowed);
    }

    @Test
    @DisplayName("Null alertType — only per-minute check applied")
    void nullAlertType_onlyPerMinuteCheck() {
        UUID userId = UUID.randomUUID();
        when(valueOps.increment(anyString())).thenReturn(1L);

        boolean allowed = protection.allowPushForUser(userId, null);

        assertTrue(allowed);
        // Only one increment call (per-minute only, no per-type)
        verify(valueOps, times(1)).increment(anyString());
    }

    @Test
    @DisplayName("First increment sets expiry on key")
    void firstIncrement_setsExpiry() {
        UUID userId = UUID.randomUUID();
        when(valueOps.increment(anyString())).thenReturn(1L);

        protection.allowPushForUser(userId, "LOW_BATTERY");

        // Verify expiry is set for both keys (per-minute and per-type)
        verify(redisTemplate).expire(contains("notify:rate:user:min:"), eq(Duration.ofMinutes(1)));
        verify(redisTemplate).expire(contains("notify:rate:user:type:"), eq(Duration.ofHours(1)));
    }
}
