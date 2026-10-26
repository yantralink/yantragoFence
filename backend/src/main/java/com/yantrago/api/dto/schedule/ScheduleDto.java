package com.yantrago.api.dto.schedule;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Response DTO for a machine's ON/OFF schedule.
 *
 * nextOnAt/nextOffAt are server-computed UTC instants so the mobile UI stays
 * "dumb" and never does schedule math on the phone clock. They are null when
 * the schedule is disabled.
 */
public record ScheduleDto(
        UUID id,
        UUID machineId,
        LocalTime onTime,
        LocalTime offTime,
        int daysOfWeek,
        String timezone,
        boolean enabled,
        LocalDateTime nextOnAt,
        LocalDateTime nextOffAt,
        ScheduleRunDto lastRun,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
