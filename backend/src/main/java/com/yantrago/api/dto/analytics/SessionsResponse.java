package com.yantrago.api.dto.analytics;

import java.util.List;

/**
 * Activity payload for the Machine Activity card: derived ignition
 * sessions plus manual command markers for the same range.
 */
public record SessionsResponse(
        List<MachineSessionDto> sessions,
        List<CommandMarkerDto> commandMarkers) {
}
