package com.yantrago.api.dto.analytics;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One FENCE_FAULT alert rendered as a timeline interval for the
 * Analytics screen. resolvedAt is null while the incident is open;
 * durationMinutes counts to now() in that case.
 */
public record FaultIntervalDto(
        UUID id,
        LocalDateTime triggeredAt,
        LocalDateTime resolvedAt,
        String incidentState,
        int occurrenceCount,
        long durationMinutes,
        boolean ongoing) {
}
