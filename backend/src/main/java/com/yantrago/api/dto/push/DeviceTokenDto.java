package com.yantrago.api.dto.push;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for device token operations.
 */
public record DeviceTokenDto(
        UUID id,
        String platform,
        String deviceLabel,
        String appVersion,
        Boolean isActive,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt
) {}
