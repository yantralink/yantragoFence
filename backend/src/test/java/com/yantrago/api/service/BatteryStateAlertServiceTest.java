package com.yantrago.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for BatteryStateAlertService — the battery-state transition detector.
 *
 * Semantics (matching the app's bulb tiles):
 *   {0, 20, 40, 60}% = FAULT    -> opens FENCE_FAULT (WARNING)
 *   other values     = NORMAL   -> resolves the fault incident silently
 *   null previous    -> first reading: establishes baseline, no notification
 *
 * MACHINE_CHARGING is never raised by this service — stale charging
 * incidents are only resolved silently as cleanup.
 *
 * Notifications fire only on state transitions. Repeated values at the same
 * state never notify. Resolutions are always silent (resolveIncidentSilently)
 * — the publishing resolveIncident path must never be used.
 */
class BatteryStateAlertServiceTest {

    private CanonicalAlertService canonicalAlertService;
    private BatteryStateAlertService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final Instant observedAt = Instant.now();

    @BeforeEach
    void setUp() {
        canonicalAlertService = mock(CanonicalAlertService.class);
        service = new BatteryStateAlertService(canonicalAlertService);
    }

    // ===== Baseline (previous == null) =====

    @Test
    @DisplayName("first-ever reading of a fault level establishes baseline — no notification")
    void firstReadingFault_isSilentBaseline() {
        service.evaluate(orgId, machineId, null, 40.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    @Test
    @DisplayName("first-ever reading of 100% establishes baseline — no notification")
    void firstReading100_isSilentBaseline() {
        service.evaluate(orgId, machineId, null, 100.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    @Test
    @DisplayName("first-ever reading of a normal value establishes baseline — no notification")
    void firstReadingNormal_isSilentBaseline() {
        service.evaluate(orgId, machineId, null, 80.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    // ===== Same-state repeats (dedup) =====

    @Test
    @DisplayName("20 -> 40 within FAULT produces nothing")
    void faultToFault_isDeduped() {
        service.evaluate(orgId, machineId, 20.0, 40.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    @Test
    @DisplayName("80 -> 100 within NORMAL produces nothing")
    void normalToNormal_isDeduped() {
        service.evaluate(orgId, machineId, 80.0, 100.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    @Test
    @DisplayName("10 -> 80 within NORMAL produces nothing (10% is Normal now)")
    void normalLevel10To80_isDeduped() {
        service.evaluate(orgId, machineId, 10.0, 80.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    // ===== Entry transitions =====

    @Test
    @DisplayName("80 -> 40 opens FENCE_FAULT and silently resolves MACHINE_CHARGING")
    void normalToFault_opensFault() {
        service.evaluate(orgId, machineId, 80.0, 40.0, observedAt);

        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("MACHINE_CHARGING"), anyString());
        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("FENCE_FAULT"), eq("WARNING"),
                anyString(), eq(observedAt), eq(40.0), eq("PERCENT"), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("80 -> 0 opens FENCE_FAULT (0% is a fault level)")
    void normalToZero_opensFault() {
        service.evaluate(orgId, machineId, 80.0, 0.0, observedAt);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("FENCE_FAULT"), eq("WARNING"),
                anyString(), eq(observedAt), eq(0.0), eq("PERCENT"), any());
    }

    @Test
    @DisplayName("100 -> 20 opens FENCE_FAULT (100% is Normal now)")
    void hundredToFaultLevel_opensFault() {
        service.evaluate(orgId, machineId, 100.0, 20.0, observedAt);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("FENCE_FAULT"), eq("WARNING"),
                anyString(), eq(observedAt), eq(20.0), eq("PERCENT"), any());
    }

    @Test
    @DisplayName("80 -> 10 produces nothing — 10% is Normal, never raises MACHINE_CHARGING")
    void normalToTen_isSilent() {
        service.evaluate(orgId, machineId, 80.0, 10.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    // ===== Exit transitions (silent resolves) =====

    @Test
    @DisplayName("20 -> 80 silently resolves both incidents (fault cleared)")
    void faultToNormal_resolvesBothSilently() {
        service.evaluate(orgId, machineId, 20.0, 80.0, observedAt);

        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("FENCE_FAULT"), anyString());
        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("MACHINE_CHARGING"), anyString());
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("60 -> 100 silently resolves both incidents (fault cleared at 100%)")
    void faultToHundred_resolvesBothSilently() {
        service.evaluate(orgId, machineId, 60.0, 100.0, observedAt);

        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("FENCE_FAULT"), anyString());
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ===== Unbound device =====

    @Test
    @DisplayName("null machineId (unbound device) is skipped silently")
    void unboundDevice_isSkipped() {
        service.evaluate(orgId, null, 40.0, 80.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    // ===== Re-notification after leaving and re-entering =====

    @Test
    @DisplayName("80 -> 20 -> 80 -> 20 re-opens incident so the second entry notifies")
    void reentryAfterNormal_opensFreshIncident() {
        service.evaluate(orgId, machineId, 80.0, 20.0, observedAt);
        service.evaluate(orgId, machineId, 20.0, 80.0, observedAt);
        service.evaluate(orgId, machineId, 80.0, 20.0, observedAt);

        // The resolve at 80 cleared the previous incident, so the second
        // entry into FAULT opens a fresh one — two processAlertEvent calls.
        verify(canonicalAlertService, times(2)).processAlertEvent(
                eq(machineId), eq("FENCE_FAULT"), eq("WARNING"),
                anyString(), eq(observedAt), eq(20.0), eq("PERCENT"), any());
    }
}
