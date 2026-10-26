package com.yantrago.api.service;

import com.yantrago.api.model.MachineSchedule;
import com.yantrago.api.repository.MachineScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScheduleCommandScheduler — tick behaviour, asymmetric
 * catch-up windows, kill switch, and missed-slot handling.
 *
 * Fail-safe asymmetry (per design doc decision #5): ON slots older than the
 * catch-up window are recorded MISSED and never fired; OFF slots fire late.
 */
class ScheduleCommandSchedulerTest {

    private MachineScheduleRepository scheduleRepository;
    private ScheduleExecutionService executionService;
    private ScheduleTimeService timeService;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(MachineScheduleRepository.class);
        executionService = mock(ScheduleExecutionService.class);
        timeService = new ScheduleTimeService();
    }

    private ScheduleCommandScheduler scheduler(boolean enabled) {
        return new ScheduleCommandScheduler(
                scheduleRepository, timeService, executionService,
                enabled, 15, 5, 5, 90);
    }

    private MachineSchedule schedule(LocalTime on, LocalTime off) {
        MachineSchedule s = new MachineSchedule();
        s.setId(UUID.randomUUID());
        s.setOrganizationId(UUID.randomUUID());
        s.setMachineId(UUID.randomUUID());
        s.setOnTime(on);
        s.setOffTime(off);
        s.setDaysOfWeek(127);
        s.setTimezone("Asia/Kolkata");
        s.setEnabled(true);
        // configured a day ago — slots since then count as "missed while enabled"
        s.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        return s;
    }

    private LocalTime istTime(Instant instant) {
        return LocalTime.ofInstant(instant, IST);
    }

    @Test
    @DisplayName("tick does nothing when the kill switch is off")
    void tick_killSwitchOff() {
        scheduler(false).tick();
        verify(scheduleRepository, never()).findAll();
        verify(executionService, never()).processSlot(any(), any(), any(), anyBoolean());
        verify(executionService, never()).sweep(any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("tick fires a due ON slot inside the catch-up window")
    void tick_firesDueOn() {
        Instant now = Instant.now();
        // ON 2 minutes ago → inside the 15-min catch-up window
        MachineSchedule s = schedule(istTime(now.minusSeconds(120)), LocalTime.of(7, 0));
        when(scheduleRepository.findAll()).thenReturn(List.of(s));

        scheduler(true).tick();

        verify(executionService).processSlot(eq(s), argThat(i ->
                Math.abs(i.getEpochSecond() - now.minusSeconds(120).getEpochSecond()) < 3600),
                eq("ON"), eq(true));
        verify(executionService).sweep(any(), eq(5L), eq(5L), eq(15L));
    }

    @Test
    @DisplayName("tick records (does not fire) a stale ON slot as MISSED")
    void tick_missedOnNotFired() {
        Instant now = Instant.now();
        // ON 2 hours ago → outside catch-up but inside the 24h record window
        MachineSchedule s = schedule(istTime(now.minusSeconds(7200)), LocalTime.of(7, 0));
        when(scheduleRepository.findAll()).thenReturn(List.of(s));

        scheduler(true).tick();

        verify(executionService).processSlot(eq(s), any(), eq("ON"), eq(false));
        // and it is never marked fireable
        verify(executionService, never()).processSlot(eq(s), any(), eq("ON"), eq(true));
    }

    @Test
    @DisplayName("tick fires a missed OFF late only when its ON cycle started")
    void tick_firesLateOff() {
        Instant now = Instant.now();
        // ON 2h ago, OFF 3 min ago → OFF is due but was missed; must still fire
        MachineSchedule s = schedule(istTime(now.minusSeconds(7200)), istTime(now.minusSeconds(180)));
        when(scheduleRepository.findAll()).thenReturn(List.of(s));
        when(executionService.hasOnRunForCycle(eq(s), any())).thenReturn(true);

        scheduler(true).tick();

        verify(executionService).processSlot(eq(s), any(), eq("OFF"), eq(true));
    }

    @Test
    @DisplayName("tick skips a due OFF whose ON cycle never started (fresh schedule)")
    void tick_skipsOffWithoutOnRun() {
        Instant now = Instant.now();
        MachineSchedule s = schedule(istTime(now.minusSeconds(7200)), istTime(now.minusSeconds(180)));
        when(scheduleRepository.findAll()).thenReturn(List.of(s));
        when(executionService.hasOnRunForCycle(eq(s), any())).thenReturn(false);

        scheduler(true).tick();

        verify(executionService, never()).processSlot(eq(s), any(), eq("OFF"), anyBoolean());
    }

    @Test
    @DisplayName("tick does not record MISSED for slots older than the schedule's last update")
    void tick_noMissedForPreUpdateSlots() {
        Instant now = Instant.now();
        // ON 2h ago, but schedule was last updated just now (re-enable) →
        // the old slot must not produce a MISSED record or a notification
        MachineSchedule s = schedule(istTime(now.minusSeconds(7200)), LocalTime.of(7, 0));
        s.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(scheduleRepository.findAll()).thenReturn(List.of(s));

        scheduler(true).tick();

        verify(executionService, never()).processSlot(eq(s), any(), eq("ON"), eq(false));
    }

    @Test
    @DisplayName("disabled schedule still fires a pending OFF of a claimed ON cycle")
    void tick_disabledStillFiresPendingOff() {
        Instant now = Instant.now();
        // User disabled after the ON fired — the cycle's OFF must still run
        // (decision #9: fail-safe toward de-energized).
        MachineSchedule s = schedule(istTime(now.minusSeconds(7200)), istTime(now.minusSeconds(180)));
        s.setEnabled(false);
        when(scheduleRepository.findAll()).thenReturn(List.of(s));
        when(executionService.hasOnRunForCycle(eq(s), any())).thenReturn(true);

        scheduler(true).tick();

        verify(executionService).processSlot(eq(s), any(), eq("OFF"), eq(true));
    }

    @Test
    @DisplayName("disabled schedule never claims ON slots and records no MISSED")
    void tick_disabledSkipsOn() {
        Instant now = Instant.now();
        MachineSchedule s = schedule(istTime(now.minusSeconds(120)), LocalTime.of(7, 0));
        s.setEnabled(false);
        when(scheduleRepository.findAll()).thenReturn(List.of(s));

        scheduler(true).tick();

        verify(executionService, never()).processSlot(eq(s), any(), eq("ON"), anyBoolean());
    }

    @Test
    @DisplayName("a failing schedule does not stop the tick for other schedules")
    void tick_isolatesPerScheduleErrors() {
        Instant now = Instant.now();
        MachineSchedule bad = schedule(istTime(now.minusSeconds(120)), LocalTime.of(7, 0));
        bad.setOnTime(null); // will throw inside dueSlots → error must be contained
        MachineSchedule good = schedule(istTime(now.minusSeconds(120)), LocalTime.of(7, 0));
        when(scheduleRepository.findAll()).thenReturn(List.of(bad, good));

        scheduler(true).tick();

        verify(executionService).processSlot(eq(good), any(), eq("ON"), eq(true));
        verify(executionService).sweep(any(), anyLong(), anyLong(), anyLong());
    }
}
