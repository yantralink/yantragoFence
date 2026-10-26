package com.yantrago.api.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScheduleExecutionService — slot claiming, dispatch,
 * scheduler-owned retries, and the reconciliation sweep.
 *
 * Per AGENTS.md rule 5/6: scheduled commands reuse the audited
 * machine_commands + RabbitMQ pipeline; success is never assumed.
 */
class ScheduleExecutionServiceTest {

    private ScheduleRunRepository runRepository;
    private MachineScheduleRepository scheduleRepository;
    private MachineRepository machineRepository;
    private DeviceRepository deviceRepository;
    private CommandRepository commandRepository;
    private CommandService commandService;
    private JdbcTemplate jdbcTemplate;
    private ScheduleTimeService timeService;
    private ScheduleExecutionService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID commandId = UUID.randomUUID();

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        runRepository = mock(ScheduleRunRepository.class);
        scheduleRepository = mock(MachineScheduleRepository.class);
        machineRepository = mock(MachineRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        commandRepository = mock(CommandRepository.class);
        commandService = mock(CommandService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        timeService = new ScheduleTimeService();

        service = new ScheduleExecutionService(
                runRepository, scheduleRepository, machineRepository, deviceRepository,
                commandRepository, commandService, new CommandStateMachine(), timeService,
                jdbcTemplate, new ObjectMapper().findAndRegisterModules(), 3);
    }

    private void stubBoundDevice() {
        Device d = new Device();
        d.setId(UUID.randomUUID());
        d.setImei("IMEI123456");
        d.setMachineId(machineId);
        when(deviceRepository.findByMachineId(machineId)).thenReturn(Optional.of(d));
    }

    private MachineSchedule schedule() {
        MachineSchedule s = new MachineSchedule();
        s.setId(scheduleId);
        s.setOrganizationId(orgId);
        s.setMachineId(machineId);
        s.setOnTime(LocalTime.of(18, 0));
        s.setOffTime(LocalTime.of(7, 0));
        s.setDaysOfWeek(127);
        s.setTimezone("Asia/Kolkata");
        return s;
    }

    private ScheduleRun pendingRun(String type, Instant slot) {
        ScheduleRun r = new ScheduleRun();
        r.setId(runId);
        r.setScheduleId(scheduleId);
        r.setCommandType(type);
        r.setScheduledSlot(LocalDateTime.ofInstant(slot, ZoneOffset.UTC));
        r.setStatus(ScheduleExecutionService.RUN_PENDING);
        r.setAttemptCount(0);
        r.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return r;
    }

    private Machine machine() {
        Machine m = new Machine();
        m.setId(machineId);
        m.setOrganizationId(orgId);
        return m;
    }

    // ---------- processSlot ----------

    @Test
    @DisplayName("claim conflict (double tick) does not dispatch")
    void processSlot_claimConflict_skips() {
        when(jdbcTemplate.update(startsWith("INSERT INTO schedule_runs"), any(), any(), any(), any()))
                .thenReturn(0);

        service.processSlot(schedule(), Instant.now(), "ON", true);

        verify(commandService, never()).dispatch(any(), any(), any(), any(), any());
        verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("claimed fireable ON slot dispatches via CommandService and marks DISPATCHED")
    void processSlot_dispatches() {
        Instant slot = Instant.now();
        LocalDateTime slotUtc = LocalDateTime.ofInstant(slot, ZoneOffset.UTC);
        ScheduleRun run = pendingRun("ON", slot);

        when(jdbcTemplate.update(startsWith("INSERT INTO schedule_runs"), any(), any(), any(), any()))
                .thenReturn(1);
        when(runRepository.findByScheduleIdAndScheduledSlotAndCommandType(scheduleId, slotUtc, "ON"))
                .thenReturn(Optional.of(run));
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine()));
        stubBoundDevice();
        MachineCommand command = new MachineCommand();
        command.setId(commandId);
        when(commandService.dispatch(any(), eq("ON"), any(), eq("SCHEDULE"), eq(scheduleId)))
                .thenReturn(command);

        service.processSlot(schedule(), slot, "ON", true);

        verify(commandService).dispatch(any(), eq("ON"), any(), eq("SCHEDULE"), eq(scheduleId));
        assertEquals(ScheduleExecutionService.RUN_DISPATCHED, run.getStatus());
        assertEquals(commandId, run.getCommandId());
        assertEquals(1, run.getAttemptCount());
    }

