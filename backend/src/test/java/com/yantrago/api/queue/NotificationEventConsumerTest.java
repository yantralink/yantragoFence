package com.yantrago.api.queue;

import com.yantrago.api.model.NotificationInbox;
import com.yantrago.api.repository.NotificationInboxRepository;
import com.yantrago.api.service.NotificationFeatureSwitches;
import com.yantrago.api.service.NotificationMetrics;
import com.yantrago.api.service.NotificationTemplateService;
import com.yantrago.api.service.PushDeliveryService;
import com.yantrago.api.service.RecipientResolutionService;
import com.yantrago.api.service.RecipientResolutionService.RecipientSnapshot;
import com.yantrago.api.websocket.NotificationBroadcastService;
import com.yantrago.shared.queue.AlertTransitionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for NotificationEventConsumer.
 *
 * Verifies Phase 3 acceptance criteria:
 * - Eligible assignees each get exactly one inbox item
 * - Per-event/recipient deduplication
 * - Revoked access suppresses queued fanout
 * - No-recipient behavior (no inbox item created)
 * - Idempotency on redelivery
 * - Recipient snapshots are captured
 *
 * Per notification plan Phase 3 acceptance.
 */
class NotificationEventConsumerTest {

    private JdbcTemplate jdbcTemplate;
    private RecipientResolutionService recipientResolutionService;
    private NotificationTemplateService templateService;
    private NotificationInboxRepository inboxRepository;
    private NotificationBroadcastService broadcastService;
    private PushDeliveryService pushDeliveryService;
    private NotificationFeatureSwitches featureSwitches;
    private NotificationMetrics notificationMetrics;

    private NotificationEventConsumer consumer;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID alertId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        recipientResolutionService = mock(RecipientResolutionService.class);
        templateService = mock(NotificationTemplateService.class);
        inboxRepository = mock(NotificationInboxRepository.class);
        broadcastService = mock(NotificationBroadcastService.class);
        pushDeliveryService = mock(PushDeliveryService.class);
        featureSwitches = mock(NotificationFeatureSwitches.class);
        notificationMetrics = mock(NotificationMetrics.class);

        consumer = new NotificationEventConsumer(jdbcTemplate, recipientResolutionService,
                templateService, inboxRepository, broadcastService, pushDeliveryService,
                featureSwitches, notificationMetrics);

        // Default: all event families enabled
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        // Default: idempotency insert succeeds (first processing)
        when(jdbcTemplate.update(
                anyString(), eq(eventId), anyString()))
                .thenReturn(1);

