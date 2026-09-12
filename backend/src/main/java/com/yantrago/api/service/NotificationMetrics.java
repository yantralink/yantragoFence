package com.yantrago.api.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Notification pipeline metrics (Phase 6).
 *
 * Per notification plan N11:
 * - alert transitions by type
 * - duplicate inputs suppressed
 * - recipients excluded by reason
 * - inbox creation latency
 * - provider acceptance/failure
 * - invalid tokens
 * - worker lease recovery
 * - no-recipient rate
 * - event family skipped
 *
 * Per AGENTS.md rule 12: production feature with logging.
 * Per N11: avoid user/IMEI/token IDs as metric labels; use eventId/alertId
 * for correlation in structured logs, not as metric tags.
 */
@Service
public class NotificationMetrics {

    private static final Logger log = LoggerFactory.getLogger(NotificationMetrics.class);

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter alertTransitions;
    private final Counter duplicatesSuppressed;
    private final Counter recipientsExcluded;
    private final Counter noRecipientEvents;
    private final Counter eventFamilySkipped;
    private final Counter pushAccepted;
    private final Counter pushFailed;
    private final Counter pushInvalidToken;
    private final Counter pushSkipped;
    private final Counter pushExpired;
    private final Counter pushCancelled;
    private final Counter pushRetried;
    private final Counter dlqMessages;

    // Timers
    private final Timer inboxCreationLatency;
    private final Timer pushDeliveryLatency;

    // SIG 27: additional metrics
    private final Timer outboxAgeTimer;
    private final Counter leaseRecoveryCounter;
    private final AtomicInteger queueDepthGauge;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.alertTransitions = Counter.builder("notification.alert.transitions")
                .description("Alert transitions received by type and state")
                .register(meterRegistry);

        this.duplicatesSuppressed = Counter.builder("notification.duplicates.suppressed")
                .description("Duplicate inbox items suppressed by dedup")
                .register(meterRegistry);

        this.recipientsExcluded = Counter.builder("notification.recipients.excluded")
                .description("Recipients excluded by reason")
                .register(meterRegistry);

        this.noRecipientEvents = Counter.builder("notification.no.recipient")
                .description("Events with no eligible recipient")
                .register(meterRegistry);

        this.eventFamilySkipped = Counter.builder("notification.event.family.skipped")
                .description("Events skipped by feature switch")
                .register(meterRegistry);

        this.pushAccepted = Counter.builder("notification.push.accepted")
                .description("Push messages accepted by provider (ACCEPTED_BY_PROVIDER)")
                .register(meterRegistry);

        this.pushFailed = Counter.builder("notification.push.failed")
                .description("Push messages that failed permanently")
                .register(meterRegistry);

        this.pushInvalidToken = Counter.builder("notification.push.invalid.token")
                .description("Push tokens reported invalid by provider")
                .register(meterRegistry);

        this.pushSkipped = Counter.builder("notification.push.skipped")
                .description("Push messages skipped (provider disabled or no token)")
                .register(meterRegistry);

        this.pushExpired = Counter.builder("notification.push.expired")
                .description("Push jobs expired (stale-event cancellation)")
                .register(meterRegistry);

        this.pushCancelled = Counter.builder("notification.push.cancelled")
                .description("Push jobs cancelled (access revoked or push disabled)")
                .register(meterRegistry);

        this.pushRetried = Counter.builder("notification.push.retried")
                .description("Push jobs retried after transient failure")
                .register(meterRegistry);

        this.dlqMessages = Counter.builder("notification.dlq.messages")
                .description("Messages sent to dead-letter queue")
                .register(meterRegistry);

        this.inboxCreationLatency = Timer.builder("notification.inbox.creation.latency")
                .description("Time to create inbox item from event receipt")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.pushDeliveryLatency = Timer.builder("notification.push.delivery.latency")
                .description("Time from inbox creation to provider acceptance")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // SIG 27: outbox row age when published
        this.outboxAgeTimer = Timer.builder("notification.outbox.age")
                .description("Age of outbox rows at time of successful publish")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // SIG 27: recovered expired leases counter
        this.leaseRecoveryCounter = Counter.builder("notification.outbox.lease.recovered")
                .description("Outbox rows whose expired claim lease was recovered by a worker")
                .register(meterRegistry);

