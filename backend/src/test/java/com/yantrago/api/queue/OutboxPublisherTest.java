package com.yantrago.api.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.config.OutboxCorrelationData;
import com.yantrago.api.service.NotificationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for OutboxPublisher.
 *
 * Per notification plan Phase 1 (N5):
 * - Outbox rows are claimed with bounded leases for multi-instance safety.
 * - Publication is marked complete only after broker confirmation.
 * - Failures schedule retries with exponential backoff.
 * - Exhausted retries mark the row as permanently failed.
 * - Lease recoveries (rows from crashed workers) are counted as metrics.
 *
 * Per AGENTS.md rule 12: production feature with tests.
 */
class OutboxPublisherTest {

    private JdbcTemplate jdbcTemplate;
    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private NotificationMetrics notificationMetrics;

    private OutboxPublisher publisher;

    private final UUID outboxId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID aggregateId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate = mock(JdbcTemplate.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        notificationMetrics = mock(NotificationMetrics.class);

        publisher = new OutboxPublisher(jdbcTemplate, rabbitTemplate,
                objectMapper, notificationMetrics);

        // Default: objectMapper returns a mock JSON node
        JsonNode mockNode = mock(JsonNode.class);
        when(objectMapper.readTree(anyString())).thenReturn(mockNode);
    }

