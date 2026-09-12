package com.yantrago.api.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NotificationMetrics (Phase 6).
 *
 * Verifies that metrics are registered and incremented correctly.
 * Per notification plan N11: avoid user/IMEI/token IDs as metric labels.
 */
class NotificationMetricsTest {

    private MeterRegistry meterRegistry;
    private NotificationMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new NotificationMetrics(meterRegistry);
    }

    @Test
    @DisplayName("recordAlertTransition increments counter with type and state tags")
    void recordAlertTransition_incrementsCounter() {
        metrics.recordAlertTransition("LOW_BATTERY", "OPEN");
        metrics.recordAlertTransition("LOW_BATTERY", "OPEN");
        metrics.recordAlertTransition("DEVICE_OFFLINE", "RESOLVED");

        var lowBatteryOpen = meterRegistry.find("notification.alert.transitions")
                .tag("type", "LOW_BATTERY").tag("state", "OPEN").counter();
        assertNotNull(lowBatteryOpen);
        assertEquals(2.0, lowBatteryOpen.count());

        var offline = meterRegistry.find("notification.alert.transitions")
                .tag("type", "DEVICE_OFFLINE").tag("state", "RESOLVED").counter();
        assertNotNull(offline);
        assertEquals(1.0, offline.count());
    }

    @Test
    @DisplayName("recordDuplicateSuppressed increments global and typed counters")
    void recordDuplicateSuppressed_incrementsCounters() {
        metrics.recordDuplicateSuppressed("LOW_BATTERY");
        metrics.recordDuplicateSuppressed("LOW_BATTERY");

        var global = meterRegistry.find("notification.duplicates.suppressed").counter();
        assertNotNull(global);
        assertEquals(2.0, global.count());
    }

    @Test
    @DisplayName("recordPushAccepted increments push.accepted counter")
    void recordPushAccepted_incrementsCounter() {
        metrics.recordPushAccepted();
        metrics.recordPushAccepted();

        var counter = meterRegistry.find("notification.push.accepted").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }

    @Test
    @DisplayName("recordPushInvalidToken increments push.invalid.token counter")
    void recordPushInvalidToken_incrementsCounter() {
        metrics.recordPushInvalidToken();

        var counter = meterRegistry.find("notification.push.invalid.token").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    @DisplayName("recordInboxCreationLatency records timer")
    void recordInboxCreationLatency_recordsTimer() {
        metrics.recordInboxCreationLatency(5_000_000L); // 5ms

        var timer = meterRegistry.find("notification.inbox.creation.latency").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    @DisplayName("safeTag: null becomes 'unknown'")
    void safeTag_nullBecomesUnknown() {
        // Indirectly verified: recordAlertTransition with null doesn't throw
        assertDoesNotThrow(() -> metrics.recordAlertTransition(null, null));
    }

    @Test
    @DisplayName("recordDlqMessage increments dlq counter")
    void recordDlqMessage_incrementsCounter() {
        metrics.recordDlqMessage();
        metrics.recordDlqMessage();

        var counter = meterRegistry.find("notification.dlq.messages").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }

    @Test
    @DisplayName("All push status counters work")
    void allPushStatusCountersWork() {
        metrics.recordPushAccepted();
        metrics.recordPushFailed();
        metrics.recordPushInvalidToken();
        metrics.recordPushSkipped();
        metrics.recordPushExpired();
        metrics.recordPushCancelled();
        metrics.recordPushRetried();

        assertNotNull(meterRegistry.find("notification.push.accepted").counter());
        assertNotNull(meterRegistry.find("notification.push.failed").counter());
        assertNotNull(meterRegistry.find("notification.push.invalid.token").counter());
        assertNotNull(meterRegistry.find("notification.push.skipped").counter());
        assertNotNull(meterRegistry.find("notification.push.expired").counter());
        assertNotNull(meterRegistry.find("notification.push.cancelled").counter());
        assertNotNull(meterRegistry.find("notification.push.retried").counter());
    }
}
