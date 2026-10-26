package com.yantrago.api.dto.schedule;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for one schedule run (an ON or OFF execution record).
 * scheduledSlot is stored in UTC (timestamptz), consistent with all
 * timestamps in the API.
 */
public record ScheduleRunDto(
        UUID id,
        LocalDateTime scheduledSlot,
        String commandType,
        String status,
        UUID commandId,
        Integer attemptCount,
        String error,
        LocalDateTime updatedAt
) {}