    private Map<String, Object> createOutboxRow(int attempts, int maxAttempts) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", outboxId);
        row.put("organization_id", orgId);
        row.put("event_type", "ALERT_TRANSITION");
        row.put("schema_version", 1);
        row.put("aggregate_id", aggregateId);
        row.put("event_id", eventId);
        row.put("payload", "{\"eventId\":\"" + eventId + "\"}");
        row.put("publish_attempts", attempts);
        row.put("max_attempts", maxAttempts);
        row.put("created_at", Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(60)));
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> createClaimResult(boolean withExpiredLease) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", outboxId);
        if (withExpiredLease) {
            row.put("old_lease_until", Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(30)));
        } else {
            row.put("old_lease_until", null);
        }
        return List.of(row);
    }

    @SuppressWarnings("unchecked")
    private void mockClaim(List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(rows);
    }

    @Test
    @DisplayName("publishPending: does nothing when no rows are claimable")
    void publishPending_noClaimableRows_doesNothing() {
        mockClaim(List.of());

        publisher.publishPending();

        verify(rabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), any(Object.class), any(OutboxCorrelationData.class));
        verify(jdbcTemplate, never()).update(anyString(), any(LocalDateTime.class), any(UUID.class));
    }

    @Test
    @DisplayName("publishPending: marks row as published after broker confirm")
    void publishPending_brokerConfirm_marksPublished() {
        mockClaim(createClaimResult(false));
        when(jdbcTemplate.queryForMap(anyString(), eq(outboxId)))
                .thenReturn(createOutboxRow(0, 3));

        // Simulate broker confirm by completing the correlation data future
        doAnswer(invocation -> {
            OutboxCorrelationData corr = invocation.getArgument(3);
            corr.markPublished();
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(OutboxCorrelationData.class));

        publisher.publishPending();

        // Verify row marked as published
        verify(jdbcTemplate).update(
                contains("published_at = ?"),
                any(LocalDateTime.class),
                eq(outboxId)
        );
        // Verify outbox age metric recorded
        verify(notificationMetrics).recordOutboxAge(anyLong());
    }

    @Test
    @DisplayName("publishPending: schedules retry when broker does not confirm (timeout)")
    void publishPending_brokerTimeout_schedulesRetry() {
        mockClaim(createClaimResult(false));
        when(jdbcTemplate.queryForMap(anyString(), eq(outboxId)))
                .thenReturn(createOutboxRow(0, 3));

        // Do NOT complete the correlation data future — simulate broker timeout
        // awaitConfirm will time out and return false
        doNothing().when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(OutboxCorrelationData.class));

        publisher.publishPending();

        // Verify retry is scheduled (publish_attempts incremented, next_attempt_at set)
        verify(jdbcTemplate).update(
                contains("publish_attempts = ?"),
                eq(1),
                any(LocalDateTime.class),
                eq(outboxId)
        );
        // Row should NOT be marked as published
        verify(jdbcTemplate, never()).update(
                contains("published_at = ?"),
                any(LocalDateTime.class),
                eq(outboxId)
        );
    }

    @Test
    @DisplayName("publishPending: marks row as failed after exhausting max attempts")
    void publishPending_exhaustedRetries_marksFailed() {
        mockClaim(createClaimResult(false));
        when(jdbcTemplate.queryForMap(anyString(), eq(outboxId)))
                .thenReturn(createOutboxRow(2, 3)); // attempts=2, max=3 → next attempt exhausts

        // Simulate broker timeout
        doNothing().when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(OutboxCorrelationData.class));

        publisher.publishPending();

        // Verify row marked as permanently failed (next_attempt_at = NULL)
        verify(jdbcTemplate).update(
                contains("next_attempt_at = NULL"),
                eq(3),
                eq(outboxId)
        );
    }

    @Test
    @DisplayName("publishPending: schedules retry with exponential backoff on first failure")
    void publishPending_firstFailure_schedules30sRetry() {
        mockClaim(createClaimResult(false));
        when(jdbcTemplate.queryForMap(anyString(), eq(outboxId)))
                .thenReturn(createOutboxRow(0, 3)); // attempts=0 → first failure

        doNothing().when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(OutboxCorrelationData.class));

        publisher.publishPending();

        // Verify retry scheduled with 30s delay (RETRY_DELAY_SECONDS[0])
        ArgumentCaptor<LocalDateTime> nextAttemptCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jdbcTemplate).update(
                contains("next_attempt_at = ?"),
                eq(1),
                nextAttemptCaptor.capture(),
                eq(outboxId)
        );
        // Next attempt should be ~30 seconds from now
        LocalDateTime nextAttempt = nextAttemptCaptor.getValue();
        long delaySeconds = java.time.Duration.between(
                LocalDateTime.now(ZoneOffset.UTC), nextAttempt).toSeconds();
        assertTrue(delaySeconds >= 25 && delaySeconds <= 35,
                "Expected ~30s retry delay, got " + delaySeconds + "s");
    }

    @Test
    @DisplayName("publishPending: records lease recovery for rows with expired leases")
    void publishPending_expiredLease_recordsLeaseRecovery() {
        mockClaim(createClaimResult(true)); // withExpiredLease = true
        when(jdbcTemplate.queryForMap(anyString(), eq(outboxId)))
                .thenReturn(createOutboxRow(0, 3));

        doAnswer(invocation -> {
            OutboxCorrelationData corr = invocation.getArgument(3);
            corr.markPublished();
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(OutboxCorrelationData.class));

        publisher.publishPending();

        // Verify lease recovery metric was recorded
        verify(notificationMetrics).recordLeaseRecovery();
    }

    @Test
    @DisplayName("publishPending: continues processing other rows when one fails")
    void publishPending_oneRowFails_continuesOthers() {
        UUID outboxId2 = UUID.randomUUID();

        // Two rows claimed
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", outboxId);
        row1.put("old_lease_until", null);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", outboxId2);
        row2.put("old_lease_until", null);
        mockClaim(List.of(row1, row2));

        // First row throws, second succeeds
        when(jdbcTemplate.queryForMap(anyString(), eq(outboxId)))
                .thenThrow(new RuntimeException("DB error"));
        when(jdbcTemplate.queryForMap(anyString(), eq(outboxId2)))
                .thenReturn(createOutboxRow(0, 3));

        doAnswer(invocation -> {
            OutboxCorrelationData corr = invocation.getArgument(3);
            corr.markPublished();
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(OutboxCorrelationData.class));

        publisher.publishPending();

        // Second row should still be published
        verify(jdbcTemplate).update(
                contains("published_at = ?"),
                any(LocalDateTime.class),
                eq(outboxId2)
        );
    }

    @Test
    @DisplayName("publishPending: uses CTE with ORDER BY and SKIP LOCKED for claiming")
    void publishPending_claimQueryUsesCteAndSkipLocked() {
        mockClaim(List.of());

        publisher.publishPending();

        // Verify the claim query uses CTE pattern with ORDER BY and FOR UPDATE SKIP LOCKED
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("WITH claimable AS"), "Expected CTE pattern");
        assertTrue(sql.contains("ORDER BY"), "Expected ORDER BY for deterministic claiming");
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"), "Expected SKIP LOCKED");
    }
}
