package com.yantrago.api.controller;

import com.yantrago.api.model.Notification;
import com.yantrago.api.model.NotificationPreference;
import com.yantrago.api.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Notification endpoints — list, get, dispatch notifications, manage preferences.
 *
 * GET /api/v1/notifications — list notifications (paged, filtered by tenant)
 * GET /api/v1/notifications/mine — list current user's notifications
 * GET /api/v1/notifications/{id} — get notification details
 * POST /api/v1/notifications/{id}/dispatch — dispatch a PENDING notification
 * GET /api/v1/notifications/preferences/{userId} — get user's notification preferences
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Page<Notification>> listNotifications(Pageable pageable) {
        return ResponseEntity.ok(notificationService.listNotifications(pageable));
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<Notification>> listMyNotifications(Pageable pageable) {
        return ResponseEntity.ok(notificationService.listMyNotifications(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    @PostMapping("/{id}/dispatch")
    public ResponseEntity<Notification> dispatchNotification(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.dispatchNotification(id));
    }

    @GetMapping("/preferences/{userId}")
    public ResponseEntity<List<NotificationPreference>> getUserPreferences(@PathVariable UUID userId) {
        return ResponseEntity.ok(notificationService.getUserPreferences(userId));
    }
}
