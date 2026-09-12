package com.yantrago.api.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.config.OutboxCorrelationData;
import com.yantrago.api.service.NotificationMetrics;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox publisher — claims unpublished outbox rows and publishes them to the
 * NOTIFICATION_EXCHANGE. Uses bounded row claiming with lease expiry to allow
 * safe multi-instance operation.
 *
 * Per notification plan N5: outbox rows are committed with business state.
 * Publishers use bounded row claiming/leases, retry after failure, and mark
 * publication complete only after confirmation. A crash can republish, so
 * consumers must be idempotent.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final int BATCH_SIZE = 50;
    private static final long LEASE_DURATION_SECONDS = 30;
    private static final long[] RETRY_DELAY_SECONDS = {30, 120, 600}; // 30s, 2m, 10m
    private static final long CONFIRM_TIMEOUT_SECONDS = 10; // wait up to 10s for broker confirm

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationMetrics notificationMetrics;

    public OutboxPublisher(JdbcTemplate jdbcTemplate,
                            RabbitTemplate rabbitTemplate,
                            ObjectMapper objectMapper,
                            NotificationMetrics notificationMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.notificationMetrics = notificationMetrics;
    }

    /**
     * Claims and publishes unpublished outbox rows every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPending() {
        List<UUID> claimedIds = claimUnpublishedRows();
        if (claimedIds.isEmpty()) {
            return;
        }

        log.debug("Claimed {} outbox rows for publishing", claimedIds.size());

        for (UUID outboxId : claimedIds) {
            try {
                publishRow(outboxId);
            } catch (Exception e) {
                log.error("Failed to publish outbox row {}: {}", outboxId, e.getMessage(), e);
                // The lease will expire and the row will be retried on the next cycle
            }
        }
    }

    /**
     * Claims up to BATCH_SIZE unpublished rows whose next_attempt_at has passed.
     * Sets a lease to prevent concurrent publishers from picking the same rows.
     *
     * SIG 27: rows whose claim_lease_until has expired (previously claimed by a
     * crashed worker) are counted as lease recoveries and recorded as a metric.
     */
    private List<UUID> claimUnpublishedRows() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime leaseUntil = now.plusSeconds(LEASE_DURATION_SECONDS);

        // Atomically claim rows by setting claim_lease_until.
        // Returns the old claim_lease_until so we can detect lease recoveries
        // (rows where old_lease_until is NOT NULL were previously claimed by a
        // worker that crashed before completing).
        // Uses CTE with ORDER BY for deterministic, reliable row claiming
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "WITH claimable AS (" +
                        "  SELECT id, claim_lease_until FROM event_outbox" +
                        "  WHERE published_at IS NULL" +
                        "    AND next_attempt_at <= ?" +
                        "    AND (claim_lease_until IS NULL OR claim_lease_until <= ?)" +
                        "  ORDER BY next_attempt_at" +
                        "  LIMIT ?" +
                        "  FOR UPDATE SKIP LOCKED" +
                        ") UPDATE event_outbox SET claim_lease_until = ?" +
                        "  FROM claimable WHERE event_outbox.id = claimable.id" +
                        "  RETURNING event_outbox.id, claimable.claim_lease_until AS old_lease_until",
                now, now, BATCH_SIZE, leaseUntil
        );

        List<UUID> ids = new ArrayList<>(rows.size());
        int recoveredCount = 0;
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            ids.add(id);
            // If old_lease_until is not null, the row was previously claimed by
            // a worker whose lease expired — this is a lease recovery.
            if (row.get("old_lease_until") != null) {
                recoveredCount++;
            }
        }

        if (recoveredCount > 0) {
            log.info("Recovered {} outbox rows with expired leases (previously claimed by crashed worker)",
                    recoveredCount);
            for (int i = 0; i < recoveredCount; i++) {
                notificationMetrics.recordLeaseRecovery();
            }
        }

        return ids;
    }

    /**
     * Publishes a single outbox row to the notification exchange.
     * Marks the row as published on success, or increments attempts on failure.
     */
    private void publishRow(UUID outboxId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, organization_id, event_type, schema_version, aggregate_id, " +
                        "event_id, payload, publish_attempts, max_attempts, created_at " +
                        "FROM event_outbox WHERE id = ?",
                outboxId
        );

        UUID eventId = (UUID) row.get("event_id");
        String eventType = (String) row.get("event_type");
        int attempts = ((Number) row.get("publish_attempts")).intValue();
        int maxAttempts = ((Number) row.get("max_attempts")).intValue();
        String payloadJson = row.get("payload").toString();
        // SIG 27: extract created_at for outbox age metric
        LocalDateTime createdAt = ((java.sql.Timestamp) row.get("created_at")).toLocalDateTime();

        try {
            // Parse the payload as a generic JSON node and publish
            JsonNode payload = objectMapper.readTree(payloadJson);

            // Phase 1 fix: use correlation data for publisher confirms
            OutboxCorrelationData correlationData = new OutboxCorrelationData(outboxId);
            rabbitTemplate.convertAndSend(
                    QueueNames.NOTIFICATION_EXCHANGE,
                    QueueNames.NOTIFICATION_ROUTING_KEY,
                    payload,
                    correlationData
            );

            // Wait for broker confirm before marking as published
            boolean confirmed = correlationData.awaitConfirm(CONFIRM_TIMEOUT_SECONDS);

            if (confirmed) {
                // Mark as published only after broker confirmation
                LocalDateTime publishedAt = LocalDateTime.now(ZoneOffset.UTC);
                jdbcTemplate.update(
                        "UPDATE event_outbox SET published_at = ?, claim_lease_until = NULL " +
                                "WHERE id = ?",
                        publishedAt, outboxId
                );
                // SIG 27: record outbox row age at publish time
                long ageSeconds = ChronoUnit.SECONDS.between(createdAt, publishedAt);
                notificationMetrics.recordOutboxAge(ageSeconds);
                log.info("Published outbox row id={} eventId={} type={} age={}s (confirmed by broker)",
                        outboxId, eventId, eventType, ageSeconds);
            } else {
                // Broker did not confirm — treat as failure and schedule retry
                throw new RuntimeException("Broker did not confirm publication within " +
                        CONFIRM_TIMEOUT_SECONDS + "s");
            }

        } catch (Exception e) {
            log.error("Failed to publish outbox row id={} eventId={}: {}", outboxId, eventId, e.getMessage(), e);

            int newAttempts = attempts + 1;
            if (newAttempts >= maxAttempts) {
                // Exhausted retries — mark as permanently failed but keep the row for diagnostics
                jdbcTemplate.update(
                        "UPDATE event_outbox SET publish_attempts = ?, next_attempt_at = NULL, " +
                                "claim_lease_until = NULL WHERE id = ?",
                        newAttempts, outboxId
                );
                log.error("Outbox row id={} exhausted {} publish attempts — marked as failed", outboxId, maxAttempts);
            } else {
                // Schedule retry with exponential backoff
                long delaySeconds = RETRY_DELAY_SECONDS[Math.min(attempts, RETRY_DELAY_SECONDS.length - 1)];
                LocalDateTime nextAttempt = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds);
                jdbcTemplate.update(
                        "UPDATE event_outbox SET publish_attempts = ?, next_attempt_at = ?, " +
                                "claim_lease_until = NULL WHERE id = ?",
                        newAttempts, nextAttempt, outboxId
                );
                log.warn("Outbox row id={} scheduled for retry #{} in {}s", outboxId, newAttempts, delaySeconds);
            }
        }
    }
}