        // SIG 27: notification queue depth gauge (updated by health indicator)
        this.queueDepthGauge = new AtomicInteger(0);
        Gauge.builder("notification.queue.depth", queueDepthGauge, AtomicInteger::doubleValue)
                .description("Current notification queue depth (message count)")
                .register(meterRegistry);
    }

    public void recordAlertTransition(String alertType, String incidentState) {
        Counter.builder("notification.alert.transitions")
                .tag("type", safeTag(alertType))
                .tag("state", safeTag(incidentState))
                .register(meterRegistry)
                .increment();
    }

    public void recordDuplicateSuppressed(String alertType) {
        duplicatesSuppressed.increment();
        Counter.builder("notification.duplicates.suppressed")
                .tag("type", safeTag(alertType))
                .register(meterRegistry)
                .increment();
    }

    public void recordRecipientExcluded(String reason) {
        recipientsExcluded.increment();
        Counter.builder("notification.recipients.excluded")
                .tag("reason", safeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    public void recordNoRecipient(String alertType) {
        noRecipientEvents.increment();
        Counter.builder("notification.no.recipient")
                .tag("type", safeTag(alertType))
                .register(meterRegistry)
                .increment();
    }

    public void recordEventFamilySkipped(String alertType) {
        eventFamilySkipped.increment();
        Counter.builder("notification.event.family.skipped")
                .tag("type", safeTag(alertType))
                .register(meterRegistry)
                .increment();
    }

    public void recordPushAccepted() {
        pushAccepted.increment();
    }

    public void recordPushFailed() {
        pushFailed.increment();
    }

    public void recordPushInvalidToken() {
        pushInvalidToken.increment();
    }

    public void recordPushSkipped() {
        pushSkipped.increment();
    }

    public void recordPushExpired() {
        pushExpired.increment();
    }

    public void recordPushCancelled() {
        pushCancelled.increment();
    }

    public void recordPushRetried() {
        pushRetried.increment();
    }

    public void recordDlqMessage() {
        dlqMessages.increment();
    }

    public void recordInboxCreationLatency(long nanos) {
        inboxCreationLatency.record(java.time.Duration.ofNanos(nanos));
    }

    public void recordPushDeliveryLatency(long nanos) {
        pushDeliveryLatency.record(java.time.Duration.ofNanos(nanos));
    }

    // ------------------------------------------------------------------
    // SIG 27: additional metrics
    // ------------------------------------------------------------------

    /**
     * Records the age of an outbox row at the time it was successfully
     * published. Called from OutboxPublisher.publishRow after broker
     * confirmation.
     *
     * @param ageSeconds the row age in seconds (from created_at to now)
     */
    public void recordOutboxAge(long ageSeconds) {
        outboxAgeTimer.record(java.time.Duration.ofSeconds(ageSeconds));
    }

    /**
     * Updates the notification queue depth gauge. Called from the
     * NotificationHealthIndicators queue depth check.
     *
     * @param depth the current queue depth (message count)
     */
    public void recordQueueDepth(int depth) {
        queueDepthGauge.set(depth);
    }

    /**
     * Increments the counter for outbox rows whose expired claim lease was
     * recovered by a worker. Called from OutboxPublisher.claimUnpublishedRows
     * when it claims rows that were previously claimed by a crashed worker.
     */
    public void recordLeaseRecovery() {
        leaseRecoveryCounter.increment();
    }

    /**
     * Sanitizes a tag value for Prometheus — avoids high-cardinality labels.
     * Per N11: avoid user/IMEI/token IDs as metric labels.
     */
    private String safeTag(String value) {
        if (value == null || value.isBlank()) return "unknown";
        // Limit length and sanitize
        String sanitized = value.replaceAll("[^a-zA-Z0-9_]", "_");
        return sanitized.length() > 50 ? sanitized.substring(0, 50) : sanitized;
    }
}