        // Default: machine name resolution
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId)))
                .thenReturn("Tractor-01");

        // Default: template rendering
        when(templateService.render(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new NotificationTemplateService.RenderedTemplate(
                        "Test Title", "Test Body", 1));
    }

    private AlertTransitionMessage createMessage(UUID eventId, String incidentState) {
        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(eventId);
        msg.setAlertId(alertId);
        msg.setOrganizationId(orgId);
        msg.setMachineId(machineId);
        msg.setAlertType("LOW_BATTERY");
        msg.setSeverity("WARNING");
        msg.setIncidentState(incidentState);
        msg.setObservedValue(15.0);
        msg.setObservedUnit("%");
        msg.setMessage("Battery low");
        msg.setOccurredAt(Instant.now());
        return msg;
    }

    private RecipientSnapshot createSnapshot() {
        return new RecipientSnapshot(userId, customerId, "John Farmer");
    }

    @Test
    @DisplayName("handleAlertTransition: creates inbox item for assigned user with recipient snapshot")
    void handleAlertTransition_createsInboxItemForAssignedUser() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(createSnapshot()));
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId))
                .thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());
        when(inboxRepository.save(any())).thenAnswer(inv -> {
            NotificationInbox inbox = inv.getArgument(0);
            // Verify recipient snapshot is captured
            assertEquals(customerId, inbox.getRecipientCustomerId());
            assertEquals("John Farmer", inbox.getRecipientCustomerName());
            return inbox;
        });
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId))
                .thenReturn(1L);

        consumer.handleAlertTransition(msg);

        verify(inboxRepository).save(any(NotificationInbox.class));
        verify(broadcastService).notifyNewNotification(userId, 1L);
    }

    @Test
    @DisplayName("handleAlertTransition: no recipients — no inbox item created")
    void handleAlertTransition_noRecipients_noInboxItem() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of());

        consumer.handleAlertTransition(msg);

        verify(inboxRepository, never()).save(any());
        verify(broadcastService, never()).notifyNewNotification(any(), anyLong());
    }

    @Test
    @DisplayName("handleAlertTransition: idempotent — skips already-processed event")
    void handleAlertTransition_idempotent_skipsAlreadyProcessed() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        // Idempotency insert returns 0 = already processed
        when(jdbcTemplate.update(anyString(), eq(eventId), anyString()))
                .thenReturn(0);

        consumer.handleAlertTransition(msg);

        verify(recipientResolutionService, never()).resolveRecipientSnapshots(any(), any());
        verify(inboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleAlertTransition: deduplicates per-event/recipient")
    void handleAlertTransition_deduplicatesPerEventRecipient() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(createSnapshot()));
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId))
                .thenReturn(true);
        // Inbox item already exists for this event+user
        when(inboxRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(new NotificationInbox()));

        consumer.handleAlertTransition(msg);

        verify(inboxRepository, never()).save(any());
        verify(broadcastService, never()).notifyNewNotification(any(), anyLong());
    }

    @Test
    @DisplayName("handleAlertTransition: revoked access suppresses fanout")
    void handleAlertTransition_revokedAccessSuppressesFanout() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(createSnapshot()));
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId))
                .thenReturn(false); // access revoked
        when(inboxRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());

        consumer.handleAlertTransition(msg);

        verify(inboxRepository, never()).save(any());
        verify(broadcastService, never()).notifyNewNotification(any(), anyLong());
    }

    @Test
    @DisplayName("handleAlertTransition: missing eventId — throws")
    void handleAlertTransition_missingEventId_throws() {
        AlertTransitionMessage msg = createMessage(null, "OPEN");

        assertThrows(IllegalArgumentException.class, () -> consumer.handleAlertTransition(msg));
    }

    @Test
    @DisplayName("handleAlertTransition: missing orgId — skips inbox creation")
    void handleAlertTransition_missingOrgId_skipsInbox() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        msg.setOrganizationId(null);

        consumer.handleAlertTransition(msg);

        verify(recipientResolutionService, never()).resolveRecipientSnapshots(any(), any());
        verify(inboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleAlertTransition: uses fallback when no template found")
    void handleAlertTransition_usesFallbackWhenNoTemplate() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(createSnapshot()));
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId))
                .thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());
        when(inboxRepository.save(any())).thenAnswer(inv -> {
            NotificationInbox inbox = inv.getArgument(0);
            // Verify fallback title/body
            assertTrue(inbox.getTitle().contains("LOW_BATTERY"));
            assertEquals("Battery low", inbox.getBody());
            assertEquals(0, inbox.getTemplateVersion());
            return inbox;
        });
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId))
                .thenReturn(1L);

        // No template found
        when(templateService.render(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

        consumer.handleAlertTransition(msg);

        verify(inboxRepository).save(any(NotificationInbox.class));
    }

    @Test
    @DisplayName("handleAlertTransition: creates inbox for RESOLVED state")
    void handleAlertTransition_createsInboxForResolvedState() {
        AlertTransitionMessage msg = createMessage(eventId, "RESOLVED");
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(createSnapshot()));
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId))
                .thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());
        when(inboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId))
                .thenReturn(0L);

        consumer.handleAlertTransition(msg);

        verify(inboxRepository).save(any(NotificationInbox.class));
        verify(broadcastService).notifyNewNotification(userId, 0L);
    }

    @Test
    @DisplayName("handleAlertTransition: event family disabled by feature switch — no inbox item")
    void handleAlertTransition_eventFamilyDisabled_noInboxItem() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(featureSwitches.isEventFamilyEnabled("LOW_BATTERY")).thenReturn(false);

        consumer.handleAlertTransition(msg);

        verify(recipientResolutionService, never()).resolveRecipientSnapshots(any(), any());
        verify(inboxRepository, never()).save(any());
        verify(notificationMetrics).recordEventFamilySkipped("LOW_BATTERY");
    }

    @Test
    @DisplayName("handleAlertTransition: no recipients — records no-recipient metric")
    void handleAlertTransition_noRecipients_recordsMetric() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(Collections.emptyList());

        consumer.handleAlertTransition(msg);

        verify(inboxRepository, never()).save(any());
        verify(notificationMetrics).recordNoRecipient("LOW_BATTERY");
    }

    @Test
    @DisplayName("handleAlertTransition: duplicate inbox item — records duplicate suppressed metric")
    void handleAlertTransition_duplicateInboxItem_recordsMetric() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(createSnapshot()));
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId))
                .thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(new NotificationInbox()));

        consumer.handleAlertTransition(msg);

        verify(inboxRepository, never()).save(any());
        verify(notificationMetrics).recordDuplicateSuppressed("LOW_BATTERY");
    }

    @Test
    @DisplayName("handleAlertTransition: access revoked — records recipient excluded metric")
    void handleAlertTransition_accessRevoked_recordsMetric() {
        AlertTransitionMessage msg = createMessage(eventId, "OPEN");
        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(recipientResolutionService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(createSnapshot()));
        when(recipientResolutionService.revalidateAccess(orgId, machineId, userId))
                .thenReturn(false);
        when(inboxRepository.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.empty());

        consumer.handleAlertTransition(msg);

        verify(inboxRepository, never()).save(any());
        verify(notificationMetrics).recordRecipientExcluded("access_revoked");
    }
}