    @Test
    @DisplayName("non-fireable (stale) ON slot is recorded MISSED, never dispatched")
    void processSlot_staleOn_markedMissed() {
        Instant slot = Instant.now().minusSeconds(7200);
        LocalDateTime slotUtc = LocalDateTime.ofInstant(slot, ZoneOffset.UTC);
        ScheduleRun run = pendingRun("ON", slot);

        when(jdbcTemplate.update(startsWith("INSERT INTO schedule_runs"), any(), any(), any(), any()))
                .thenReturn(1);
        when(runRepository.findByScheduleIdAndScheduledSlotAndCommandType(scheduleId, slotUtc, "ON"))
                .thenReturn(Optional.of(run));

        service.processSlot(schedule(), slot, "ON", false);

        verify(commandService, never()).dispatch(any(), any(), any(), any(), any());
        assertEquals(ScheduleExecutionService.RUN_MISSED, run.getStatus());
        // MISSED notification event written to event_outbox
        verify(jdbcTemplate).update(startsWith("INSERT INTO event_outbox"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("dispatch failure marks the run FAILED and emits failure event")
    void processSlot_dispatchFailure_marksFailed() {
        Instant slot = Instant.now();
        LocalDateTime slotUtc = LocalDateTime.ofInstant(slot, ZoneOffset.UTC);
        ScheduleRun run = pendingRun("ON", slot);

        when(jdbcTemplate.update(startsWith("INSERT INTO schedule_runs"), any(), any(), any(), any()))
                .thenReturn(1);
        when(runRepository.findByScheduleIdAndScheduledSlotAndCommandType(scheduleId, slotUtc, "ON"))
                .thenReturn(Optional.of(run));
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine()));
        stubBoundDevice();
        when(commandService.dispatch(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("RabbitMQ unreachable"));

        service.processSlot(schedule(), slot, "ON", true);

        assertEquals(ScheduleExecutionService.RUN_FAILED, run.getStatus());
        assertNotNull(run.getError());
        verify(jdbcTemplate).update(startsWith("INSERT INTO event_outbox"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("no bound device marks the run FAILED without calling dispatch")
    void processSlot_noDevice_marksFailedNoDispatch() {
        Instant slot = Instant.now();
        LocalDateTime slotUtc = LocalDateTime.ofInstant(slot, ZoneOffset.UTC);
        ScheduleRun run = pendingRun("ON", slot);

        when(jdbcTemplate.update(startsWith("INSERT INTO schedule_runs"), any(), any(), any(), any()))
                .thenReturn(1);
        when(runRepository.findByScheduleIdAndScheduledSlotAndCommandType(scheduleId, slotUtc, "ON"))
                .thenReturn(Optional.of(run));
        when(deviceRepository.findByMachineId(machineId)).thenReturn(Optional.empty());

        service.processSlot(schedule(), slot, "ON", true);

        verify(commandService, never()).dispatch(any(), any(), any(), any(), any());
        assertEquals(ScheduleExecutionService.RUN_FAILED, run.getStatus());
        verify(jdbcTemplate).update(startsWith("INSERT INTO event_outbox"), any(), any(), any(), any(), any(), any());
    }

    // ---------- onScheduledCommandResult ----------

    @Test
    @DisplayName("DONE result marks the run ACKED and emits executed event")
    void onResult_done_ackesRun() {
        ScheduleRun run = pendingRun("ON", Instant.now());
        run.setStatus(ScheduleExecutionService.RUN_DISPATCHED);
        run.setCommandId(commandId);
        when(runRepository.findByCommandId(commandId)).thenReturn(Optional.of(run));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule()));

        CommandResponse response = new CommandResponse(
                commandId, orgId, machineId, null, null, "ON", "DONE", 1, 3, null,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), "SCHEDULE", scheduleId);
        service.onScheduledCommandResult(response, "DONE", null);

        assertEquals(ScheduleExecutionService.RUN_ACKED, run.getStatus());
        verify(jdbcTemplate).update(startsWith("INSERT INTO event_outbox"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("FAILED result records error but keeps run DISPATCHED for retry")
    void onResult_failed_keepsDispatched() {
        ScheduleRun run = pendingRun("ON", Instant.now());
        run.setStatus(ScheduleExecutionService.RUN_DISPATCHED);
        run.setCommandId(commandId);
        when(runRepository.findByCommandId(commandId)).thenReturn(Optional.of(run));

        CommandResponse response = new CommandResponse(
                commandId, orgId, machineId, null, null, "ON", "FAILED", 1, 3, "Device offline",
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), "SCHEDULE", scheduleId);
        service.onScheduledCommandResult(response, "FAILED", "Device offline");

        assertEquals(ScheduleExecutionService.RUN_DISPATCHED, run.getStatus());
        assertEquals("Device offline", run.getError());
    }

    // ---------- sweep (retry + reconciliation) ----------

    @Test
    @DisplayName("sweep re-dispatches a terminally failed run inside its cycle")
    void sweep_retriesFailedRunInsideCycle() {
        Instant now = Instant.now();
        // ON slot 10 min ago; OFF 10 min from now → still inside the cycle
        MachineSchedule s = schedule();
        s.setOnTime(LocalTime.ofInstant(now.minusSeconds(600), IST));
        s.setOffTime(LocalTime.ofInstant(now.plusSeconds(600), IST));
        Instant onSlot = ZonedDateTime.ofInstant(now, IST)
                .with(s.getOnTime()).withSecond(0).withNano(0).toInstant();

        ScheduleRun run = pendingRun("ON", onSlot);
        run.setStatus(ScheduleExecutionService.RUN_DISPATCHED);
        run.setCommandId(commandId);
        run.setAttemptCount(1);
        run.setUpdatedAt(LocalDateTime.ofInstant(now.minusSeconds(600), ZoneOffset.UTC));

        MachineCommand failed = new MachineCommand();
        failed.setId(commandId);
        failed.setStatus("FAILED");

        when(runRepository.findByStatusInAndUpdatedAtBefore(anyList(), any()))
                .thenReturn(java.util.List.of(run));
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(failed));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(s));
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine()));
        stubBoundDevice();
        MachineCommand retry = new MachineCommand();
        retry.setId(UUID.randomUUID());
        when(commandService.dispatch(any(), eq("ON"), any(), eq("SCHEDULE"), eq(scheduleId)))
                .thenReturn(retry);

        service.sweep(now, 5, 5, 15);

        assertEquals(2, run.getAttemptCount());
        assertEquals(retry.getId(), run.getCommandId());
        assertEquals(ScheduleExecutionService.RUN_DISPATCHED, run.getStatus());
    }

