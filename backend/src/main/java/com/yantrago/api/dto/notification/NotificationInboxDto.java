package com.yantrago.api.dto.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for notification inbox items — returned by REST controllers.
 *
 * Per AGENTS.md rule 21: DTOs separate from entities. Never return JPA entities from controllers.
 * Per notification plan Phase 3: read-state DTOs.
 */
public record NotificationInboxDto(
        UUID id,
        UUID organizationId,
        UUID userId,
        UUID alertId,
        UUID eventId,
        String alertType,
        String severity,
        String incidentState,
        String title,
        String body,
        UUID machineId,
        Double observedValue,
        String observedUnit,
        String locale,
        int templateVersion,
        boolean isRead,
        LocalDateTime readAt,
        UUID recipientCustomerId,
        String recipientCustomerName,
        boolean isAcknowledged,
        LocalDateTime acknowledgedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
