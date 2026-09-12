package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.model.UserDeviceToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for enqueuing and processing push delivery jobs.
 *
 * Per notification plan Phase 5:
 * - Multi-device delivery jobs (one job per device token per inbox item)
 * - Bounded retries (max_attempts with exponential backoff)
 * - Invalid-token removal (deactivate tokens that FCM reports invalid)
 * - TTL / stale-event cancellation (skip jobs past expires_at)
 * - Honest delivery status (ACCEPTED_BY_PROVIDER, not device delivery)
 *
 * Per AGENTS.md rule 12: production feature with logging + error handling.
 */
@Service
public class PushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(PushDeliveryService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DeviceTokenService deviceTokenService;
    private final PushProvider pushProvider;
    private final ObjectMapper objectMapper;
    private final NotificationPreferenceService preferenceService;
    private final RecipientResolutionService recipientResolutionService;
    private final NotificationFeatureSwitches featureSwitches;
    private final NotificationMetrics notificationMetrics;
    private final NotificationStormProtection stormProtection;
    private final TokenEncryptionService tokenEncryptionService;
    private final int maxBacklogPerUser;

    public PushDeliveryService(JdbcTemplate jdbcTemplate,
                                DeviceTokenService deviceTokenService,
                                PushProvider pushProvider,
                                ObjectMapper objectMapper,
                                NotificationPreferenceService preferenceService,
                                RecipientResolutionService recipientResolutionService,
                                NotificationFeatureSwitches featureSwitches,
                                NotificationMetrics notificationMetrics,
                                NotificationStormProtection stormProtection,
                                TokenEncryptionService tokenEncryptionService,
                                @org.springframework.beans.factory.annotation.Value("${push.backlog.max-per-user:1000}") int maxBacklogPerUser) {
        this.jdbcTemplate = jdbcTemplate;
        this.deviceTokenService = deviceTokenService;
        this.pushProvider = pushProvider;
        this.objectMapper = objectMapper;
        this.preferenceService = preferenceService;
        this.recipientResolutionService = recipientResolutionService;
        this.featureSwitches = featureSwitches;
        this.notificationMetrics = notificationMetrics;
        this.stormProtection = stormProtection;
        this.tokenEncryptionService = tokenEncryptionService;
        this.maxBacklogPerUser = maxBacklogPerUser;
    }

    /**
     * Enqueues push delivery jobs for all active device tokens of a user.
     * Called by NotificationEventConsumer after creating an inbox item.
     *
     * Per Phase 5: one job per device token (multi-device support).
     * Per Phase 5: respects push_enabled preference per alert type.
     */
    @Transactional
    public void enqueuePushDelivery(UUID orgId, UUID inboxId, UUID userId,
                                      String title, String body,
                                      Map<String, String> dataPayload) {
        // Phase 6: global push kill switch (preserves inbox and incident processing)
        if (!featureSwitches.isPushEnabled()) {
            log.info("Push disabled by feature switch — skipping push delivery for inbox={}", inboxId);
            return;
        }

        // Check push preference for this alert type
        String alertType = dataPayload != null ? dataPayload.get("alertType") : null;
        if (!preferenceService.isPushEnabled(orgId, userId, alertType)) {
            log.info("Push disabled by preference for user={} alertType={} — skipping push", userId, alertType);
            return;
        }

        // Phase 6: storm protection — per-user rate limiting
        if (!stormProtection.allowPushForUser(userId, alertType)) {
            log.info("Push rate-limited by storm protection for user={} alertType={}", userId, alertType);
            notificationMetrics.recordPushSkipped();
            return;
        }

        // SIG 30: bounded backlog protection — if the user already has too many
        // pending push jobs, skip enqueueing to prevent unbounded queue growth.
        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM push_delivery_jobs WHERE user_id = ? AND status = 'PENDING'",
                Integer.class, userId
        );
        if (pendingCount != null && pendingCount > maxBacklogPerUser) {
            log.warn("Push backlog exceeded for user={} (pending={} max={}) — skipping enqueue",
                    userId, pendingCount, maxBacklogPerUser);
            notificationMetrics.recordPushSkipped();
            return;
        }

        List<UserDeviceToken> tokens = deviceTokenService.getActiveTokensForUser(userId);
        if (tokens.isEmpty()) {
            log.debug("No active device tokens for user={} — skipping push delivery", userId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int ttlSeconds = 86400; // 24-hour TTL for stale-event cancellation
        LocalDateTime expiresAt = now.plusSeconds(ttlSeconds);

        String dataJson;
        try {
            Map<String, String> data = dataPayload != null ? new HashMap<>(dataPayload) : new HashMap<>();
            data.put("inboxId", inboxId.toString());
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize push data payload: {}", e.getMessage());
            dataJson = "{\"inboxId\":\"" + inboxId + "\"}";
        }

        for (UserDeviceToken token : tokens) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO push_delivery_jobs (organization_id, inbox_id, user_id, " +
                                "device_token_id, token, title, body, data_payload, status, " +
                                "max_attempts, provider, ttl_seconds, expires_at, next_attempt_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', 3, 'FCM', ?, ?, ?)",
                        orgId, inboxId, userId, token.getId(), token.getToken(),
                        title, body, dataJson, ttlSeconds, expiresAt, now
                );
                log.debug("Enqueued push job for inbox={} user={} token={}", inboxId, userId, token.getId());
            } catch (Exception e) {
                log.error("Failed to enqueue push job for token={}: {}", token.getId(), e.getMessage(), e);
            }
        }

        log.info("Enqueued {} push delivery jobs for inbox={} user={}", tokens.size(), inboxId, userId);
    }

    /**
     * Processes a single push delivery job.
     * Returns true if the job is complete (sent, failed permanently, cancelled, or expired).
     *
     * Per Phase 5:
     * - Stale-event cancellation: skip if past expires_at
     * - Bounded retries: increment attempt_count, check max_attempts
     * - Invalid-token removal: deactivate token if FCM reports invalid
     * - Honest delivery status: record FCM message ID as ACCEPTED_BY_PROVIDER
     */
    @Transactional
    public boolean processJob(UUID jobId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                    "SELECT id, organization_id, inbox_id, user_id, device_token_id, token, " +
                            "title, body, data_payload, status, attempt_count, max_attempts, " +
                            "expires_at, next_attempt_at " +
                            "FROM push_delivery_jobs WHERE id = ?",
                    jobId
            );
        } catch (Exception e) {
            log.debug("Push job {} not found", jobId);
            return true;
        }

        String status = (String) row.get("status");
        if (!"PENDING".equals(status)) {
            log.debug("Push job {} already processed (status={})", jobId, status);
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = ((java.sql.Timestamp) row.get("expires_at")).toLocalDateTime();

        // Stale-event cancellation
        if (now.isAfter(expiresAt)) {
            jdbcTemplate.update(
                    "UPDATE push_delivery_jobs SET status = 'EXPIRED', updated_at = ? WHERE id = ?",
                    now, jobId
            );
            log.info("Push job {} expired (stale event cancellation)", jobId);
            notificationMetrics.recordPushExpired();
            return true;
        }

        UUID orgId = (UUID) row.get("organization_id");
        UUID userId = (UUID) row.get("user_id");
        UUID inboxId = (UUID) row.get("inbox_id");

        // Revalidate access at delivery time (reassigned machine suppression).
        // If the machine was reassigned to a different customer after the inbox
        // item was created, suppress the push to avoid notifying the wrong user.
        String machineIdStr = extractFromDataPayload(row, "machineId");
        if (machineIdStr != null) {
            try {
                UUID machineId = UUID.fromString(machineIdStr);
                if (!recipientResolutionService.revalidateAccess(orgId, machineId, userId)) {
                    jdbcTemplate.update(
                            "UPDATE push_delivery_jobs SET status = 'CANCELLED', " +
                                    "error = 'Access revoked — machine reassigned', updated_at = ? WHERE id = ?",
                            now, jobId
                    );
                    log.info("Push job {} cancelled (access revoked — machine reassigned)", jobId);
                    return true;
                }
            } catch (IllegalArgumentException e) {
                log.debug("Push job {} has invalid machineId in payload — skipping revalidation", jobId);
            }
        }

        UUID deviceTokenId = (UUID) row.get("device_token_id");
        // Phase 5 fix: decrypt token before sending to provider
        String encryptedToken = (String) row.get("token");
        String token = tokenEncryptionService.decrypt(encryptedToken);
        String title = (String) row.get("title");
        String body = (String) row.get("body");
        int attemptCount = (int) row.get("attempt_count");
        int maxAttempts = (int) row.get("max_attempts");

        // Parse data payload
        Map<String, String> data = new HashMap<>();
        try {
            String dataJson = row.get("data_payload").toString();
            Map<String, Object> parsed = objectMapper.readValue(dataJson, Map.class);
            for (Map.Entry<String, Object> e : parsed.entrySet()) {
                data.put(e.getKey(), String.valueOf(e.getValue()));
            }
        } catch (Exception e) {
            log.warn("Failed to parse push data payload for job={}: {}", jobId, e.getMessage());
        }

        // Send via push provider (Phase 5 fix: use interface, not concrete class)
        // SIG 28: time the provider send call and record delivery latency
        long sendStartNanos = System.nanoTime();
        PushProvider.PushSendResult result = pushProvider.send(token, title, body, data);
        long elapsedNanos = System.nanoTime() - sendStartNanos;
        notificationMetrics.recordPushDeliveryLatency(elapsedNanos);

        // Update token last_used_at
        jdbcTemplate.update(
                "UPDATE user_device_tokens SET last_used_at = ? WHERE id = ?",
                now, deviceTokenId
        );

        if (result.isSuccess()) {
            // FCM accepted the message — record provider message ID
            // SIG 23: use ACCEPTED_BY_PROVIDER status (honest delivery status —
            // the provider accepted the message, not that the device received it)
            jdbcTemplate.update(
                    "UPDATE push_delivery_jobs SET status = 'ACCEPTED_BY_PROVIDER', provider_message_id = ?, " +
                            "attempt_count = ?, last_attempt_at = ?, sent_at = ?, updated_at = ? WHERE id = ?",
                    result.providerMessageId(), attemptCount + 1, now, now, now, jobId
            );
            log.info("Push job {} sent (ACCEPTED_BY_PROVIDER) messageId={}", jobId, result.providerMessageId());
            notificationMetrics.recordPushAccepted();
            return true;
        }

        if (result.isInvalidToken()) {
            // Token is permanently invalid — remove it and mark job as failed
            deviceTokenService.invalidateToken(deviceTokenId, result.errorCode());
            jdbcTemplate.update(
                    "UPDATE push_delivery_jobs SET status = 'FAILED', error = ?, " +
                            "attempt_count = ?, last_attempt_at = ?, updated_at = ? WHERE id = ?",
                    result.errorMessage(), attemptCount + 1, now, now, jobId
            );
            log.warn("Push job {} failed (invalid token) — token {} removed", jobId, deviceTokenId);
            notificationMetrics.recordPushFailed();
            notificationMetrics.recordPushInvalidToken();
            return true;
        }

        if (result.isSkipped()) {
            jdbcTemplate.update(
                    "UPDATE push_delivery_jobs SET status = 'CANCELLED', error = ?, " +
                            "attempt_count = ?, last_attempt_at = ?, updated_at = ? WHERE id = ?",
                    result.errorMessage(), attemptCount + 1, now, now, jobId
            );
            log.info("Push job {} cancelled (push disabled)", jobId);
            notificationMetrics.recordPushSkipped();
            notificationMetrics.recordPushCancelled();
            return true;
        }

        // Transient failure — retry if attempts remain
        int newAttemptCount = attemptCount + 1;
        if (newAttemptCount >= maxAttempts) {
            jdbcTemplate.update(
                    "UPDATE push_delivery_jobs SET status = 'FAILED', error = ?, " +
                            "attempt_count = ?, last_attempt_at = ?, updated_at = ? WHERE id = ?",
                    result.errorMessage(), newAttemptCount, now, now, jobId
            );
            log.warn("Push job {} failed after {} attempts: {}", jobId, newAttemptCount, result.errorMessage());
            notificationMetrics.recordPushFailed();
            return true;
        }

        // Schedule retry with exponential backoff (30s, 2min, 10min)
        long backoffSeconds = (long) (30 * Math.pow(6, newAttemptCount - 1));
        LocalDateTime nextAttempt = now.plusSeconds(backoffSeconds);
        // Don't retry past expiry
        if (nextAttempt.isAfter(expiresAt)) {
            jdbcTemplate.update(
                    "UPDATE push_delivery_jobs SET status = 'EXPIRED', error = ?, " +
                            "attempt_count = ?, last_attempt_at = ?, updated_at = ? WHERE id = ?",
                    "Retry would exceed TTL", newAttemptCount, now, now, jobId
            );
            log.info("Push job {} expired (retry would exceed TTL)", jobId);
            notificationMetrics.recordPushExpired();
            return true;
        }

        jdbcTemplate.update(
                "UPDATE push_delivery_jobs SET error = ?, attempt_count = ?, " +
                        "last_attempt_at = ?, next_attempt_at = ?, updated_at = ? WHERE id = ?",
                result.errorMessage(), newAttemptCount, now, nextAttempt, now, jobId
        );
        log.info("Push job {} retry scheduled (attempt {} of {}) at {}",
                jobId, newAttemptCount, maxAttempts, nextAttempt);
        notificationMetrics.recordPushRetried();
        return false;
    }

    /**
     * Extracts a string value from the data_payload JSONB column.
     */
    @SuppressWarnings("unchecked")
    private String extractFromDataPayload(Map<String, Object> row, String key) {
        try {
            Object dataPayload = row.get("data_payload");
            if (dataPayload == null) return null;
            String dataJson = dataPayload.toString();
            Map<String, Object> parsed = objectMapper.readValue(dataJson, Map.class);
            Object value = parsed.get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
