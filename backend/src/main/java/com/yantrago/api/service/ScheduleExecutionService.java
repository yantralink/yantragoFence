package com.yantrago.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.dto.command.CommandResponse;
import com.yantrago.api.model.Device;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineCommand;
import com.yantrago.api.model.MachineSchedule;
import com.yantrago.api.model.ScheduleRun;
import com.yantrago.api.repository.CommandRepository;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.repository.MachineScheduleRepository;
import com.yantrago.api.repository.ScheduleRunRepository;
import com.yantrago.shared.queue.AlertTransitionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes scheduled ON/OFF slots on top of the existing audited command
 * pipeline (rules 4, 5, 6 — never bypasses CommandService.dispatch).
 *
 * Slot claiming: INSERT ... ON CONFLICT DO NOTHING against the
 * (schedule_id, scheduled_slot, command_type) unique constraint — atomic,
 * idempotent across 30s ticks, restarts and multiple instances.
 *
 * Retry + timeout ownership (verified): the gateway fails immediately when a
 * device is offline and has NO retry loop, and no backend component times out
 * stuck commands. This service owns both:
 *  - retry pass: a run whose linked command terminally failed is re-dispatched
 *    as a NEW machine_command (full audit) every retry-interval, up to
 *    max-attempts, only while still inside its cycle;
 *  - reconciliation sweep: a DISPATCHED run whose command never reached a
 *    terminal state within dispatch-timeout is transitioned to TIMEOUT and
 *    then handled by the retry pass.
 *
 * Fail-safe asymmetry: ON slots older than the catch-up window are recorded
 * MISSED and never fired late; OFF slots fire late (bounded by the most
 * recent ON slot) — a de-energized fence is the safe default.
 */
