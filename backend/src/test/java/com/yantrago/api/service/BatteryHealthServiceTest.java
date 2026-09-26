package com.yantrago.api.service;

import com.yantrago.api.dto.analytics.BatteryHealthDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the rule-based battery health analysis — score bands,
 * declining-trend detection, projection, and insufficient-data guard.
 */
class BatteryHealthServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 20, 0, 0);

    @Test
    @DisplayName("stable healthy battery scores high with STABLE insight")
    void stableHealthy() {
        BatteryHealthDto dto = BatteryHealthService.analyze(ramp(12.85, 0.0, 100));

        assertEquals("HEALTHY", dto.status());
        assertEquals("STABLE", dto.insight());
        assertTrue(dto.score() >= 90, "score=" + dto.score());
        assertNull(dto.estimatedDaysUntilLow());
        assertTrue(dto.projection().isEmpty());
    }

    @Test
    @DisplayName("steady decline flags DECLINING with an estimate")
    void steadyDecline() {
        // ~0.5 V/day decline over 4 days → 12.02 V now, essentially at
        // the low band → estimate ~0 days, no projection line needed.
        BatteryHealthDto dto = BatteryHealthService.analyze(ramp(14.0, -0.5, 96));

        assertEquals("DECLINING", dto.insight());
        assertTrue(dto.estimatedDaysUntilLow() != null && dto.estimatedDaysUntilLow() <= 1,
                "days=" + dto.estimatedDaysUntilLow());
    }

    @Test
    @DisplayName("early-stage decline projects down to the low band")
    void earlyDeclineProjection() {
        // ~0.2 V/day decline from 13.2 → 12.4 V now, ~3 days until low.
        BatteryHealthDto dto = BatteryHealthService.analyze(ramp(13.2, -0.2, 96));

        assertEquals("DECLINING", dto.insight());
        assertNotNull(dto.estimatedDaysUntilLow());
        assertFalse(dto.projection().isEmpty());
        assertEquals(BatteryHealthService.BAND_MIN,
                dto.projection().get(dto.projection().size() - 1).voltage(), 0.01);
    }

    @Test
    @DisplayName("noisy non-trend data does not produce a projection")
    void noisyNoProjection() {
        // Alternating ±0.4 V — large variance, zero net slope.
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 96; i++) {
            rows.add(row(T0.plusHours(i), 12.6 + (i % 2 == 0 ? 0.4 : -0.4)));
        }
        BatteryHealthDto dto = BatteryHealthService.analyze(rows);

        assertEquals("STABLE", dto.insight());
        assertNull(dto.estimatedDaysUntilLow());
    }

    @Test
    @DisplayName("critically low latest voltage wins over other signals")
    void criticallyLow() {
        BatteryHealthDto dto = BatteryHealthService.analyze(ramp(10.8, 0.0, 40));

        assertEquals("CRITICAL", dto.status());
        assertEquals("CRITICALLY_LOW", dto.insight());
        assertTrue(dto.score() < 30, "score=" + dto.score());
    }

    @Test
    @DisplayName("fewer than 5 points yields INSUFFICIENT_DATA")
    void insufficientData() {
        BatteryHealthDto dto = BatteryHealthService.analyze(ramp(12.7, 0.0, 3));

        assertEquals("INSUFFICIENT_DATA", dto.insight());
        assertEquals(-1, dto.score());
        assertNull(dto.latestVoltage());
    }

    @Test
    @DisplayName("empty input is tolerated")
    void empty() {
        BatteryHealthDto dto = BatteryHealthService.analyze(List.of());
        assertEquals("INSUFFICIENT_DATA", dto.status());
    }

    @Test
    @DisplayName("above-band latest voltage reports HIGH")
    void highVoltage() {
        BatteryHealthDto dto = BatteryHealthService.analyze(ramp(14.5, 0.0, 30));
        assertEquals("HIGH", dto.status());
        assertEquals("OVERVOLTAGE", dto.insight());
    }

    @Test
    @DisplayName("already-below-band decline clamps estimate at 0 days")
    void belowBandDecline() {
        // Declining and already under the band (but above critical) →
        // "≈0 days", never negative. Latest ≈11.1 V.
        BatteryHealthDto dto = BatteryHealthService.analyze(ramp(12.3, -0.3, 96));

        assertEquals("DECLINING", dto.insight());
        assertNotNull(dto.estimatedDaysUntilLow());
        assertTrue(dto.estimatedDaysUntilLow() >= 0);
    }

    // ---------- helpers ----------

    /** n points, hourly, starting at startV with vPerHour... slope per hour = slopePerDay/24. */
    private static List<Map<String, Object>> ramp(double startV, double vPerDay, int n) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(row(T0.plusHours(i), startV + vPerDay * i / 24.0));
        }
        return rows;
    }

    private static Map<String, Object> row(LocalDateTime at, double volts) {
        Map<String, Object> r = new HashMap<>();
        r.put("recorded_at", Timestamp.valueOf(at));
        r.put("voltage", volts);
        return r;
    }
}
