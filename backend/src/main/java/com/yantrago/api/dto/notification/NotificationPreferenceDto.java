package com.yantrago.api.dto.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for notification preferences — returned by REST controllers.
 *
 * Per AGENTS.md rule 21: DTOs separate from entities. Never return JPA entities from controllers.
 */
public record NotificationPreferenceDto(
        UUID id,
        UUID userId,
        UUID organizationId,
        String channel,
        String alertType,
        boolean isEnabled,
        boolean pushEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
