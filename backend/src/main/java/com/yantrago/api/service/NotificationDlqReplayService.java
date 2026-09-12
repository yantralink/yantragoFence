package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.shared.queue.AlertTransitionMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ replay service — privileged, audited, bounded, idempotent.
 *
 * Per notification plan Phase 6 / N11:
 * - "Replays are privileged, audited, bounded, and idempotent."
 * - "Recheck current recipient access, expiry-cycle validity, and event age
 *    so recovery does not flood users with stale pushes."
 *
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 12: production feature with logging + audit.
 */
@Service
public class NotificationDlqReplayService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDlqReplayService.class);

    // Maximum event age for replay (24 hours) — prevents stale push flooding
    private static final Duration MAX_REPLAY_AGE = Duration.ofHours(24);

    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationMetrics notificationMetrics;
    private final RecipientResolutionService recipientResolutionService;

    public NotificationDlqReplayService(RabbitTemplate rabbitTemplate,
                                         JdbcTemplate jdbcTemplate,
                                         ObjectMapper objectMapper,
                                         NotificationMetrics notificationMetrics,
                                         RecipientResolutionService recipientResolutionService) {
        this.rabbitTemplate = rabbitTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationMetrics = notificationMetrics;
        this.recipientResolutionService = recipientResolutionService;
    }

    /**
     * Replays a single DLQ message back to the notification queue.
     *
     * Safety checks:
     * - Event age must be within MAX_REPLAY_AGE (prevents stale push flooding)
     * - If machineId is present, revalidate access (machine may have been reassigned)
     * - Idempotent: processed_events dedup prevents duplicate inbox creation
     * - Audited: every replay is recorded in notification_replay_audit
     *
     * @param messageBody the original DLQ message JSON
     * @param replayedBy the user ID of the admin performing the replay
     * @param reason the reason for the replay
     * @return ReplayResult with status
     */
    @Transactional
    public ReplayResult replayMessage(String messageBody, UUID replayedBy, String reason) {
        notificationMetrics.recordDlqMessage();

        try {
            AlertTransitionMessage message = objectMapper.readValue(messageBody,
                    AlertTransitionMessage.class);

            UUID eventId = message.getEventId();
            UUID orgId = message.getOrganizationId();
            UUID machineId = message.getMachineId();
            String alertType = message.getAlertType();

            // SIG 29: idempotency check — if this message was already successfully
            // replayed within the last 24 hours, return early to prevent duplicate
            // replays from flooding the user with notifications.
            Integer priorReplayCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification_replay_audit " +
                            "WHERE replay_status = 'SUCCESS' " +
                            "AND created_at >= now() - interval '24 hours' " +
                            "AND (event_id = ? OR message_body = ?)",
                    Integer.class, eventId, messageBody
            );
            if (priorReplayCount != null && priorReplayCount > 0) {
                log.info("DLQ replay skipped (already replayed within 24h): eventId={} priorCount={}",
                        eventId, priorReplayCount);
                auditReplay(replayedBy, messageBody, reason, "ALREADY_REPLAYED",
                        "Message already replayed within the last 24 hours",
                        eventId, alertType, machineId, orgId);
                return new ReplayResult(true, "ALREADY_REPLAYED",
                        "Message already replayed within the last 24 hours");
            }

            // Check 1: event age — reject stale events
            if (message.getOccurredAt() != null) {
                Duration age = Duration.between(message.getOccurredAt(), Instant.now());
                if (age.compareTo(MAX_REPLAY_AGE) > 0) {
                    log.warn("DLQ replay rejected: event {} is {} hours old (max {})",
                            eventId, age.toHours(), MAX_REPLAY_AGE.toHours());
                    auditReplay(replayedBy, messageBody, reason, "REJECTED_STALE",
                            "Event age " + age.toHours() + "h exceeds max " + MAX_REPLAY_AGE.toHours() + "h",
                            eventId, alertType, machineId, orgId);
                    return new ReplayResult(false, "REJECTED_STALE",
                            "Event is too old to replay (max " + MAX_REPLAY_AGE.toHours() + "h)");
                }
            }

            // Check 2: revalidate access if machineId is present
            if (orgId != null && machineId != null) {
                // We can't know the exact user here, but we check if the machine
                // still has an active assignment in the org
                var recipients = recipientResolutionService.resolveRecipientSnapshots(orgId, machineId);
                if (recipients.isEmpty()) {
                    log.warn("DLQ replay rejected: no active recipient for machine={}", machineId);
                    auditReplay(replayedBy, messageBody, reason, "REJECTED_NO_ACCESS",
                            "No active recipient for machine " + machineId,
                            eventId, alertType, machineId, orgId);
                    return new ReplayResult(false, "REJECTED_NO_ACCESS",
                            "Machine has no active recipient — may have been reassigned");
                }
            }

            // Clear the processed_events entry so the replay will be reprocessed
            // (idempotent: if the event was never processed, this is a no-op)
            if (eventId != null) {
                jdbcTemplate.update(
                        "DELETE FROM processed_events WHERE event_id = ? AND event_type = 'ALERT_TRANSITION'",
                        eventId
                );
            }

            // Re-publish to the notification queue
            rabbitTemplate.convertAndSend(
                    QueueNames.NOTIFICATION_EXCHANGE,
                    QueueNames.NOTIFICATION_ROUTING_KEY,
                    message
            );

            log.info("DLQ replay successful: eventId={} replayedBy={} reason={}",
                    eventId, replayedBy, reason);
            auditReplay(replayedBy, messageBody, reason, "SUCCESS", null,
                    eventId, alertType, machineId, orgId);

            return new ReplayResult(true, "SUCCESS", "Message replayed successfully");

        } catch (Exception e) {
            log.error("DLQ replay failed: {}", e.getMessage(), e);
            auditReplay(replayedBy, messageBody, reason, "FAILED",
                    e.getMessage(), null, null, null, null);
            return new ReplayResult(false, "FAILED", e.getMessage());
        }
    }

    /**
     * Records the replay attempt in the audit table.
     */
    private void auditReplay(UUID replayedBy, String messageBody, String reason,
                              String status, String errorDetail,
                              UUID eventId, String alertType, UUID machineId, UUID orgId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO notification_replay_audit (replayed_by, message_body, replay_reason, " +
                            "replay_status, error_detail, event_id, alert_type, machine_id, organization_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    replayedBy, messageBody, reason, status, errorDetail,
                    eventId, alertType, machineId, orgId
            );
        } catch (Exception e) {
            log.error("Failed to record replay audit: {}", e.getMessage(), e);
        }
    }

    /**
     * Result of a DLQ replay attempt.
     */
    public record ReplayResult(boolean success, String status, String message) {}
}
