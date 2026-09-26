package com.yantrago.api.dto.analytics;

import java.time.LocalDateTime;

/**
 * One ignition-ON session derived from location_history.ignition_on.
 * endAt is null and ongoing=true while the session is still open at
 * the end of the queried range (and the device is still reporting).
 */
public record MachineSessionDto(
        LocalDateTime startAt,
        LocalDateTime endAt,
        long durationMinutes,
        boolean ongoing) {
}
