package com.yantrago.api.controller;

import com.yantrago.api.dto.notification.NotificationDto;
import com.yantrago.api.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Legacy notification endpoints — kept for backward compatibility but protected with RBAC.
 *
 * Per notification plan Phase 3: protect or deprecate legacy broad endpoints.
 * The new inbox endpoints are in NotificationInboxController and preference endpoints
 * are in NotificationPreferenceController.
 *
 * Endpoints:
 *   GET  /api/v1/notifications        — list all notifications in org (admin only)
 *   GET  /api/v1/notifications/{id}    — get a notification (tenant-guarded)
 *   POST /api/v1/notifications/{id}/dispatch — dispatch a PENDING notification (admin only)
 *
 * Note: The /mine and /preferences/{userId} endpoints are deprecated in favor of
 * NotificationInboxController and NotificationPreferenceController respectively.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Lists all notifications in the organization. Admin-only.
     * For user-facing inbox, use /api/v1/notifications/inbox instead.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('notification:read_all') or hasRole('super_admin')")
    public ResponseEntity<Page<NotificationDto>> listNotifications(Pageable pageable) {
        return ResponseEntity.ok(notificationService.listNotifications(pageable));
    }

    /**
     * Gets a single notification. Tenant-guarded.
     *
     * Note: The {id} path variable is constrained to UUID format to avoid
     * conflicts with sub-path controllers like NotificationPreferenceController
     * (/api/v1/notifications/preferences) and NotificationInboxController
     * (/api/v1/notifications/inbox). Without the regex, "preferences" and
     * "inbox" would be caught by this handler and fail UUID parsing.
     */
    @GetMapping("/{id:[a-fA-F0-9-]+}")
    @PreAuthorize("hasAuthority('notification:read') or hasRole('super_admin')")
    public ResponseEntity<NotificationDto> getNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    /**
     * Dispatches a PENDING notification via its configured channel.
     * Admin-only. Push delivery remains OFF in Phase 3.
     */
    @PostMapping("/{id:[a-fA-F0-9-]+}/dispatch")
    @PreAuthorize("hasAuthority('notification:write') or hasRole('super_admin')")
    public ResponseEntity<NotificationDto> dispatchNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.dispatchNotification(id));
    }
}
