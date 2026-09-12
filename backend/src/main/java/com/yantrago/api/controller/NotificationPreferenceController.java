package com.yantrago.api.controller;

import com.yantrago.api.dto.notification.EventCatalogDto;
import com.yantrago.api.dto.notification.NotificationPreferenceDto;
import com.yantrago.api.service.NotificationPreferenceService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Notification preference endpoints — per-user channel preferences.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: RBAC on all endpoints.
 * Per AGENTS.md rule 21: returns DTOs, never JPA entities.
 * Per notification plan Phase 3: deterministic preference uniqueness.
 *
 * Endpoints:
 *   GET  /api/v1/notifications/preferences         — get current user's preferences
 *   GET  /api/v1/notifications/preferences/{userId} — get a specific user's preferences (own or super_admin)
 *   PUT  /api/v1/notifications/preferences          — set a preference for current user
 */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification_preference:read') or hasRole('super_admin')")
    public ResponseEntity<List<NotificationPreferenceDto>> getMyPreferences() {
        return ResponseEntity.ok(preferenceService.getMyPreferences());
    }

    /**
     * Returns the catalog of supported notification event types and their descriptions.
     * Per SIG 16: allows clients to discover configurable event types.
     */
    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('notification_preference:read') or hasRole('super_admin')")
    public ResponseEntity<List<EventCatalogDto>> getEventCatalog() {
        return ResponseEntity.ok(preferenceService.getEventCatalog());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('notification_preference:read') or hasRole('super_admin')")
    public ResponseEntity<List<NotificationPreferenceDto>> getUserPreferences(@PathVariable UUID userId) {
        return ResponseEntity.ok(preferenceService.getUserPreferences(userId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('notification_preference:write') or hasRole('super_admin')")
    public ResponseEntity<NotificationPreferenceDto> setPreference(@RequestBody PreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.setMyPreference(
                request.channel(), request.alertType(), request.isEnabled(),
                request.pushEnabled() != null ? request.pushEnabled() : true));
    }

    public record PreferenceRequest(
            @NotBlank String channel,
            String alertType,
            boolean isEnabled,
            Boolean pushEnabled
    ) {}
}
