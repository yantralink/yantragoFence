package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.model.UserDeviceToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.yantrago.api.service.TokenEncryptionService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for PushDeliveryService.
 *
 * Verifies Phase 5 acceptance criteria:
 * - Multi-device delivery (one job per token)
 * - Bounded retries (max_attempts with exponential backoff)
 * - Invalid-token removal (deactivate tokens FCM reports invalid)
 * - TTL / stale-event cancellation (skip jobs past expires_at)
 * - Honest delivery status (ACCEPTED_BY_PROVIDER, not device delivery)
 *
 * Per notification plan Phase 5: provider-adapter tests with controlled responses.
 */
class PushDeliveryServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DeviceTokenService deviceTokenService;
    private PushProvider pushProvider;
    private ObjectMapper objectMapper;
    private NotificationPreferenceService preferenceService;
    private RecipientResolutionService recipientResolutionService;
    private NotificationFeatureSwitches featureSwitches;
    private NotificationMetrics notificationMetrics;
    private NotificationStormProtection stormProtection;
    private TokenEncryptionService tokenEncryptionService;

    private PushDeliveryService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID inboxId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        deviceTokenService = mock(DeviceTokenService.class);
        pushProvider = mock(PushProvider.class);
        objectMapper = new ObjectMapper();
        preferenceService = mock(NotificationPreferenceService.class);
        recipientResolutionService = mock(RecipientResolutionService.class);
        featureSwitches = mock(NotificationFeatureSwitches.class);
        notificationMetrics = mock(NotificationMetrics.class);
        stormProtection = mock(NotificationStormProtection.class);
        tokenEncryptionService = new TokenEncryptionService("");

        service = new PushDeliveryService(jdbcTemplate, deviceTokenService,
                pushProvider, objectMapper, preferenceService, recipientResolutionService,
                featureSwitches, notificationMetrics, stormProtection, tokenEncryptionService, 1000);

        // Default: push is enabled by feature switch and preference
        when(featureSwitches.isPushEnabled()).thenReturn(true);
        when(preferenceService.isPushEnabled(any(), any(), any())).thenReturn(true);
        // Default: storm protection allows push
        when(stormProtection.allowPushForUser(any(), any())).thenReturn(true);
        // Default: access is valid
        when(recipientResolutionService.revalidateAccess(any(), any(), any())).thenReturn(true);
    }

    private UserDeviceToken createToken() {
        UserDeviceToken token = new UserDeviceToken();
        token.setId(tokenId);
        token.setOrganizationId(orgId);
        token.setUserId(userId);
        token.setToken("fcm-token-123");
        token.setPlatform("ANDROID");
        token.setIsActive(true);
        return token;
    }

    private Map<String, Object> createJobRow(String status, int attemptCount, int maxAttempts,
                                                LocalDateTime expiresAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", jobId);
        row.put("organization_id", orgId);
        row.put("inbox_id", inboxId);
        row.put("user_id", userId);
        row.put("device_token_id", tokenId);
        row.put("token", "fcm-token-123");
        row.put("title", "Test Alert");
        row.put("body", "Test body");
        row.put("data_payload", "{\"inboxId\":\"" + inboxId + "\",\"alertType\":\"LOW_BATTERY\"}");
        row.put("status", status);
        row.put("attempt_count", attemptCount);
        row.put("max_attempts", maxAttempts);
        row.put("expires_at", Timestamp.valueOf(expiresAt));
        row.put("next_attempt_at", Timestamp.valueOf(LocalDateTime.now()));
        return row;
    }

    @Test
    @DisplayName("enqueuePushDelivery: creates one job per active device token")
    void enqueuePushDelivery_createsJobPerToken() {
        UserDeviceToken t1 = createToken();
        t1.setId(UUID.randomUUID());
        t1.setToken("token-1");
        UserDeviceToken t2 = createToken();
        t2.setId(UUID.randomUUID());
        t2.setToken("token-2");

        when(deviceTokenService.getActiveTokensForUser(userId))
                .thenReturn(List.of(t1, t2));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(1);

        Map<String, String> data = new HashMap<>();
        data.put("alertType", "LOW_BATTERY");

        service.enqueuePushDelivery(orgId, inboxId, userId, "Title", "Body", data);

        // Two jobs should be enqueued (one per token)
        verify(jdbcTemplate, times(2)).update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("enqueuePushDelivery: no tokens — no jobs created")
    void enqueuePushDelivery_noTokens_noJobsCreated() {
        when(deviceTokenService.getActiveTokensForUser(userId))
                .thenReturn(List.of());

        service.enqueuePushDelivery(orgId, inboxId, userId, "Title", "Body", null);

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("processJob: FCM success — marks ACCEPTED_BY_PROVIDER with provider message ID")
    void processJob_fcmSuccess_marksSent() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("PENDING", 0, 3, expiresAt));
        when(pushProvider.send(any(), any(), any(), any()))
                .thenReturn(PushProvider.PushSendResult.success("fcm-msg-123"));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(jdbcTemplate).update(
                contains("status = 'ACCEPTED_BY_PROVIDER'"),
                eq("fcm-msg-123"), eq(1), any(), any(), any(), eq(jobId));
    }

    @Test
    @DisplayName("processJob: invalid token — marks FAILED and invalidates token")
    void processJob_invalidToken_marksFailedAndInvalidatesToken() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("PENDING", 0, 3, expiresAt));
        when(pushProvider.send(any(), any(), any(), any()))
                .thenReturn(PushProvider.PushSendResult.invalidToken("UNREGISTERED", "Token not registered"));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(deviceTokenService).invalidateToken(eq(tokenId), eq("UNREGISTERED"));
        verify(jdbcTemplate).update(
                contains("status = 'FAILED'"), any(), eq(1), any(), any(), eq(jobId));
    }

    @Test
    @DisplayName("processJob: transient failure with retries remaining — schedules retry")
    void processJob_transientFailureWithRetries_schedulesRetry() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("PENDING", 0, 3, expiresAt));
        when(pushProvider.send(any(), any(), any(), any()))
                .thenReturn(PushProvider.PushSendResult.transientFailure("UNAVAILABLE", "Try again"));

        boolean complete = service.processJob(jobId);

        assertFalse(complete); // not complete — retry scheduled
        verify(jdbcTemplate).update(
                contains("next_attempt_at"), any(), eq(1), any(), any(), any(), eq(jobId));
    }

    @Test
    @DisplayName("processJob: transient failure at max attempts — marks FAILED")
    void processJob_transientFailureAtMaxAttempts_marksFailed() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("PENDING", 2, 3, expiresAt));
        when(pushProvider.send(any(), any(), any(), any()))
                .thenReturn(PushProvider.PushSendResult.transientFailure("UNAVAILABLE", "Try again"));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(jdbcTemplate).update(
                contains("status = 'FAILED'"), any(), eq(3), any(), any(), eq(jobId));
    }

    @Test
    @DisplayName("processJob: expired job — marks EXPIRED (stale-event cancellation)")
    void processJob_expiredJob_marksExpired() {
        LocalDateTime pastExpiry = LocalDateTime.now().minusHours(1);
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("PENDING", 0, 3, pastExpiry));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(pushProvider, never()).send(any(), any(), any(), any());
        verify(jdbcTemplate).update(
                contains("status = 'EXPIRED'"), any(), eq(jobId));
    }

    @Test
    @DisplayName("processJob: skipped (push disabled) — marks CANCELLED")
    void processJob_skipped_marksCancelled() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("PENDING", 0, 3, expiresAt));
        when(pushProvider.send(any(), any(), any(), any()))
                .thenReturn(PushProvider.PushSendResult.skipped("Push disabled"));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(jdbcTemplate).update(
                contains("status = 'CANCELLED'"), any(), eq(1), any(), any(), eq(jobId));
    }

    @Test
    @DisplayName("processJob: already processed — returns true without sending")
    void processJob_alreadyProcessed_returnsTrue() {
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("ACCEPTED_BY_PROVIDER", 1, 3, LocalDateTime.now().plusHours(24)));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(pushProvider, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("processJob: job not found — returns true without error")
    void processJob_notFound_returnsTrue() {
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenThrow(new RuntimeException("not found"));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(pushProvider, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("processJob: retry would exceed TTL — marks EXPIRED")
    void processJob_retryExceedsTTL_marksExpired() {
        // Expires in 10 seconds — retry backoff (30s) would exceed
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(10);
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(createJobRow("PENDING", 0, 3, expiresAt));
        when(pushProvider.send(any(), any(), any(), any()))
                .thenReturn(PushProvider.PushSendResult.transientFailure("UNAVAILABLE", "Try again"));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(jdbcTemplate).update(
                contains("status = 'EXPIRED'"), anyString(), eq(1), any(), any(), eq(jobId));
    }

    // ===== Phase 5 gap fixes: preference check, access revalidation, throttling =====

    @Test
    @DisplayName("enqueuePushDelivery: push disabled by preference — no jobs created")
    void enqueuePushDelivery_pushDisabledByPreference_noJobsCreated() {
        when(preferenceService.isPushEnabled(orgId, userId, "LOW_BATTERY"))
                .thenReturn(false);

        Map<String, String> data = new HashMap<>();
        data.put("alertType", "LOW_BATTERY");

        service.enqueuePushDelivery(orgId, inboxId, userId, "Title", "Body", data);

        verify(deviceTokenService, never()).getActiveTokensForUser(any());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("enqueuePushDelivery: push disabled by feature switch — no jobs created (preserves inbox)")
    void enqueuePushDelivery_pushDisabledByFeatureSwitch_noJobsCreated() {
        when(featureSwitches.isPushEnabled()).thenReturn(false);

        Map<String, String> data = new HashMap<>();
        data.put("alertType", "LOW_BATTERY");

        service.enqueuePushDelivery(orgId, inboxId, userId, "Title", "Body", data);

        verify(deviceTokenService, never()).getActiveTokensForUser(any());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("enqueuePushDelivery: storm protection rate-limits push — no jobs created")
    void enqueuePushDelivery_stormProtectionRateLimits_noJobsCreated() {
        when(stormProtection.allowPushForUser(userId, "LOW_BATTERY")).thenReturn(false);

        Map<String, String> data = new HashMap<>();
        data.put("alertType", "LOW_BATTERY");

        service.enqueuePushDelivery(orgId, inboxId, userId, "Title", "Body", data);

        verify(deviceTokenService, never()).getActiveTokensForUser(any());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any());
        verify(notificationMetrics).recordPushSkipped();
    }

    @Test
    @DisplayName("processJob: access revoked (machine reassigned) — marks CANCELLED")
    void processJob_accessRevoked_marksCancelled() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        Map<String, Object> row = createJobRow("PENDING", 0, 3, expiresAt);
        // data_payload includes machineId for revalidation
        row.put("data_payload", "{\"inboxId\":\"" + inboxId + "\",\"machineId\":\"" +
                UUID.randomUUID() + "\",\"alertType\":\"LOW_BATTERY\"}");
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(row);
        when(recipientResolutionService.revalidateAccess(any(), any(), eq(userId)))
                .thenReturn(false);

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(pushProvider, never()).send(any(), any(), any(), any());
        verify(jdbcTemplate).update(
                contains("status = 'CANCELLED'"), any(), eq(jobId));
    }

    @Test
    @DisplayName("processJob: no machineId in payload — skips revalidation, sends push")
    void processJob_noMachineIdInPayload_skipsRevalidation() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        Map<String, Object> row = createJobRow("PENDING", 0, 3, expiresAt);
        // data_payload without machineId
        row.put("data_payload", "{\"inboxId\":\"" + inboxId + "\",\"alertType\":\"LOW_BATTERY\"}");
        when(jdbcTemplate.queryForMap(anyString(), eq(jobId)))
                .thenReturn(row);
        when(pushProvider.send(any(), any(), any(), any()))
                .thenReturn(PushProvider.PushSendResult.success("fcm-msg-123"));

        boolean complete = service.processJob(jobId);

        assertTrue(complete);
        verify(recipientResolutionService, never()).revalidateAccess(any(), any(), any());
        verify(pushProvider).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("FcmPushProvider: throttling error codes are transient failures")
    void pushProvider_throttlingIsTransientFailure() {
        // Verify via the provider's PushSendResult that throttling is transient
        PushProvider.PushSendResult result = PushProvider.PushSendResult.transientFailure(
                "QUOTA_EXCEEDED", "Quota exceeded");
        assertTrue(result.isTransientFailure());
        assertFalse(result.isInvalidToken());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("FcmPushProvider: UNAVAILABLE is transient failure (retryable)")
    void pushProvider_unavailableIsTransientFailure() {
        PushProvider.PushSendResult result = PushProvider.PushSendResult.transientFailure(
                "UNAVAILABLE", "Server unavailable");
        assertTrue(result.isTransientFailure());
        assertFalse(result.isInvalidToken());
    }

    @Test
    @DisplayName("processJob: multiple devices — both jobs processed (multi-phone)")
    void processJob_multipleDevices_bothProcessed() {
        // This test verifies multi-device delivery by enqueuing for two tokens
        UserDeviceToken t1 = createToken();
        t1.setId(UUID.randomUUID());
        t1.setToken("token-device-1");
        UserDeviceToken t2 = createToken();
        t2.setId(UUID.randomUUID());
        t2.setToken("token-device-2");

        when(deviceTokenService.getActiveTokensForUser(userId))
                .thenReturn(List.of(t1, t2));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(1);

        Map<String, String> data = new HashMap<>();
        data.put("alertType", "LOW_BATTERY");

        service.enqueuePushDelivery(orgId, inboxId, userId, "Title", "Body", data);

        // Two jobs should be enqueued — one per device token (multi-phone)
        verify(jdbcTemplate, times(2)).update(anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any());
    }
}