@Service
public class ScheduleExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleExecutionService.class);

    public static final String RUN_PENDING = "PENDING";
    public static final String RUN_DISPATCHED = "DISPATCHED";
    public static final String RUN_ACKED = "ACKED";
    public static final String RUN_FAILED = "FAILED";
    public static final String RUN_MISSED = "MISSED";

    private static final List<String> TERMINAL_COMMAND_STATES = List.of("DONE", "FAILED", "TIMEOUT");

    private final ScheduleRunRepository runRepository;
    private final MachineScheduleRepository scheduleRepository;
    private final MachineRepository machineRepository;
    private final DeviceRepository deviceRepository;
    private final CommandRepository commandRepository;
    private final CommandService commandService;
    private final CommandStateMachine stateMachine;
    private final ScheduleTimeService timeService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final int maxAttempts;

    public ScheduleExecutionService(ScheduleRunRepository runRepository,
                                    MachineScheduleRepository scheduleRepository,
                                    MachineRepository machineRepository,
                                    DeviceRepository deviceRepository,
                                    CommandRepository commandRepository,
                                    CommandService commandService,
                                    CommandStateMachine stateMachine,
                                    ScheduleTimeService timeService,
                                    JdbcTemplate jdbcTemplate,
                                    ObjectMapper objectMapper,
                                    @Value("${schedule.max-attempts:3}") int maxAttempts) {
        this.runRepository = runRepository;
        this.scheduleRepository = scheduleRepository;
        this.machineRepository = machineRepository;
        this.deviceRepository = deviceRepository;
        this.commandRepository = commandRepository;
        this.commandService = commandService;
        this.stateMachine = stateMachine;
        this.timeService = timeService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Claims and processes one due slot. ON slots may be marked MISSED
     * instead of dispatched when they are older than the catch-up window.
     *
     * Intentionally NOT @Transactional: the claim insert must commit
     * immediately (it IS the lock), and dispatch runs in its own transaction
     * so its afterCommit RabbitMQ publish works. A transactional wrapper
     * would also be poisoned rollback-only by an inner @Transactional method
     * throwing — nothing (including the FAILED marking below) would persist.
     *
     * Dispatch-time failures (e.g. device unbound) are recorded as FAILED —
     * they are configuration problems, not transient device errors, so the
     * retry pass is not used for them.
     */
    public void processSlot(MachineSchedule schedule, Instant slot, String commandType, boolean fireable) {
        LocalDateTime slotUtc = LocalDateTime.ofInstant(slot, ZoneOffset.UTC);
        Optional<ScheduleRun> claimed = claimSlot(schedule.getId(), slotUtc, commandType);
        if (claimed.isEmpty()) {
            return; // already claimed by a previous tick — idempotent
        }
        ScheduleRun run = claimed.get();

        if (!fireable) {
            run.setStatus(RUN_MISSED);
            run.setError("Slot missed beyond ON catch-up window — not fired for safety");
            runRepository.save(run);
            log.warn("Schedule {} slot {} {} missed — too old to fire safely",
                    schedule.getId(), slotUtc, commandType);
            emitEvent(schedule, "SCHEDULED_COMMAND_MISSED", "WARNING", "MISSED",
                    String.format("Scheduled %s for machine was missed (%s UTC) and was NOT executed for safety.",
                            commandType, slotUtc), commandType, run.getId());
            return;
        }

        // Pre-flight: no device bound → permanent configuration error, mark
        // FAILED without calling dispatch (which would throw and leave the
        // slot re-claimable for an infinite retry loop).
        if (!hasBoundDevice(schedule.getMachineId())) {
            run.setStatus(RUN_FAILED);
            run.setError("No device with IMEI bound to machine: " + schedule.getMachineId());
            runRepository.save(run);
            log.error("Scheduled {} failed for schedule={} — no device bound to machine={}",
                    commandType, schedule.getId(), schedule.getMachineId());
            emitEvent(schedule, "SCHEDULED_COMMAND_FAILED", "WARNING", "FAILED",
                    String.format("Scheduled %s could not be dispatched — no device is bound to the machine. Fence state is UNCHANGED.",
                            commandType), commandType, run.getId());
            return;
        }

        try {
            Machine machine = machineRepository.findById(schedule.getMachineId())
                    .orElseThrow(() -> new IllegalStateException("Machine not found: " + schedule.getMachineId()));
            MachineCommand command = commandService.dispatch(
                    machine, commandType, schedule.getCreatedBy(),
                    CommandService.SOURCE_SCHEDULE, schedule.getId());
            run.setStatus(RUN_DISPATCHED);
            run.setCommandId(command.getId());
            run.setAttemptCount(run.getAttemptCount() + 1);
            runRepository.save(run);
            log.info("Dispatched scheduled {} for schedule={} machine={} slot={} command={}",
                    commandType, schedule.getId(), schedule.getMachineId(), slotUtc, command.getId());
        } catch (Exception e) {
            run.setStatus(RUN_FAILED);
            run.setError("Dispatch failed: " + e.getMessage());
            runRepository.save(run);
            log.error("Scheduled dispatch failed schedule={} type={} slot={}: {}",
                    schedule.getId(), commandType, slotUtc, e.getMessage());
            emitEvent(schedule, "SCHEDULED_COMMAND_FAILED", "WARNING", "FAILED",
                    String.format("Scheduled %s could not be dispatched (%s). Fence state is UNCHANGED.",
                            commandType, e.getMessage()), commandType, run.getId());
        }
    }

    /**
     * Returns true when the ON cycle paired with this OFF slot was actually
     * claimed (a run row exists). OFF catch-up only applies to cycles that
     * started — a freshly created schedule must not immediately de-energize
     * a manually-operated fence.
     */
    public boolean hasOnRunForCycle(MachineSchedule schedule, Instant offInstant) {
        return timeService.pairedOnInstant(schedule, offInstant)
                .map(on -> runRepository.existsByScheduleIdAndScheduledSlotAndCommandType(
                        schedule.getId(), LocalDateTime.ofInstant(on, ZoneOffset.UTC), "ON"))
                .orElse(false);
    }

    /**
     * Called by CommandResultConsumer when a scheduled command reaches a
     * lifecycle state. DONE completes the run; FAILED/TIMEOUT leaves it
     * DISPATCHED so the retry pass can re-dispatch.
     */
    @Transactional
    public void onScheduledCommandResult(CommandResponse command, String status, String error) {
        runRepository.findByCommandId(command.getId()).ifPresent(run -> {
            if ("DONE".equals(status)) {
                run.setStatus(RUN_ACKED);
                runRepository.save(run);
                MachineSchedule schedule = loadSchedule(run.getScheduleId());
                emitEvent(schedule,
                        "ON".equals(command.getCommandType()) ? "SCHEDULED_ON_EXECUTED" : "SCHEDULED_OFF_EXECUTED",
                        "INFO", "DONE",
                        String.format("Fence turned %s by schedule (slot %s UTC).",
                                command.getCommandType(), run.getScheduledSlot()),
                        command.getCommandType(), run.getId());
            } else {
                // FAILED / TIMEOUT of this attempt — record the error; the
                // retry pass decides whether to re-dispatch or finish FAILED.
                run.setError(error);
                runRepository.save(run);
            }
        });
    }

    /**
     * One sweep of the retry + reconciliation pass. Called each scheduler tick.
     *
     * Intentionally NOT @Transactional: per-run work commits independently,
     * and inner @Transactional calls (dispatch/transitionCommand) would poison
     * a shared tx rollback-only when they throw.
     *
     * 1. Stale undispatched ON runs → MISSED (never fired late — safety).
     * 2. Runs whose command is non-terminal and older than dispatchTimeout →
     *    transition command to TIMEOUT (lost result), then handled by 3.
     * 3. Runs whose command is terminally failed and older than retryInterval
     *    → re-dispatch (bounded by maxAttempts + cycle), else FAILED + notify.
     */
    public void sweep(Instant now, long retryIntervalMinutes, long dispatchTimeoutMinutes,
                      long onCatchupMinutes) {
        LocalDateTime retryCutoff = LocalDateTime.ofInstant(
                now.minusSeconds(retryIntervalMinutes * 60), ZoneOffset.UTC);
        LocalDateTime stuckCutoff = LocalDateTime.ofInstant(
                now.minusSeconds(dispatchTimeoutMinutes * 60), ZoneOffset.UTC);
        LocalDateTime staleOnCutoff = LocalDateTime.ofInstant(
                now.minusSeconds(onCatchupMinutes * 60), ZoneOffset.UTC);

        List<ScheduleRun> candidates = runRepository.findByStatusInAndUpdatedAtBefore(
                List.of(RUN_PENDING, RUN_DISPATCHED), retryCutoff);
        for (ScheduleRun run : candidates) {
            try {
                sweepRun(run, now, stuckCutoff, staleOnCutoff);
            } catch (Exception e) {
                log.error("Sweep failed for schedule_run={}: {}", run.getId(), e.getMessage(), e);
            }
        }
    }

    private void sweepRun(ScheduleRun run, Instant now, LocalDateTime stuckCutoff,
                          LocalDateTime staleOnCutoff) {
        // Stale undispatched ON — claimed but never dispatched (e.g. crash
        // between claim and dispatch). Mark MISSED rather than firing late.
        if ("ON".equals(run.getCommandType()) && run.getCommandId() == null
                && run.getScheduledSlot().isBefore(staleOnCutoff)) {
            run.setStatus(RUN_MISSED);
            run.setError("Slot missed beyond ON catch-up window — not fired for safety");
            runRepository.save(run);
            MachineSchedule schedule = loadSchedule(run.getScheduleId());
            emitEvent(schedule, "SCHEDULED_COMMAND_MISSED", "WARNING", "MISSED",
                    String.format("Scheduled ON for machine was missed (%s UTC) and was NOT executed for safety.",
                            run.getScheduledSlot()), "ON", run.getId());
            return;
        }

        MachineCommand command = run.getCommandId() != null
                ? commandRepository.findById(run.getCommandId()).orElse(null)
                : null;

        // Non-terminal command (QUEUED/SENT/ACK or never created) — reconcile
        // once the dispatch timeout passed; a lost result must not stall the run.
        if (command == null || !TERMINAL_COMMAND_STATES.contains(command.getStatus())) {
            if (command != null && run.getUpdatedAt().isAfter(stuckCutoff)) {
                return; // still within dispatch timeout — wait for the result
            }
            if (command != null) {
                timeoutCommand(command);
            }
            retryOrFail(run, now, "no terminal result within dispatch timeout");
            return;
        }

        if ("DONE".equals(command.getStatus())) {
            // Consumer hook may have missed (e.g. message redelivered before
            // the hook ran) — converge the run to its final state.
            run.setStatus(RUN_ACKED);
            runRepository.save(run);
            return;
        }

        retryOrFail(run, now, command.getLastError());
    }

    /**
     * Re-dispatch a failed attempt or finish the run FAILED.
     * Retries stop once the slot's cycle is over (ON: paired OFF passed;
     * OFF: next ON slot passed) — a late retry must not fire a stale command.
     */
    private void retryOrFail(ScheduleRun run, Instant now, String lastError) {
        MachineSchedule schedule = loadSchedule(run.getScheduleId());
        Instant slot = run.getScheduledSlot().toInstant(ZoneOffset.UTC);
        boolean onRun = "ON".equals(run.getCommandType());

        boolean insideCycle = onRun
                ? timeService.pairedOffInstant(schedule, slot).isAfter(now)
                : timeService.nextOnInstant(schedule, slot)
                        .map(nextOn -> nextOn.isAfter(now))
                        .orElse(false);

        if (!insideCycle || run.getAttemptCount() >= maxAttempts) {
            run.setStatus(RUN_FAILED);
            if (run.getError() == null) {
                run.setError(lastError != null ? lastError : "Attempts exhausted");
            }
            runRepository.save(run);
            log.warn("Schedule run {} {} slot={} FAILED (attempts={}, insideCycle={})",
                    run.getId(), run.getCommandType(), run.getScheduledSlot(),
                    run.getAttemptCount(), insideCycle);
            emitEvent(schedule, "SCHEDULED_COMMAND_FAILED", "WARNING", "FAILED",
                    String.format("Scheduled %s failed on the machine — device unreachable. Fence state is UNCHANGED. Please check.",
                            run.getCommandType()), run.getCommandType(), run.getId());
            return;
        }

        if (!hasBoundDevice(schedule.getMachineId())) {
            run.setStatus(RUN_FAILED);
            run.setError("No device with IMEI bound to machine: " + schedule.getMachineId());
            runRepository.save(run);
            emitEvent(schedule, "SCHEDULED_COMMAND_FAILED", "WARNING", "FAILED",
                    String.format("Scheduled %s failed — no device is bound to the machine. Fence state is UNCHANGED.",
                            run.getCommandType()), run.getCommandType(), run.getId());
            return;
        }

        try {
            Machine machine = machineRepository.findById(schedule.getMachineId())
                    .orElseThrow(() -> new IllegalStateException("Machine not found: " + schedule.getMachineId()));
            MachineCommand retry = commandService.dispatch(
                    machine, run.getCommandType(), schedule.getCreatedBy(),
                    CommandService.SOURCE_SCHEDULE, schedule.getId());
            run.setCommandId(retry.getId());
            run.setAttemptCount(run.getAttemptCount() + 1);
            runRepository.save(run);
            log.info("Retry {} dispatched for schedule run {} command={}",
                    run.getAttemptCount(), run.getId(), retry.getId());
        } catch (Exception e) {
            run.setError("Retry dispatch failed: " + e.getMessage());
            runRepository.save(run);
            log.error("Retry dispatch failed for schedule_run={}: {}", run.getId(), e.getMessage());
        }
    }

    /**
     * Times out a stuck command inline (no @Transactional transitionCommand
     * call — a thrown exception there would mark the surrounding tx
     * rollback-only). Records a terminal attempt so the audit trail shows
     * the reconciliation.
     */
    private void timeoutCommand(MachineCommand command) {
        CommandStateMachine.CommandState current = stateMachine.fromString(command.getStatus());
        CommandStateMachine.CommandState target =
                stateMachine.canTransition(current, CommandStateMachine.CommandState.TIMEOUT)
                        ? CommandStateMachine.CommandState.TIMEOUT
                        : CommandStateMachine.CommandState.FAILED;
        command.setStatus(target.name());
        command.setLastError(target == CommandStateMachine.CommandState.TIMEOUT
                ? "No terminal result within dispatch timeout"
                : "Timed out in state " + current);
        command.setCompletedAt(LocalDateTime.now());
        commandRepository.save(command);
        log.warn("Reconciled stuck command {} → {} (was {})", command.getId(), target, current);
    }

    private boolean hasBoundDevice(UUID machineId) {
        Device device = deviceRepository.findByMachineId(machineId).orElse(null);
        return device != null && device.getImei() != null && !device.getImei().isBlank();
    }

    /**
     * Atomically claims a slot. ON CONFLICT DO NOTHING makes this safe for
     * repeated ticks and multiple instances without a distributed lock.
     */
    private Optional<ScheduleRun> claimSlot(UUID scheduleId, LocalDateTime slotUtc, String commandType) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO schedule_runs (id, schedule_id, scheduled_slot, command_type, status, attempt_count) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', 0) " +
                        "ON CONFLICT (schedule_id, scheduled_slot, command_type) DO NOTHING",
                UUID.randomUUID(), scheduleId, slotUtc, commandType);
        if (inserted == 0) {
            return Optional.empty();
        }
        return runRepository.findByScheduleIdAndScheduledSlotAndCommandType(scheduleId, slotUtc, commandType);
    }

    /**
     * Retention cleanup — removes run history older than the retention window.
     */
    @Transactional
    public int cleanup(LocalDateTime cutoff) {
        int deleted = runRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} schedule_runs older than {}", deleted, cutoff);
        }
        return deleted;
    }

    private MachineSchedule loadSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).orElse(null);
    }

    /**
     * Emits a schedule lifecycle event through the existing event_outbox →
     * notification pipeline (same insert shape as CommandResultConsumer).
     * alert_id stays null — schedule events have no alerts table row.
     *
     * commandType ("ON"/"OFF") is carried in observedUnit — it is the only
     * free-form String slot on AlertTransitionMessage, and the template
     * engine already exposes it as {observedUnit} so SCHEDULED_COMMAND_*
     * templates can name the direction in every locale.
     */
    private void emitEvent(MachineSchedule schedule, String alertType, String severity,
                           String incidentState, String message, String commandType,
                           UUID correlationId) {
        if (schedule == null) {
            return;
        }
        try {
            AlertTransitionMessage transition = new AlertTransitionMessage();
            transition.setSchemaVersion(1);
            transition.setEventId(UUID.randomUUID());
            transition.setCorrelationId(correlationId);
            transition.setOccurredAt(Instant.now());
            transition.setAlertId(null);
            transition.setOrganizationId(schedule.getOrganizationId());
            transition.setMachineId(schedule.getMachineId());
            transition.setDeviceId(null);
            transition.setAlertType(alertType);
            transition.setSeverity(severity);
            transition.setIncidentState(incidentState);
            transition.setObservedUnit(commandType);
            transition.setMessage(message);
            transition.setOccurrenceCount(1);

            String payload = objectMapper.writeValueAsString(transition);
            jdbcTemplate.update(
                    "INSERT INTO event_outbox (organization_id, event_type, schema_version, " +
                            "aggregate_id, event_id, payload) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
                    schedule.getOrganizationId(), "ALERT_TRANSITION", 1,
                    correlationId, transition.getEventId(), payload);
        } catch (JsonProcessingException | RuntimeException e) {
            // Notification emission must never break schedule execution
            log.error("Failed to emit {} for schedule={}: {}", alertType, schedule.getId(), e.getMessage());
        }
    }
}
