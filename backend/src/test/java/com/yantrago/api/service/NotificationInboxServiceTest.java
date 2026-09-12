package com.yantrago.api.service;

import com.yantrago.api.dto.notification.NotificationInboxDto;
import com.yantrago.api.model.NotificationInbox;
import com.yantrago.api.repository.NotificationInboxRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.websocket.NotificationBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for NotificationInboxService.
 *
 * Verifies Phase 3 acceptance criteria:
 * - Read state is private (cross-user rejection)
 * - Read state is idempotent
 * - Mark-all-read works
 * - Unread count is correct
 * - Acknowledgement is private and idempotent
 *
 * Per notification plan Phase 3/4 acceptance.
 */
class NotificationInboxServiceTest {

    private NotificationInboxRepository inboxRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private PermissionEvaluator permissionEvaluator;
    private NotificationBroadcastService broadcastService;

    private NotificationInboxService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inboxRepository = mock(NotificationInboxRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        broadcastService = mock(NotificationBroadcastService.class);

        service = new NotificationInboxService(inboxRepository, ownerContextService,
                tenantGuard, permissionEvaluator, broadcastService);

        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    @DisplayName("listMyNotifications: returns current user's notifications as DTOs")
    void listMyNotifications_returnsCurrentUserNotifications() {
        NotificationInbox item = createInboxItem(userId);
        Page<NotificationInbox> page = new PageImpl<>(List.of(item));
        when(inboxRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationInboxDto> result = service.listMyNotifications(PageRequest.of(0, 20), null, null);

        assertEquals(1, result.getTotalElements());
        assertEquals(userId, result.getContent().get(0).userId());
    }

    @Test
    @DisplayName("listMyNotifications: filters unread only")
    void listMyNotifications_filtersUnreadOnly() {
        NotificationInbox item = createInboxItem(userId);
        item.setIsRead(false);
        Page<NotificationInbox> page = new PageImpl<>(List.of(item));
        when(inboxRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationInboxDto> result = service.listMyNotifications(PageRequest.of(0, 20), true, null);

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().get(0).isRead());
    }

    @Test
    @DisplayName("listMyNotifications: filters by alert type")
    void listMyNotifications_filtersByAlertType() {
        NotificationInbox item = createInboxItem(userId);
        Page<NotificationInbox> page = new PageImpl<>(List.of(item));
        when(inboxRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationInboxDto> result = service.listMyNotifications(PageRequest.of(0, 20), null, "LOW_BATTERY");

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("listMyNotifications: applies both unreadOnly and alertType filters together")
    void listMyNotifications_appliesBothFilters() {
        NotificationInbox item = createInboxItem(userId);
        item.setIsRead(false);
        item.setAlertType("VOLTAGE_DROP");
        Page<NotificationInbox> page = new PageImpl<>(List.of(item));
        when(inboxRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationInboxDto> result = service.listMyNotifications(PageRequest.of(0, 20), true, "VOLTAGE_DROP");

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().get(0).isRead());
    }

    @Test
    @DisplayName("listMyNotifications: caps page size at 100")
    void listMyNotifications_capsPageSize() {
        NotificationInbox item = createInboxItem(userId);
        Page<NotificationInbox> page = new PageImpl<>(List.of(item));
        when(inboxRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        service.listMyNotifications(PageRequest.of(0, 500), null, null);

        // Verify the capped pageable (size 100) was passed, not 500
        verify(inboxRepository).findAll(any(Specification.class), argThat((Pageable p) -> p.getPageSize() == 100));
    }

    @Test
    @DisplayName("getNotification: rejects cross-user access")
    void getNotification_rejectsCrossUserAccess() {
        NotificationInbox item = createInboxItem(otherUserId);
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.getNotification(item.getId()));
        assertTrue(ex.getMessage().contains("another user"));
    }

    @Test
    @DisplayName("getNotification: allows owner access and returns DTO")
    void getNotification_allowsOwnerAccess() {
        NotificationInbox item = createInboxItem(userId);
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));

        NotificationInboxDto result = service.getNotification(item.getId());

        assertEquals(userId, result.userId());
        assertFalse(result.isRead());
    }

    @Test
    @DisplayName("countUnread: returns correct count for current user")
    void countUnread_returnsCorrectCount() {
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId))
                .thenReturn(5L);

        long count = service.countUnread();

        assertEquals(5L, count);
    }

