package com.yantrago.api.dto.analytics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rule-based battery health analysis for the Analytics card.
 *
 * Honest scope: computed from voltage_readings only. This is NOT
 * machine learning — true end-of-life prediction needs charge current
 * and temperature data the tracker does not report.
 *
 * @param score                 0–100 composite (resting voltage, variance,
 *                              time below 12 V, declining trend)
 * @param status                HEALTHY / LOW / HIGH / CRITICAL band of the
 *                              latest reading, or DECLINING when a strong
 *                              downward trend exists
 * @param insight               STABLE / DECLINING / CRITICALLY_LOW /
 *                              INSUFFICIENT_DATA — maps to a localized
 *                              sentence on the card
 * @param latestVoltage         most recent reading, null when no data
 * @param slopeMvPerDay         least-squares slope in millivolts/day;
 *                              null when the range is too short
 * @param estimatedDaysUntilLow days until voltage crosses the low band,
 *                              only when a consistent decline exists
 * @param projection            dotted-line points the chart overlays,
 *                              daily steps until the low band or +7 days
 */
public record BatteryHealthDto(
        int score,
        String status,
        String insight,
        Double latestVoltage,
        Double slopeMvPerDay,
        Integer estimatedDaysUntilLow,
        List<ProjectionPoint> projection) {

    public record ProjectionPoint(LocalDateTime at, double voltage) {
    }
}
