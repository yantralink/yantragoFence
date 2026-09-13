package com.yantrago.api.dto.theft;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for customer theft protection defaults.
 */
public record CustomerSettingsDto(
        UUID id,
        UUID customerId,
        Integer defaultGeofenceRadiusMeters,
        Integer defaultSpeedThresholdKmh,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
