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
 *   10%  = CHARGING  -> opens MACHINE_CHARGING (INFO)
 *   100% = FAULT     -> opens FENCE_FAULT (WARNING)
 *   other values     -> NORMAL   -> resolves both incidents silently
 *   null previous    -> first reading: establishes baseline, no notification
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
    @DisplayName("first-ever reading of 10% establishes baseline — no notification")
    void firstReading10_isSilentBaseline() {
        service.evaluate(orgId, machineId, null, 10.0, observedAt);

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
        service.evaluate(orgId, machineId, null, 60.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    // ===== Same-state repeats (dedup) =====

    @Test
    @DisplayName("10 -> 10 repeated heartbeat produces nothing")
    void chargingToCharging_isDeduped() {
        service.evaluate(orgId, machineId, 10.0, 10.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    @Test
    @DisplayName("100 -> 100 repeated heartbeat produces nothing")
    void faultToFault_isDeduped() {
        service.evaluate(orgId, machineId, 100.0, 100.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    @Test
    @DisplayName("20 -> 60 within NORMAL produces nothing")
    void normalToNormal_isDeduped() {
        service.evaluate(orgId, machineId, 20.0, 60.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    // ===== Entry transitions =====

    @Test
    @DisplayName("10 -> 100 opens FENCE_FAULT and silently resolves MACHINE_CHARGING")
    void chargingToFault_opensFault() {
        service.evaluate(orgId, machineId, 10.0, 100.0, observedAt);

        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("MACHINE_CHARGING"), anyString());
        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("FENCE_FAULT"), eq("WARNING"),
                anyString(), eq(observedAt), eq(100.0), eq("PERCENT"), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("100 -> 10 opens MACHINE_CHARGING and silently resolves FENCE_FAULT")
    void faultToCharging_opensCharging() {
        service.evaluate(orgId, machineId, 100.0, 10.0, observedAt);

        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("FENCE_FAULT"), anyString());
        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("MACHINE_CHARGING"), eq("INFO"),
                anyString(), eq(observedAt), eq(10.0), eq("PERCENT"), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("NORMAL -> 10 opens MACHINE_CHARGING")
    void normalToCharging_opensCharging() {
        service.evaluate(orgId, machineId, 20.0, 10.0, observedAt);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("MACHINE_CHARGING"), eq("INFO"),
                anyString(), eq(observedAt), eq(10.0), eq("PERCENT"), any());
    }

    @Test
    @DisplayName("NORMAL -> 100 opens FENCE_FAULT")
    void normalToFault_opensFault() {
        service.evaluate(orgId, machineId, 60.0, 100.0, observedAt);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("FENCE_FAULT"), eq("WARNING"),
                anyString(), eq(observedAt), eq(100.0), eq("PERCENT"), any());
    }

    // ===== Exit transitions (silent resolves) =====

    @Test
    @DisplayName("10 -> 20 silently resolves both incidents (charging stopped)")
    void chargingToNormal_resolvesBothSilently() {
        service.evaluate(orgId, machineId, 10.0, 20.0, observedAt);

        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("MACHINE_CHARGING"), anyString());
        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("FENCE_FAULT"), anyString());
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("100 -> 40 silently resolves both incidents (fault cleared)")
    void faultToNormal_resolvesBothSilently() {
        service.evaluate(orgId, machineId, 100.0, 40.0, observedAt);

        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("MACHINE_CHARGING"), anyString());
        verify(canonicalAlertService).resolveIncidentSilently(
                eq(orgId), eq(machineId), eq("FENCE_FAULT"), anyString());
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ===== Unbound device =====

    @Test
    @DisplayName("null machineId (unbound device) is skipped silently")
    void unboundDevice_isSkipped() {
        service.evaluate(orgId, null, 10.0, 100.0, observedAt);

        verifyNoInteractions(canonicalAlertService);
    }

    // ===== Re-notification after leaving and re-entering =====

    @Test
    @DisplayName("10 -> 20 -> 10 re-opens incident so the second 10 notifies")
    void reentryAfterNormal_opensFreshIncident() {
        service.evaluate(orgId, machineId, 10.0, 20.0, observedAt);
        service.evaluate(orgId, machineId, 20.0, 10.0, observedAt);

        // Second entry into CHARGING opens a (fresh) incident — the resolve at
        // 20 cleared the previous one, so processAlertEvent creates a new OPEN.
        verify(canonicalAlertService, times(1)).processAlertEvent(
                eq(machineId), eq("MACHINE_CHARGING"), eq("INFO"),
                anyString(), eq(observedAt), eq(10.0), eq("PERCENT"), any());
    }
}
