package com.yantrago.api.service;

import com.yantrago.api.dto.analytics.FaultIntervalDto;
import com.yantrago.api.dto.analytics.MachineSessionDto;
import com.yantrago.api.dto.analytics.SessionsResponse;
import com.yantrago.api.model.Alert;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineCommand;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.repository.CommandRepository;
import com.yantrago.api.repository.LocationRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.TenantGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsService.
 *
 * Covers: FENCE_FAULT interval mapping (open vs resolved), ignition
 * session derivation (transitions, gap splitting, null handling,
 * range-boundary clamping, ongoing detection), command markers, and
 * tenant isolation.
 */
class AnalyticsServiceTest {

    private MachineRepository machineRepository;
    private AlertRepository alertRepository;
    private CommandRepository commandRepository;
    private LocationRepository locationRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private AnalyticsService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 26, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 9, 26, 23, 59);

    @BeforeEach
    void setUp() {
        machineRepository = mock(MachineRepository.class);
        alertRepository = mock(AlertRepository.class);
        commandRepository = mock(CommandRepository.class);
        locationRepository = mock(LocationRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        service = new AnalyticsService(machineRepository, alertRepository, commandRepository,
                locationRepository, ownerContextService, tenantGuard);

        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        Machine machine = new Machine();
        machine.setId(machineId);
        machine.setOrganizationId(orgId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        // Default: no DONE commands → sessions fall back to ignition bit.
        when(commandRepository
                .findTop1ByOrganizationIdAndMachineIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(commandRepository
                .findByOrganizationIdAndMachineIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                        any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    // ---------- faults ----------

    @Test
    @DisplayName("faults: resolved incident reports its real duration")
    void faults_resolvedIncident_hasDuration() {
        Alert a = alert(LocalDateTime.of(2026, 9, 26, 10, 0),
                LocalDateTime.of(2026, 9, 26, 10, 12), "RESOLVED");
        when(alertRepository.findIncidentsOverlapping(eq(orgId), eq(machineId),
                eq("FENCE_FAULT"), any(), any())).thenReturn(List.of(a));

        List<FaultIntervalDto> result = service.getFaultIntervals(machineId, FROM, TO);

        assertEquals(1, result.size());
        assertEquals(12, result.get(0).durationMinutes());
        assertFalse(result.get(0).ongoing());
        assertEquals("RESOLVED", result.get(0).incidentState());
    }

    @Test
    @DisplayName("faults: open incident is ongoing with null resolvedAt")
    void faults_openIncident_isOngoing() {
        Alert a = alert(LocalDateTime.of(2026, 9, 26, 10, 0), null, "OPEN");
        when(alertRepository.findIncidentsOverlapping(eq(orgId), eq(machineId),
                eq("FENCE_FAULT"), any(), any())).thenReturn(List.of(a));

        List<FaultIntervalDto> result = service.getFaultIntervals(machineId, FROM, TO);

        assertTrue(result.get(0).ongoing());
        assertNull(result.get(0).resolvedAt());
        assertTrue(result.get(0).durationMinutes() > 0);
    }

    @Test
    @DisplayName("faults: unknown machine throws")
    void faults_unknownMachine_throws() {
        when(machineRepository.findById(machineId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.getFaultIntervals(machineId, FROM, TO));
    }

    @Test
    @DisplayName("faults: tenant guard invoked with machine's org")
    void faults_validatesTenant() {
        when(alertRepository.findIncidentsOverlapping(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        service.getFaultIntervals(machineId, FROM, TO);
        verify(tenantGuard).validateTenantAccess(orgId);
    }

    // ---------- session derivation (pure function) ----------

    @Test
    @DisplayName("sessions: simple on→off produces one closed session")
    void sessions_simpleOnOff() {
        List<Map<String, Object>> points = List.of(
                pt(at(9, 0), false), pt(at(9, 5), true), pt(at(9, 10), true),
                pt(at(9, 15), false));

        List<MachineSessionDto> sessions =
                AnalyticsService.deriveSessions(points, FROM, TO);

        assertEquals(1, sessions.size());
        assertEquals(at(9, 5), sessions.get(0).startAt());
        assertEquals(at(9, 15), sessions.get(0).endAt());
        assertEquals(10, sessions.get(0).durationMinutes());
        assertFalse(sessions.get(0).ongoing());
    }

    @Test
    @DisplayName("sessions: gap >10min while ON splits into two sessions")
    void sessions_gapSplits() {
        List<Map<String, Object>> points = List.of(
                pt(at(9, 0), true), pt(at(9, 5), true),
                pt(at(9, 40), true), pt(at(9, 45), true),
                pt(at(9, 50), false));

        List<MachineSessionDto> sessions =
                AnalyticsService.deriveSessions(points, FROM, TO);

        // First point is ON → session start clamps to range start.
        assertEquals(2, sessions.size());
        assertEquals(FROM, sessions.get(0).startAt());
        assertEquals(at(9, 5), sessions.get(0).endAt());
        assertEquals(at(9, 40), sessions.get(1).startAt());
        assertEquals(at(9, 50), sessions.get(1).endAt());
    }

    @Test
    @DisplayName("sessions: first point already ON clamps start to range start")
    void sessions_clampsToFrom() {
        List<Map<String, Object>> points = List.of(
                pt(at(10, 0), true), pt(at(10, 10), false));

        List<MachineSessionDto> sessions =
                AnalyticsService.deriveSessions(points, FROM, TO);

        assertEquals(FROM, sessions.get(0).startAt());
        assertEquals(at(10, 10), sessions.get(0).endAt());
    }

    @Test
    @DisplayName("sessions: all-off or empty range yields no sessions")
    void sessions_allOff_empty() {
        assertTrue(AnalyticsService.deriveSessions(
                List.of(pt(at(9, 0), false), pt(at(9, 5), false)), FROM, TO).isEmpty());
        assertTrue(AnalyticsService.deriveSessions(List.of(), FROM, TO).isEmpty());
    }

    @Test
    @DisplayName("sessions: stale trailing ON point closes at last true point, not ongoing")
    void sessions_staleTail_notOngoing() {
        // last point is hours old → session closed, not "ongoing"
        List<Map<String, Object>> points = List.of(
                pt(at(1, 0), true), pt(at(1, 5), true));

        List<MachineSessionDto> sessions =
                AnalyticsService.deriveSessions(points, FROM, TO);

        assertEquals(1, sessions.size());
        assertFalse(sessions.get(0).ongoing());
        assertEquals(at(1, 5), sessions.get(0).endAt());
    }

    @Test
    @DisplayName("sessions: null ignition contributes to gap detection without toggling")
    void sessions_nullIsGap() {
        List<Map<String, Object>> points = List.of(
                pt(at(9, 0), true), pt(at(9, 4), null), pt(at(9, 8), true),
                pt(at(9, 30), true), pt(at(9, 34), false));

        List<MachineSessionDto> sessions =
                AnalyticsService.deriveSessions(points, FROM, TO);

        assertEquals(2, sessions.size()); // null didn't close; >10min gap did
        assertEquals(at(9, 8), sessions.get(0).endAt());
    }

    // ---------- command-based session derivation ----------

    @Test
    @DisplayName("command sessions: ON before range opens session at range start")
    void commandSessions_onBeforeRange() {
        List<MachineSessionDto> sessions = AnalyticsService.deriveSessionsFromCommands(
                cmd("ON", at(20, 0), null), List.of(), FROM);

        assertEquals(1, sessions.size());
        assertEquals(FROM, sessions.get(0).startAt());
        assertTrue(sessions.get(0).ongoing());
    }

    @Test
    @DisplayName("command sessions: ON then OFF in range closes at completion time")
    void commandSessions_onOffInRange() {
        List<MachineCommand> cmds = List.of(
                cmd("ON", at(9, 0), at(9, 0, 30)),
                cmd("OFF", at(10, 0), at(10, 0, 20)));

        List<MachineSessionDto> sessions =
                AnalyticsService.deriveSessionsFromCommands(null, cmds, FROM);

        assertEquals(1, sessions.size());
        assertEquals(at(9, 0, 30), sessions.get(0).startAt());
        assertEquals(at(10, 0, 20), sessions.get(0).endAt());
        assertFalse(sessions.get(0).ongoing());
    }

    @Test
    @DisplayName("command sessions: OFF before range then ON→OFF gives one bounded session")
    void commandSessions_offBeforeRange() {
        List<MachineSessionDto> sessions = AnalyticsService.deriveSessionsFromCommands(
                cmd("OFF", at(7, 0), null),
                List.of(cmd("ON", at(9, 0), at(9, 0, 5)),
                        cmd("OFF", at(9, 30), at(9, 30, 5))),
                FROM);

        assertEquals(1, sessions.size());
        assertEquals(at(9, 0, 5), sessions.get(0).startAt());
        assertEquals(at(9, 30, 5), sessions.get(0).endAt());
    }

    @Test
    @DisplayName("command sessions: duplicate ON commands are no-ops")
    void commandSessions_duplicateOn() {
        List<MachineCommand> cmds = List.of(
                cmd("ON", at(9, 0), at(9, 0, 10)),
                cmd("ON", at(9, 20), at(9, 20, 10)),
                cmd("OFF", at(10, 0), at(10, 0, 10)));

        List<MachineSessionDto> sessions =
                AnalyticsService.deriveSessionsFromCommands(null, cmds, FROM);

        assertEquals(1, sessions.size());
        assertEquals(at(9, 0, 10), sessions.get(0).startAt());
        assertEquals(at(10, 0, 10), sessions.get(0).endAt());
    }

    // ---------- sessions endpoint ----------

    @Test
    @DisplayName("sessions: endpoint bundles sessions and command markers")
    void sessions_bundlesMarkers() {
        when(locationRepository.findIgnitionHistory(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of(pt(at(9, 0), false), pt(at(9, 5), true), pt(at(9, 10), false)));

        MachineCommand cmd = new MachineCommand();
        cmd.setId(UUID.randomUUID());
        cmd.setCommandType("ON");
        cmd.setStatus("DONE");
        cmd.setCreatedAt(at(9, 4));
        when(commandRepository
                .findByOrganizationIdAndMachineIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of(cmd));

        SessionsResponse resp = service.getSessions(machineId, FROM, TO);

        assertEquals(1, resp.sessions().size());
        assertEquals(1, resp.commandMarkers().size());
        assertEquals("ON", resp.commandMarkers().get(0).commandType());
        assertEquals("DONE", resp.commandMarkers().get(0).status());
    }

    @Test
    @DisplayName("sessions: endpoint prefers command-derived sessions over ignition bit")
    void sessions_prefersCommands() {
        // ignition bit stuck OFF (unwired ACC) — commands still show the
        // real ON session.
        when(locationRepository.findIgnitionHistory(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of(pt(at(9, 0), false), pt(at(9, 5), false)));
        when(commandRepository
                .findByOrganizationIdAndMachineIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                        eq(orgId), eq(machineId), eq("DONE"), any(), any()))
                .thenReturn(List.of(
                        cmd("ON", at(8, 0), at(8, 0, 30)),
                        cmd("OFF", at(9, 0), at(9, 0, 30))));

        SessionsResponse resp = service.getSessions(machineId, FROM, TO);

        assertEquals(1, resp.sessions().size());
        assertEquals(at(8, 0, 30), resp.sessions().get(0).startAt());
        assertEquals(at(9, 0, 30), resp.sessions().get(0).endAt());
    }

    // ---------- helpers ----------

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 9, 26, hour, minute);
    }

    private static LocalDateTime at(int hour, int minute, int second) {
        return LocalDateTime.of(2026, 9, 26, hour, minute, second);
    }

    private static MachineCommand cmd(String type, LocalDateTime created,
                                      LocalDateTime completed) {
        MachineCommand c = new MachineCommand();
        c.setId(UUID.randomUUID());
        c.setCommandType(type);
        c.setStatus("DONE");
        c.setCreatedAt(created);
        c.setCompletedAt(completed);
        return c;
    }

    private static Map<String, Object> pt(LocalDateTime at, Boolean on) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("recorded_at", Timestamp.valueOf(at));
        row.put("ignition_on", on);
        return row;
    }

    private static Alert alert(LocalDateTime triggered, LocalDateTime resolved, String state) {
        Alert a = new Alert();
        a.setId(UUID.randomUUID());
        a.setOrganizationId(UUID.randomUUID());
        a.setMachineId(UUID.randomUUID());
        a.setAlertType("FENCE_FAULT");
        a.setSeverity("WARNING");
        a.setMessage("Fence fault detected");
        a.setTriggeredAt(triggered);
        a.setResolvedAt(resolved);
        a.setIncidentState(state);
        a.setOccurrenceCount(1);
        return a;
    }
}