    @Test
    @DisplayName("sweep marks run FAILED after max attempts")
    void sweep_failsAfterMaxAttempts() {
        Instant now = Instant.now();
        ScheduleRun run = pendingRun("ON", now.minusSeconds(600));
        run.setStatus(ScheduleExecutionService.RUN_DISPATCHED);
        run.setCommandId(commandId);
        run.setAttemptCount(3);
        run.setUpdatedAt(LocalDateTime.ofInstant(now.minusSeconds(600), ZoneOffset.UTC));

        MachineCommand failed = new MachineCommand();
        failed.setId(commandId);
        failed.setStatus("TIMEOUT");

        when(runRepository.findByStatusInAndUpdatedAtBefore(anyList(), any()))
                .thenReturn(java.util.List.of(run));
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(failed));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule()));

        service.sweep(now, 5, 5, 15);

        verify(commandService, never()).dispatch(any(), any(), any(), any(), any());
        assertEquals(ScheduleExecutionService.RUN_FAILED, run.getStatus());
        verify(jdbcTemplate).update(startsWith("INSERT INTO event_outbox"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sweep times out a stuck non-terminal command then retries")
    void sweep_reconcilesStuckCommand() {
        Instant now = Instant.now();
        MachineSchedule s = schedule();
        s.setOnTime(LocalTime.ofInstant(now.minusSeconds(600), IST));
        s.setOffTime(LocalTime.ofInstant(now.plusSeconds(600), IST));
        Instant onSlot = ZonedDateTime.ofInstant(now, IST)
                .with(s.getOnTime()).withSecond(0).withNano(0).toInstant();

        ScheduleRun run = pendingRun("ON", onSlot);
        run.setStatus(ScheduleExecutionService.RUN_DISPATCHED);
        run.setCommandId(commandId);
        run.setAttemptCount(1);
        run.setUpdatedAt(LocalDateTime.ofInstant(now.minusSeconds(600), ZoneOffset.UTC));

        MachineCommand stuck = new MachineCommand();
        stuck.setId(commandId);
        stuck.setStatus("SENT"); // device never replied — lost result

        when(runRepository.findByStatusInAndUpdatedAtBefore(anyList(), any()))
                .thenReturn(java.util.List.of(run));
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(stuck));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(s));
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine()));
        stubBoundDevice();
        MachineCommand retry = new MachineCommand();
        retry.setId(UUID.randomUUID());
        when(commandService.dispatch(any(), eq("ON"), any(), eq("SCHEDULE"), eq(scheduleId)))
                .thenReturn(retry);

        service.sweep(now, 5, 5, 15);

        // Stuck command was timed out inline via the state machine…
        assertEquals("TIMEOUT", stuck.getStatus());
        assertNotNull(stuck.getCompletedAt());
        verify(commandRepository).save(stuck);
        // …then the run was retried
        assertEquals(2, run.getAttemptCount());
        assertEquals(retry.getId(), run.getCommandId());
    }

    @Test
    @DisplayName("sweep does not retry outside the cycle (stale slot)")
    void sweep_noRetryOutsideCycle() {
        Instant now = Instant.now();
        // ON slot whose paired OFF is already in the past → cycle is over
        MachineSchedule s = schedule();
        s.setOnTime(LocalTime.ofInstant(now.minusSeconds(3600), IST));
        s.setOffTime(LocalTime.ofInstant(now.minusSeconds(600), IST)); // OFF 10 min ago
        Instant onSlot = ZonedDateTime.ofInstant(now, IST)
                .with(s.getOnTime()).withSecond(0).withNano(0).toInstant();

        ScheduleRun run = pendingRun("ON", onSlot);
        run.setStatus(ScheduleExecutionService.RUN_DISPATCHED);
        run.setCommandId(commandId);
        run.setAttemptCount(1);
        run.setUpdatedAt(LocalDateTime.ofInstant(now.minusSeconds(600), ZoneOffset.UTC));

        MachineCommand failed = new MachineCommand();
        failed.setId(commandId);
        failed.setStatus("FAILED");

        when(runRepository.findByStatusInAndUpdatedAtBefore(anyList(), any()))
                .thenReturn(java.util.List.of(run));
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(failed));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(s));

        service.sweep(now, 5, 5, 15);

        verify(commandService, never()).dispatch(any(), any(), any(), any(), any());
        assertEquals(ScheduleExecutionService.RUN_FAILED, run.getStatus());
    }

    @Test
    @DisplayName("sweep converges DONE command to ACKED when consumer hook was missed")
    void sweep_doneConvergesAcked() {
        Instant now = Instant.now();
        ScheduleRun run = pendingRun("OFF", now.minusSeconds(600));
        run.setStatus(ScheduleExecutionService.RUN_DISPATCHED);
        run.setCommandId(commandId);
        run.setUpdatedAt(LocalDateTime.ofInstant(now.minusSeconds(600), ZoneOffset.UTC));

        MachineCommand done = new MachineCommand();
        done.setId(commandId);
        done.setStatus("DONE");

        when(runRepository.findByStatusInAndUpdatedAtBefore(anyList(), any()))
                .thenReturn(java.util.List.of(run));
        when(commandRepository.findById(commandId)).thenReturn(Optional.of(done));

        service.sweep(now, 5, 5, 15);

        assertEquals(ScheduleExecutionService.RUN_ACKED, run.getStatus());
    }

    @Test
    @DisplayName("cleanup deletes runs older than the retention cutoff")
    void cleanup_deletesOldRuns() {
        when(runRepository.deleteOlderThan(any())).thenReturn(42);
        assertEquals(42, service.cleanup(LocalDateTime.now(ZoneOffset.UTC).minusDays(90)));
        verify(runRepository).deleteOlderThan(any());
    }
}
