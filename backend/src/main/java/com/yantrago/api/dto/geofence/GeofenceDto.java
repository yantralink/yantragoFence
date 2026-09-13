package com.yantrago.api.dto.geofence;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for geofences.
 */
public record GeofenceDto(
        UUID id,
        UUID organizationId,
        UUID machineId,
        String name,
        Double latitude,
        Double longitude,
        Integer radiusMeters,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
