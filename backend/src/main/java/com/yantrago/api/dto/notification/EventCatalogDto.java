package com.yantrago.api.dto.notification;

import java.util.List;

/**
 * DTO describing a supported notification event type for the preferences catalog.
 *
 * Per AGENTS.md rule 21: DTOs separate from entities. Never return JPA entities from controllers.
 */
public record EventCatalogDto(
        String eventType,
        String displayName,
        String description,
        List<String> channels
) {}
