package com.yantrago.api.dto.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for legacy notification records (the original notifications table).
 *
 * Per AGENTS.md rule 21: DTOs separate from entities. Never return JPA entities from controllers.
 * Fields mirror the Notification entity (notifications table).
 */
public record NotificationDto(
        UUID id,
        UUID organizationId,
        UUID userId,
        UUID alertId,
        String channel,
        String title,
        String body,
        String status,
        String provider,
        String providerMessageId,
        String error,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
