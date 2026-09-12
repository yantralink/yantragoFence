package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yantrago.shared.queue.AlertTransitionMessage;
import com.yantrago.shared.queue.QueueNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for NotificationDlqReplayService (Phase 6).
 *
 * Per notification plan N11:
 * - "Replays are privileged, audited, bounded, and idempotent."
 * - "Recheck current recipient access, expiry-cycle validity, and event age
 *    so recovery does not flood users with stale pushes."
 */
@ExtendWith(MockitoExtension.class)
class NotificationDlqReplayServiceTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private NotificationMetrics notificationMetrics;
    @Mock private RecipientResolutionService recipientResolutionService;

    private NotificationDlqReplayService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final UUID replayedBy = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationDlqReplayService(rabbitTemplate, jdbcTemplate,
                objectMapper, notificationMetrics, recipientResolutionService);
    }

    @Test
    @DisplayName("Replay: fresh event with active recipient — SUCCESS")
    void replayMessage_freshEventWithRecipient_success() throws Exception {
        AlertTransitionMessage msg = createMessage(Instant.now(), true);
        String body = objectMapper.writeValueAsString(msg);

        when(recipientResolutionService.resolveRecipientSnapshots(any(), any()))
                .thenReturn(List.of(new RecipientResolutionService.RecipientSnapshot(
                        UUID.randomUUID(), UUID.randomUUID(), "Customer")));

        var result = service.replayMessage(body, replayedBy, "Manual replay after DLQ review");

        assertTrue(result.success());
        assertEquals("SUCCESS", result.status());

        // Verify message was re-published
        verify(rabbitTemplate).convertAndSend(
                eq(QueueNames.NOTIFICATION_EXCHANGE),
                eq(QueueNames.NOTIFICATION_ROUTING_KEY),
                any(AlertTransitionMessage.class));

        // Verify processed_events was cleared for idempotent reprocessing
        verify(jdbcTemplate).update(
                contains("DELETE FROM processed_events"), eq(msg.getEventId()));

        // Verify audit was recorded
        verify(jdbcTemplate).update(
                contains("INSERT INTO notification_replay_audit"),
                eq(replayedBy), eq(body), anyString(), eq("SUCCESS"),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Replay: stale event (older than 24h) — REJECTED_STALE")
    void replayMessage_staleEvent_rejectedStale() throws Exception {
        AlertTransitionMessage msg = createMessage(Instant.now().minus(Duration.ofHours(48)), true);
        String body = objectMapper.writeValueAsString(msg);

        var result = service.replayMessage(body, replayedBy, "Manual replay");

        assertFalse(result.success());
        assertEquals("REJECTED_STALE", result.status());

        // Verify message was NOT re-published
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));

        // Verify audit recorded the rejection
        verify(jdbcTemplate).update(
                contains("INSERT INTO notification_replay_audit"),
                eq(replayedBy), eq(body), anyString(), eq("REJECTED_STALE"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Replay: machine reassigned (no active recipient) — REJECTED_NO_ACCESS")
    void replayMessage_noActiveRecipient_rejectedNoAccess() throws Exception {
        AlertTransitionMessage msg = createMessage(Instant.now(), true);
        String body = objectMapper.writeValueAsString(msg);

        when(recipientResolutionService.resolveRecipientSnapshots(any(), any()))
                .thenReturn(Collections.emptyList());

        var result = service.replayMessage(body, replayedBy, "Manual replay");

        assertFalse(result.success());
        assertEquals("REJECTED_NO_ACCESS", result.status());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(jdbcTemplate).update(
                contains("INSERT INTO notification_replay_audit"),
                eq(replayedBy), eq(body), anyString(), eq("REJECTED_NO_ACCESS"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Replay: invalid JSON — FAILED")
    void replayMessage_invalidJson_failed() {
        var result = service.replayMessage("not valid json", replayedBy, "Manual replay");

        assertFalse(result.success());
        assertEquals("FAILED", result.status());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Replay: no machineId in message — skips access check, SUCCESS")
    void replayMessage_noMachineId_skipsAccessCheck() throws Exception {
        AlertTransitionMessage msg = createMessage(Instant.now(), false);
        String body = objectMapper.writeValueAsString(msg);

        var result = service.replayMessage(body, replayedBy, "Manual replay");

        assertTrue(result.success());
        assertEquals("SUCCESS", result.status());
        verify(recipientResolutionService, never()).resolveRecipientSnapshots(any(), any());
    }

    @Test
    @DisplayName("Replay: DLQ metric is recorded")
    void replayMessage_recordsDlqMetric() throws Exception {
        AlertTransitionMessage msg = createMessage(Instant.now(), false);
        String body = objectMapper.writeValueAsString(msg);

        service.replayMessage(body, replayedBy, "Manual replay");

        verify(notificationMetrics).recordDlqMessage();
    }

    private AlertTransitionMessage createMessage(Instant occurredAt, boolean withMachineId) {
        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(UUID.randomUUID());
        msg.setAlertId(UUID.randomUUID());
        msg.setOrganizationId(UUID.randomUUID());
        if (withMachineId) {
            msg.setMachineId(UUID.randomUUID());
        }
        msg.setAlertType("LOW_BATTERY");
        msg.setSeverity("WARNING");
        msg.setIncidentState("OPEN");
        msg.setOccurredAt(occurredAt);
        return msg;
    }
}
