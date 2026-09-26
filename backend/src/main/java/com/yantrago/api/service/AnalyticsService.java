package com.yantrago.api.service;

import com.yantrago.api.dto.analytics.CommandMarkerDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analytics service — historical views for the Analytics tab.
 *
 * getFaultIntervals: FENCE_FAULT incidents from the alerts table,
 * overlapping the requested range.
 *
 * getSessions: ON sessions derived primarily from DONE machine
 * commands — on this hardware the device ACK is the only confirmed
 * fence-energized signal (location_history.ignition_on carries the GPS
 * ACC-wire bit, which is not wired on deployed units and stays false
 * even while the fence is on). When a machine has no commands at all,
 * sessions fall back to ignition_on transitions for ACC-wired units.
 *
 * Per AGENTS.md rule 7/8: tenant isolation uses organizationId from the
 * JWT context (OwnerContextService), never from the request.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private static final String ALERT_TYPE_FENCE_FAULT = "FENCE_FAULT";
    private static final String STATUS_DONE = "DONE";

    /** Max gap between ignition-ON points that still counts as one session. */
    private static final Duration SESSION_GAP = Duration.ofMinutes(10);

    private final MachineRepository machineRepository;
    private final AlertRepository alertRepository;
    private final CommandRepository commandRepository;
    private final LocationRepository locationRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public AnalyticsService(MachineRepository machineRepository,
                            AlertRepository alertRepository,
                            CommandRepository commandRepository,
                            LocationRepository locationRepository,
                            OwnerContextService ownerContextService,
                            TenantGuard tenantGuard) {
        this.machineRepository = machineRepository;
        this.alertRepository = alertRepository;
        this.commandRepository = commandRepository;
        this.locationRepository = locationRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    /**
     * FENCE_FAULT incidents overlapping [from, to], oldest first.
     * Open incidents report resolvedAt=null and ongoing=true.
     */
    @Transactional(readOnly = true)
    public List<FaultIntervalDto> getFaultIntervals(UUID machineId, LocalDateTime from, LocalDateTime to) {
        UUID orgId = validateMachineAccess(machineId);
        LocalDateTime now = LocalDateTime.now();

        return alertRepository
                .findIncidentsOverlapping(orgId, machineId, ALERT_TYPE_FENCE_FAULT, from, to)
                .stream()
                .map(a -> {
                    LocalDateTime end = a.getResolvedAt() != null ? a.getResolvedAt() : now;
                    return new FaultIntervalDto(
                            a.getId(),
                            a.getTriggeredAt(),
                            a.getResolvedAt(),
                            a.getIncidentState(),
                            a.getOccurrenceCount() != null ? a.getOccurrenceCount() : 1,
                            Duration.between(a.getTriggeredAt(), end).toMinutes(),
                            a.getResolvedAt() == null);
                })
                .toList();
    }

    /**
     * ON sessions plus manual command markers for [from, to].
     */
    @Transactional(readOnly = true)
    public SessionsResponse getSessions(UUID machineId, LocalDateTime from, LocalDateTime to) {
        UUID orgId = validateMachineAccess(machineId);

        List<MachineCommand> doneBefore = commandRepository
                .findTop1ByOrganizationIdAndMachineIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
                        orgId, machineId, STATUS_DONE, from)
                .map(List::of)
                .orElse(List.of());
        List<MachineCommand> doneInRange = commandRepository
                .findByOrganizationIdAndMachineIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                        orgId, machineId, STATUS_DONE, from, to);

        List<MachineSessionDto> sessions;
        if (!doneBefore.isEmpty() || !doneInRange.isEmpty()) {
            sessions = deriveSessionsFromCommands(
                    doneBefore.isEmpty() ? null : doneBefore.get(0), doneInRange, from);
        } else {
            // ACC-wired trackers: fall back to the ignition bit.
            sessions = deriveSessions(
                    locationRepository.findIgnitionHistory(orgId, machineId, from, to), from, to);
        }

        List<CommandMarkerDto> markers = commandRepository
                .findByOrganizationIdAndMachineIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        orgId, machineId, from, to)
                .stream()
                .map(AnalyticsService::toMarker)
                .toList();

        return new SessionsResponse(sessions, markers);
    }

    private UUID validateMachineAccess(UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        return orgId;
    }

    /**
     * Walks ordered ignition points and emits closed/open sessions.
     *
     * - ignition_on == true  → opens a session (or continues one)
     * - ignition_on == false → closes an open session at that point
     * - ignition_on == null  → unknown state; treated like a reporting
     *   gap (does not open or close, contributes to gap detection)
     * - gap between consecutive points > SESSION_GAP closes an open
     *   session at the last true point
     * - session still open at the last point → ongoing when that point
     *   is fresher than SESSION_GAP, otherwise closed at it
     * - first point already true → start is clamped to `from` since the
     *   session likely began before the range
     */
    static List<MachineSessionDto> deriveSessions(List<Map<String, Object>> points,
                                                  LocalDateTime from, LocalDateTime to) {
        List<MachineSessionDto> sessions = new ArrayList<>();
        LocalDateTime sessionStart = null;
        LocalDateTime lastTrueAt = null;
        LocalDateTime prevAt = null;

        for (Map<String, Object> row : points) {
            LocalDateTime at = toLocalDateTime(row.get("recorded_at"));
            Boolean on = toBoolean(row.get("ignition_on"));
            if (at == null) continue;

            boolean gap = prevAt != null && Duration.between(prevAt, at).compareTo(SESSION_GAP) > 0;
            if (gap && sessionStart != null) {
                sessions.add(closedSession(sessionStart, lastTrueAt != null ? lastTrueAt : prevAt));
                sessionStart = null;
                lastTrueAt = null;
            }

            if (Boolean.TRUE.equals(on)) {
                if (sessionStart == null) {
                    // First point of the range already ON → the session
                    // may have begun before `from`; clamp to the boundary.
                    sessionStart = prevAt == null ? from : at;
                }
                lastTrueAt = at;
            } else if (Boolean.FALSE.equals(on) && sessionStart != null) {
                sessions.add(closedSession(sessionStart, at));
                sessionStart = null;
                lastTrueAt = null;
            }
            prevAt = at;
        }

        if (sessionStart != null) {
            LocalDateTime lastPoint = prevAt;
            boolean fresh = lastPoint != null
                    && Duration.between(lastPoint, LocalDateTime.now()).compareTo(SESSION_GAP) <= 0;
            if (fresh) {
                sessions.add(new MachineSessionDto(sessionStart, null,
                        Duration.between(sessionStart, LocalDateTime.now()).toMinutes(), true));
            } else {
                sessions.add(closedSession(sessionStart,
                        lastTrueAt != null ? lastTrueAt : lastPoint));
            }
        }

        return sessions;
    }

    /**
     * Derives ON sessions from DONE ON/OFF command transitions.
     *
     * - `lastBefore` is the most recent DONE command before `from`; an
     *   ON-type there means the fence entered the range energized, so
     *   the session opens at the range boundary.
     * - An ON command opens a session at its completion time (device-
     *   confirmed, not requested time); OFF closes it.
     * - Duplicate ON/ON or OFF/OFF commands are no-ops.
     * - A session still open after the last command is ongoing — the
     *   fence stays energized until a confirmed OFF arrives.
     */
    static List<MachineSessionDto> deriveSessionsFromCommands(
            MachineCommand lastBefore, List<MachineCommand> inRange, LocalDateTime from) {
        List<MachineSessionDto> sessions = new ArrayList<>();
        LocalDateTime sessionStart = isOnCommand(lastBefore) ? from : null;

        for (MachineCommand cmd : inRange) {
            LocalDateTime at = cmd.getCompletedAt() != null ? cmd.getCompletedAt() : cmd.getCreatedAt();
            if (isOnCommand(cmd) && sessionStart == null) {
                sessionStart = at;
            } else if (!isOnCommand(cmd) && sessionStart != null) {
                sessions.add(closedSession(sessionStart, at));
                sessionStart = null;
            }
        }

        if (sessionStart != null) {
            sessions.add(new MachineSessionDto(sessionStart, null,
                    Duration.between(sessionStart, LocalDateTime.now()).toMinutes(), true));
        }
        return sessions;
    }

    private static boolean isOnCommand(MachineCommand cmd) {
        if (cmd == null || cmd.getCommandType() == null) return false;
        return cmd.getCommandType().equals("ON") || cmd.getCommandType().equals("FENCING_ON");
    }

    private static MachineSessionDto closedSession(LocalDateTime start, LocalDateTime end) {
        if (end == null) end = start;
        long minutes = Math.max(0, Duration.between(start, end).toMinutes());
        return new MachineSessionDto(start, end, minutes, false);
    }

    private static CommandMarkerDto toMarker(MachineCommand c) {
        return new CommandMarkerDto(
                c.getId(), c.getCommandType(), c.getStatus(),
                c.getCreatedAt(), c.getCompletedAt());
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime t) return t;
        return null;
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return null;
    }
}
