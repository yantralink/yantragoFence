package com.yantrago.api.dto.notification;

import com.yantrago.api.model.NotificationInbox;
import com.yantrago.api.model.NotificationPreference;

/**
 * Maps between notification JPA entities and DTOs.
 *
 * Per AGENTS.md rule 21: DTOs separate from entities. Never return JPA entities from controllers.
 */
public final class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationInboxDto toDto(NotificationInbox entity) {
        if (entity == null) return null;
        return new NotificationInboxDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getUserId(),
                entity.getAlertId(),
                entity.getEventId(),
                entity.getAlertType(),
                entity.getSeverity(),
                entity.getIncidentState(),
                entity.getTitle(),
                entity.getBody(),
                entity.getMachineId(),
                entity.getObservedValue(),
                entity.getObservedUnit(),
                entity.getLocale(),
                entity.getTemplateVersion() != null ? entity.getTemplateVersion() : 1,
                Boolean.TRUE.equals(entity.getIsRead()),
                entity.getReadAt(),
                entity.getRecipientCustomerId(),
                entity.getRecipientCustomerName(),
                Boolean.TRUE.equals(entity.getIsAcknowledged()),
                entity.getAcknowledgedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static NotificationPreferenceDto toDto(NotificationPreference entity) {
        if (entity == null) return null;
        return new NotificationPreferenceDto(
                entity.getId(),
                entity.getUserId(),
                entity.getOrganizationId(),
                entity.getChannel(),
                entity.getAlertType(),
                Boolean.TRUE.equals(entity.getIsEnabled()),
                Boolean.TRUE.equals(entity.getPushEnabled()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