    @Test
    @DisplayName("markAsRead: marks unread notification as read")
    void markAsRead_marksUnreadAsRead() {
        NotificationInbox item = createInboxItem(userId);
        item.setIsRead(false);
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(inboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId))
                .thenReturn(0L);

        NotificationInboxDto result = service.markAsRead(item.getId());

        assertTrue(result.isRead());
        assertNotNull(result.readAt());
        verify(broadcastService).notifyReadStateChange(userId, 0L);
    }

    @Test
    @DisplayName("markAsRead: idempotent — already read notification is no-op")
    void markAsRead_idempotent_alreadyRead() {
        NotificationInbox item = createInboxItem(userId);
        item.setIsRead(true);
        item.setReadAt(LocalDateTime.now().minusMinutes(5));
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));

        NotificationInboxDto result = service.markAsRead(item.getId());

        assertTrue(result.isRead());
        // Should NOT save (already read) and NOT broadcast
        verify(inboxRepository, never()).save(any());
        verify(broadcastService, never()).notifyReadStateChange(any(), anyLong());
    }

    @Test
    @DisplayName("markAsRead: rejects cross-user modification")
    void markAsRead_rejectsCrossUserModification() {
        NotificationInbox item = createInboxItem(otherUserId);
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.markAsRead(item.getId()));
        assertTrue(ex.getMessage().contains("another user"));
    }

    @Test
    @DisplayName("markAllAsRead: marks all unread as read and broadcasts")
    void markAllAsRead_marksAllAndBroadcasts() {
        when(inboxRepository.markAllRead(eq(orgId), eq(userId), any(LocalDateTime.class)))
                .thenReturn(3);

        int count = service.markAllAsRead();

        assertEquals(3, count);
        verify(broadcastService).notifyReadStateChange(userId, 0L);
    }

    @Test
    @DisplayName("markAllAsRead: no-op when no unread items")
    void markAllAsRead_noOpWhenNoUnread() {
        when(inboxRepository.markAllRead(eq(orgId), eq(userId), any(LocalDateTime.class)))
                .thenReturn(0);

        int count = service.markAllAsRead();

        assertEquals(0, count);
        verify(broadcastService, never()).notifyReadStateChange(any(), anyLong());
    }

    @Test
    @DisplayName("acknowledge: marks notification as acknowledged")
    void acknowledge_marksAsAcknowledged() {
        NotificationInbox item = createInboxItem(userId);
        item.setIsAcknowledged(false);
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(inboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationInboxDto result = service.acknowledge(item.getId());

        assertTrue(result.isAcknowledged());
        assertNotNull(result.acknowledgedAt());
    }

    @Test
    @DisplayName("acknowledge: idempotent — already acknowledged is no-op")
    void acknowledge_idempotent_alreadyAcknowledged() {
        NotificationInbox item = createInboxItem(userId);
        item.setIsAcknowledged(true);
        item.setAcknowledgedAt(LocalDateTime.now().minusMinutes(5));
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));

        NotificationInboxDto result = service.acknowledge(item.getId());

        assertTrue(result.isAcknowledged());
        verify(inboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("acknowledge: rejects cross-user modification")
    void acknowledge_rejectsCrossUserModification() {
        NotificationInbox item = createInboxItem(otherUserId);
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.acknowledge(item.getId()));
        assertTrue(ex.getMessage().contains("another user"));
    }

    @Test
    @DisplayName("acknowledge: also marks as read if not already read")
    void acknowledge_alsoMarksAsRead() {
        NotificationInbox item = createInboxItem(userId);
        item.setIsRead(false);
        item.setIsAcknowledged(false);
        when(inboxRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(inboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationInboxDto result = service.acknowledge(item.getId());

        assertTrue(result.isAcknowledged());
        assertTrue(result.isRead());
        assertNotNull(result.readAt());
    }

    private NotificationInbox createInboxItem(UUID userId) {
        NotificationInbox item = new NotificationInbox();
        item.setId(UUID.randomUUID());
        item.setOrganizationId(orgId);
        item.setUserId(userId);
        item.setEventId(UUID.randomUUID());
        item.setAlertType("LOW_BATTERY");
        item.setSeverity("WARNING");
        item.setIncidentState("OPEN");
        item.setTitle("Test Alert");
        item.setBody("Test body");
        item.setIsRead(false);
        item.setIsAcknowledged(false);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }
}
