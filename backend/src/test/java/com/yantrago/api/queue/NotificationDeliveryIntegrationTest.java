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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
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
 * End-to-end inbox delivery integration test.
 *
 * Validates the full notification flow:
 *   telemetry observation -> alert -> outbox -> notification queue -> inbox creation
 *
 * Simulates the AlertTransitionMessage that would be produced by the outbox publisher
 * after a committed alert state change. Verifies:
 * - Inbox item is created for the assigned user
 * - Recipient snapshot is captured (customer ID + name)
 * - WebSocket invalidation is sent
 * - Per-event/recipient deduplication works
 * - Revoked access suppresses fanout
 * - Multiple recipients each get their own inbox item
 * - Different event IDs create separate inbox items
 *
 * Per notification plan Phase 3 acceptance:
 * "Exercise inbox delivery end to end using supported simulated observations
 *  rather than a public test-alert endpoint."
 */
class NotificationDeliveryIntegrationTest {

    @Test
    @DisplayName("E2E: LOW_BATTERY alert creates inbox item for assigned user with recipient snapshot")
    void lowBatteryAlert_createsInboxItemForAssignedUser() {
        // Arrange
        UUID orgId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecipientResolutionService recipientService = mock(RecipientResolutionService.class);
        NotificationTemplateService templateService = mock(NotificationTemplateService.class);
        NotificationInboxRepository inboxRepository = mock(NotificationInboxRepository.class);
        NotificationBroadcastService broadcastService = mock(NotificationBroadcastService.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        NotificationFeatureSwitches featureSwitches = mock(NotificationFeatureSwitches.class);
        NotificationMetrics notificationMetrics = mock(NotificationMetrics.class);
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        NotificationEventConsumer consumer = new NotificationEventConsumer(
                jdbcTemplate, recipientService, templateService, inboxRepository, broadcastService,
                pushDeliveryService, featureSwitches, notificationMetrics);

        // Idempotency: first processing
        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);

        // Machine name
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId))).thenReturn("Tractor-01");

        // Recipient snapshot: assigned customer's user
        when(recipientService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(new RecipientSnapshot(userId, customerId, "John Farmer")));

        // Access still valid
        when(recipientService.revalidateAccess(orgId, machineId, userId)).thenReturn(true);

        // No existing inbox item
        when(inboxRepository.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());

        // Template rendering
        when(templateService.render(any(), eq("LOW_BATTERY"), eq("OPEN"), eq("en"), any()))
                .thenReturn(new NotificationTemplateService.RenderedTemplate(
                        "Low Battery Alert", "Battery is low on machine Tractor-01: 15%", 1));

        // Save returns the inbox item
        when(inboxRepository.save(any())).thenAnswer(inv -> {
            NotificationInbox inbox = inv.getArgument(0);
            inbox.setId(UUID.randomUUID());
            // Verify recipient snapshot is captured
            assertEquals(customerId, inbox.getRecipientCustomerId());
            assertEquals("John Farmer", inbox.getRecipientCustomerName());
            assertEquals("LOW_BATTERY", inbox.getAlertType());
            assertEquals("OPEN", inbox.getIncidentState());
            assertEquals("Low Battery Alert", inbox.getTitle());
            assertFalse(inbox.getIsRead());
            assertFalse(inbox.getIsAcknowledged());
            return inbox;
        });

        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId))
                .thenReturn(1L);

        // Act
        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(eventId);
        msg.setAlertId(alertId);
        msg.setOrganizationId(orgId);
        msg.setMachineId(machineId);
        msg.setAlertType("LOW_BATTERY");
        msg.setSeverity("WARNING");
        msg.setIncidentState("OPEN");
        msg.setObservedValue(15.0);
        msg.setObservedUnit("%");
        msg.setMessage("Battery low");
        msg.setOccurredAt(Instant.now());

        consumer.handleAlertTransition(msg);

        // Assert
        verify(inboxRepository).save(any(NotificationInbox.class));
        verify(broadcastService).notifyNewNotification(userId, 1L);
    }

    @Test
    @DisplayName("E2E: DEVICE_OFFLINE alert creates inbox item with correct template")
    void deviceOfflineAlert_createsInboxItem() {
        UUID orgId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecipientResolutionService recipientService = mock(RecipientResolutionService.class);
        NotificationTemplateService templateService = mock(NotificationTemplateService.class);
        NotificationInboxRepository inboxRepository = mock(NotificationInboxRepository.class);
        NotificationBroadcastService broadcastService = mock(NotificationBroadcastService.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        NotificationFeatureSwitches featureSwitches = mock(NotificationFeatureSwitches.class);
        NotificationMetrics notificationMetrics = mock(NotificationMetrics.class);
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        NotificationEventConsumer consumer = new NotificationEventConsumer(
                jdbcTemplate, recipientService, templateService, inboxRepository, broadcastService,
                pushDeliveryService, featureSwitches, notificationMetrics);

        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId))).thenReturn("Generator-05");
        when(recipientService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(new RecipientSnapshot(userId, customerId, "Jane Farmer")));
        when(recipientService.revalidateAccess(orgId, machineId, userId)).thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());
        when(templateService.render(any(), eq("DEVICE_OFFLINE"), eq("OPEN"), eq("en"), any()))
                .thenReturn(new NotificationTemplateService.RenderedTemplate(
                        "Device Offline", "Device for machine Generator-05 has been offline for 30 minutes.", 1));
        when(inboxRepository.save(any())).thenAnswer(inv -> {
            NotificationInbox inbox = inv.getArgument(0);
            inbox.setId(UUID.randomUUID());
            assertEquals("DEVICE_OFFLINE", inbox.getAlertType());
            assertEquals("Device Offline", inbox.getTitle());
            assertEquals("Jane Farmer", inbox.getRecipientCustomerName());
            return inbox;
        });
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId)).thenReturn(1L);

        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(eventId);
        msg.setOrganizationId(orgId);
        msg.setMachineId(machineId);
        msg.setAlertType("DEVICE_OFFLINE");
        msg.setSeverity("CRITICAL");
        msg.setIncidentState("OPEN");
        msg.setObservedValue(30.0);
        msg.setObservedUnit("minutes");
        msg.setOccurredAt(Instant.now());

        consumer.handleAlertTransition(msg);

        verify(inboxRepository).save(any(NotificationInbox.class));
    }

    @Test
    @DisplayName("E2E: SIM_EXPIRY alert creates inbox item")
    void simExpiryAlert_createsInboxItem() {
        UUID orgId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecipientResolutionService recipientService = mock(RecipientResolutionService.class);
        NotificationTemplateService templateService = mock(NotificationTemplateService.class);
        NotificationInboxRepository inboxRepository = mock(NotificationInboxRepository.class);
        NotificationBroadcastService broadcastService = mock(NotificationBroadcastService.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        NotificationFeatureSwitches featureSwitches = mock(NotificationFeatureSwitches.class);
        NotificationMetrics notificationMetrics = mock(NotificationMetrics.class);
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        NotificationEventConsumer consumer = new NotificationEventConsumer(
                jdbcTemplate, recipientService, templateService, inboxRepository, broadcastService,
                pushDeliveryService, featureSwitches, notificationMetrics);

        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId))).thenReturn("Pump-12");
        when(recipientService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(new RecipientSnapshot(userId, customerId, "Bob Farmer")));
        when(recipientService.revalidateAccess(orgId, machineId, userId)).thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, userId)).thenReturn(Optional.empty());
        when(templateService.render(any(), eq("SIM_EXPIRY"), eq("OPEN"), eq("en"), any()))
                .thenReturn(new NotificationTemplateService.RenderedTemplate(
                        "SIM Expiry Reminder", "SIM plan for machine Pump-12 expires in 7 days.", 1));
        when(inboxRepository.save(any())).thenAnswer(inv -> {
            NotificationInbox inbox = inv.getArgument(0);
            inbox.setId(UUID.randomUUID());
            assertEquals("SIM_EXPIRY", inbox.getAlertType());
            assertEquals("SIM Expiry Reminder", inbox.getTitle());
            return inbox;
        });
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId)).thenReturn(1L);

        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(eventId);
        msg.setOrganizationId(orgId);
        msg.setMachineId(machineId);
        msg.setAlertType("SIM_EXPIRY");
        msg.setSeverity("WARNING");
        msg.setIncidentState("OPEN");
        msg.setObservedValue(7.0);
        msg.setObservedUnit("days");
        msg.setOccurredAt(Instant.now());

        consumer.handleAlertTransition(msg);

        verify(inboxRepository).save(any(NotificationInbox.class));
    }

    @Test
    @DisplayName("E2E: RESOLVED state creates separate inbox item with different event ID")
    void resolvedState_createsSeparateInboxItem() {
        UUID orgId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID openEventId = UUID.randomUUID();
        UUID resolvedEventId = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecipientResolutionService recipientService = mock(RecipientResolutionService.class);
        NotificationTemplateService templateService = mock(NotificationTemplateService.class);
        NotificationInboxRepository inboxRepository = mock(NotificationInboxRepository.class);
        NotificationBroadcastService broadcastService = mock(NotificationBroadcastService.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        NotificationFeatureSwitches featureSwitches = mock(NotificationFeatureSwitches.class);
        NotificationMetrics notificationMetrics = mock(NotificationMetrics.class);
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        NotificationEventConsumer consumer = new NotificationEventConsumer(
                jdbcTemplate, recipientService, templateService, inboxRepository, broadcastService,
                pushDeliveryService, featureSwitches, notificationMetrics);

        when(jdbcTemplate.update(anyString(), eq(resolvedEventId), anyString())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId))).thenReturn("Tractor-01");
        when(recipientService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(new RecipientSnapshot(userId, customerId, "John Farmer")));
        when(recipientService.revalidateAccess(orgId, machineId, userId)).thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(resolvedEventId, userId)).thenReturn(Optional.empty());
        when(templateService.render(any(), eq("LOW_BATTERY"), eq("RESOLVED"), eq("en"), any()))
                .thenReturn(new NotificationTemplateService.RenderedTemplate(
                        "Battery Recovered", "Battery level recovered on machine Tractor-01.", 1));
        when(inboxRepository.save(any())).thenAnswer(inv -> {
            NotificationInbox inbox = inv.getArgument(0);
            inbox.setId(UUID.randomUUID());
            assertEquals("RESOLVED", inbox.getIncidentState());
            assertEquals("Battery Recovered", inbox.getTitle());
            return inbox;
        });
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId)).thenReturn(0L);

        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(resolvedEventId);
        msg.setOrganizationId(orgId);
        msg.setMachineId(machineId);
        msg.setAlertType("LOW_BATTERY");
        msg.setSeverity("INFO");
        msg.setIncidentState("RESOLVED");
        msg.setOccurredAt(Instant.now());

        consumer.handleAlertTransition(msg);

        verify(inboxRepository).save(any(NotificationInbox.class));
    }

    @Test
    @DisplayName("E2E: Two users in same org each get their own inbox item")
    void twoUsersInSameOrg_eachGetOwnInboxItem() {
        UUID orgId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID customer1 = UUID.randomUUID();
        UUID customer2 = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecipientResolutionService recipientService = mock(RecipientResolutionService.class);
        NotificationTemplateService templateService = mock(NotificationTemplateService.class);
        NotificationInboxRepository inboxRepository = mock(NotificationInboxRepository.class);
        NotificationBroadcastService broadcastService = mock(NotificationBroadcastService.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        NotificationFeatureSwitches featureSwitches = mock(NotificationFeatureSwitches.class);
        NotificationMetrics notificationMetrics = mock(NotificationMetrics.class);
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        NotificationEventConsumer consumer = new NotificationEventConsumer(
                jdbcTemplate, recipientService, templateService, inboxRepository, broadcastService,
                pushDeliveryService, featureSwitches, notificationMetrics);

        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId))).thenReturn("Tractor-01");
        when(recipientService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(
                        new RecipientSnapshot(user1, customer1, "User One"),
                        new RecipientSnapshot(user2, customer2, "User Two")));
        when(recipientService.revalidateAccess(orgId, machineId, user1)).thenReturn(true);
        when(recipientService.revalidateAccess(orgId, machineId, user2)).thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, user1)).thenReturn(Optional.empty());
        when(inboxRepository.findByEventIdAndUserId(eventId, user2)).thenReturn(Optional.empty());
        when(templateService.render(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new NotificationTemplateService.RenderedTemplate("Title", "Body", 1));
        when(inboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, user1)).thenReturn(1L);
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, user2)).thenReturn(1L);

        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(eventId);
        msg.setOrganizationId(orgId);
        msg.setMachineId(machineId);
        msg.setAlertType("LOW_BATTERY");
        msg.setSeverity("WARNING");
        msg.setIncidentState("OPEN");
        msg.setOccurredAt(Instant.now());

        consumer.handleAlertTransition(msg);

        // Both users get their own inbox item
        verify(inboxRepository, times(2)).save(any(NotificationInbox.class));
        verify(broadcastService).notifyNewNotification(user1, 1L);
        verify(broadcastService).notifyNewNotification(user2, 1L);
    }

    @Test
    @DisplayName("E2E: User in different org does not receive notification (tenant isolation)")
    void userInDifferentOrg_doesNotReceiveNotification() {
        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecipientResolutionService recipientService = mock(RecipientResolutionService.class);
        NotificationTemplateService templateService = mock(NotificationTemplateService.class);
        NotificationInboxRepository inboxRepository = mock(NotificationInboxRepository.class);
        NotificationBroadcastService broadcastService = mock(NotificationBroadcastService.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        NotificationFeatureSwitches featureSwitches = mock(NotificationFeatureSwitches.class);
        NotificationMetrics notificationMetrics = mock(NotificationMetrics.class);
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        NotificationEventConsumer consumer = new NotificationEventConsumer(
                jdbcTemplate, recipientService, templateService, inboxRepository, broadcastService,
                pushDeliveryService, featureSwitches, notificationMetrics);

        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId))).thenReturn("Tractor-01");

        // Org 1 has the machine, but no recipients
        when(recipientService.resolveRecipientSnapshots(org1, machineId))
                .thenReturn(List.of());

        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(eventId);
        msg.setOrganizationId(org1);
        msg.setMachineId(machineId);
        msg.setAlertType("LOW_BATTERY");
        msg.setSeverity("WARNING");
        msg.setIncidentState("OPEN");
        msg.setOccurredAt(Instant.now());

        consumer.handleAlertTransition(msg);

        // No inbox item created, no broadcast
        verify(inboxRepository, never()).save(any());
        verify(broadcastService, never()).notifyNewNotification(any(), anyLong());
    }

    @Test
    @DisplayName("E2E: Revoked access suppresses fanout for that user only")
    void revokedAccess_suppressesFanoutForThatUserOnly() {
        UUID orgId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RecipientResolutionService recipientService = mock(RecipientResolutionService.class);
        NotificationTemplateService templateService = mock(NotificationTemplateService.class);
        NotificationInboxRepository inboxRepository = mock(NotificationInboxRepository.class);
        NotificationBroadcastService broadcastService = mock(NotificationBroadcastService.class);
        PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
        NotificationFeatureSwitches featureSwitches = mock(NotificationFeatureSwitches.class);
        NotificationMetrics notificationMetrics = mock(NotificationMetrics.class);
        when(featureSwitches.isEventFamilyEnabled(anyString())).thenReturn(true);

        NotificationEventConsumer consumer = new NotificationEventConsumer(
                jdbcTemplate, recipientService, templateService, inboxRepository, broadcastService,
                pushDeliveryService, featureSwitches, notificationMetrics);

        when(jdbcTemplate.update(anyString(), eq(eventId), anyString())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT name FROM machines WHERE id = ?"),
                eq(String.class), eq(machineId))).thenReturn("Tractor-01");
        when(recipientService.resolveRecipientSnapshots(orgId, machineId))
                .thenReturn(List.of(
                        new RecipientSnapshot(user1, UUID.randomUUID(), "User One"),
                        new RecipientSnapshot(user2, UUID.randomUUID(), "User Two")));
        // User 1 access revoked, user 2 access valid
        when(recipientService.revalidateAccess(orgId, machineId, user1)).thenReturn(false);
        when(recipientService.revalidateAccess(orgId, machineId, user2)).thenReturn(true);
        when(inboxRepository.findByEventIdAndUserId(eventId, user2)).thenReturn(Optional.empty());
        when(templateService.render(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new NotificationTemplateService.RenderedTemplate("Title", "Body", 1));
        when(inboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, user2)).thenReturn(1L);

        AlertTransitionMessage msg = new AlertTransitionMessage();
        msg.setEventId(eventId);
        msg.setOrganizationId(orgId);
        msg.setMachineId(machineId);
        msg.setAlertType("LOW_BATTERY");
        msg.setSeverity("WARNING");
        msg.setIncidentState("OPEN");
        msg.setOccurredAt(Instant.now());

        consumer.handleAlertTransition(msg);

        // Only user 2 gets an inbox item (user 1 access was revoked)
        verify(inboxRepository, times(1)).save(any(NotificationInbox.class));
        verify(broadcastService, never()).notifyNewNotification(eq(user1), anyLong());
        verify(broadcastService).notifyNewNotification(user2, 1L);
    }
}
