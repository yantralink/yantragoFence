package com.yantrago.api.controller;

import com.yantrago.api.dto.notification.NotificationInboxDto;
import com.yantrago.api.service.NotificationInboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Notification inbox endpoints — user-facing inbox with RBAC and private access.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: RBAC on all endpoints.
 * Per AGENTS.md rule 21: returns DTOs, never JPA entities.
 * Per notification plan Phase 3: read state is private and idempotent.
 * Per notification plan Phase 4: authorized acknowledgement.
 *
 * Endpoints:
 *   GET  /api/v1/notifications/inbox        — list current user's notifications (paged, filtered)
 *   GET  /api/v1/notifications/inbox/{id}    — get a single notification (private access)
 *   GET  /api/v1/notifications/inbox/unread-count — get unread count for current user
 *   POST /api/v1/notifications/inbox/{id}/read   — mark a notification as read
 *   POST /api/v1/notifications/inbox/read-all    — mark all as read
 *   POST /api/v1/notifications/inbox/{id}/acknowledge — acknowledge a notification
 */
@RestController
@RequestMapping("/api/v1/notifications/inbox")
public class NotificationInboxController {

    private final NotificationInboxService inboxService;

    public NotificationInboxController(NotificationInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification:read') or hasRole('super_admin')")
    public ResponseEntity<Page<NotificationInboxDto>> listMyNotifications(
            Pageable pageable,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String alertType) {
        return ResponseEntity.ok(inboxService.listMyNotifications(pageable, unreadOnly, alertType));
    }

    @GetMapping("/{id:[a-fA-F0-9-]+}")
    @PreAuthorize("hasAuthority('notification:read') or hasRole('super_admin')")
    public ResponseEntity<NotificationInboxDto> getNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(inboxService.getNotification(id));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:read') or hasRole('super_admin')")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", inboxService.countUnread()));
    }

    @PostMapping("/{id:[a-fA-F0-9-]+}/read")
    @PreAuthorize("hasAuthority('notification:mark_read') or hasRole('super_admin')")
    public ResponseEntity<NotificationInboxDto> markAsRead(@PathVariable UUID id) {
        return ResponseEntity.ok(inboxService.markAsRead(id));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:mark_read') or hasRole('super_admin')")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        return ResponseEntity.ok(Map.of("markedRead", inboxService.markAllAsRead()));
    }

    @PostMapping("/{id:[a-fA-F0-9-]+}/acknowledge")
    @PreAuthorize("hasAuthority('notification:mark_read') or hasRole('super_admin')")
    public ResponseEntity<NotificationInboxDto> acknowledge(@PathVariable UUID id) {
        return ResponseEntity.ok(inboxService.acknowledge(id));
    }
}
