package com.yantrago.api.dto.analytics;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A manual ON/OFF command rendered as a marker on the activity
 * timeline so users can compare issued commands against actual
 * ignition state.
 */
public record CommandMarkerDto(
        UUID id,
        String commandType,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime completedAt) {
}
