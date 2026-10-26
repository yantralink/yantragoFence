package com.yantrago.api.service;

import com.yantrago.api.model.MachineSchedule;
import com.yantrago.api.repository.MachineScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Ticks the automatic fence ON/OFF schedule engine.
 *
 * Per docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md:
 *  - ON slots fire only within the catch-up window (default 15 min); older
 *    slots are recorded MISSED for auditability — never fired late (a
 *    surprise late ON energizing an electric fence is a safety hazard).
 *  - OFF slots are fail-safe: any missed OFF since the most recent ON slot
 *    still fires (never leave a fence energized by accident).
 *  - MISSED records are only written for slots at/after the schedule's last
 *    update — prevents a burst of stale MISSED notifications when a schedule
 *    is re-enabled after being off for days.
 *
 * The whole engine can be disabled without a deploy via
 * schedule.execution.enabled=false (kill switch), and per-schedule via the
 * enabled flag.
 */
@Component
public class ScheduleCommandScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCommandScheduler.class);

    /** MISSED records are written at most this far back (anti-spam bound). */
    private static final long MISSED_RECORD_LOOKBACK_HOURS = 24;

    private final MachineScheduleRepository scheduleRepository;
    private final ScheduleTimeService timeService;
    private final ScheduleExecutionService executionService;

    private final boolean executionEnabled;
    private final long onCatchupMinutes;
    private final long retryIntervalMinutes;
    private final long dispatchTimeoutMinutes;
    private final long runsRetentionDays;

    public ScheduleCommandScheduler(MachineScheduleRepository scheduleRepository,
                                    ScheduleTimeService timeService,
                                    ScheduleExecutionService executionService,
                                    @Value("${schedule.execution.enabled:true}") boolean executionEnabled,
                                    @Value("${schedule.on-catchup-minutes:15}") long onCatchupMinutes,
                                    @Value("${schedule.retry-interval-minutes:5}") long retryIntervalMinutes,
                                    @Value("${schedule.dispatch-timeout-minutes:5}") long dispatchTimeoutMinutes,
                                    @Value("${schedule.runs-retention-days:90}") long runsRetentionDays) {
        this.scheduleRepository = scheduleRepository;
        this.timeService = timeService;
        this.executionService = executionService;
        this.executionEnabled = executionEnabled;
        this.onCatchupMinutes = onCatchupMinutes;
        this.retryIntervalMinutes = retryIntervalMinutes;
        this.dispatchTimeoutMinutes = dispatchTimeoutMinutes;
        this.runsRetentionDays = runsRetentionDays;
    }

    @Scheduled(fixedDelayString = "${schedule.tick-ms:30000}")
    public void tick() {
        if (!executionEnabled) {
            return;
        }
        Instant now = Instant.now();
        // Disabled schedules are processed too: decision #9 — disabling
        // cancels future runs, but a pending OFF belonging to an already-
        // claimed ON cycle still fires (fail-safe toward de-energized).
        for (MachineSchedule schedule : scheduleRepository.findAll()) {
            try {
                processSchedule(schedule, now);
            } catch (Exception e) {
                log.error("Schedule tick failed for schedule={} machine={}: {}",
                        schedule.getId(), schedule.getMachineId(), e.getMessage(), e);
            }
        }
        executionService.sweep(now, retryIntervalMinutes, dispatchTimeoutMinutes, onCatchupMinutes);
    }

    /**
     * Claims and executes all slots due for one schedule.
     * Asymmetric windows (fail-safe): ON is bounded to the catch-up window,
     * OFF is bounded only by the most recent ON slot of the cycle.
     */
    private void processSchedule(MachineSchedule schedule, Instant now) {
        Instant onFireWindowStart = now.minusSeconds(onCatchupMinutes * 60);
        Instant missedRecordStart = now.minusSeconds(MISSED_RECORD_LOOKBACK_HOURS * 3600);
        LocalDateTime lastUpdated = schedule.getUpdatedAt();

        // ON section — enabled schedules only. Disabling cancels all future
        // ON runs silently: no claim, no MISSED record for slots the user
        // chose to skip.
        if (Boolean.TRUE.equals(schedule.getEnabled())) {
            for (ScheduleTimeService.DueSlot slot : timeService.dueSlots(schedule, missedRecordStart, now)) {
                if (slot.type() != ScheduleTimeService.SlotType.ON) {
                    continue;
                }
                LocalDateTime slotUtc = LocalDateTime.ofInstant(slot.instant(), ZoneOffset.UTC);
                // Only record MISSED for slots that were due while this version
                // of the schedule was already configured — avoids notification
                // spam on re-enable or after an outage older than a day.
                if (lastUpdated != null && slotUtc.isBefore(lastUpdated)) {
                    continue;
                }
                boolean fireable = !slot.instant().isBefore(onFireWindowStart);
                executionService.processSlot(schedule, slot.instant(), "ON", fireable);
            }
        }

        // OFF catch-up bound: the most recent ON slot of the current cycle.
        // OFFs older than that belong to finished/missed cycles and are moot.
        // An OFF only fires when its paired ON cycle actually started (a run
        // row exists) — a freshly created schedule must not immediately
        // de-energize a manually-operated fence.
        Instant offWindowStart = timeService.latestOnInstant(schedule, now).orElse(onFireWindowStart);
        for (ScheduleTimeService.DueSlot slot : timeService.dueSlots(schedule, offWindowStart, now)) {
            if (slot.type() == ScheduleTimeService.SlotType.OFF
                    && executionService.hasOnRunForCycle(schedule, slot.instant())) {
                executionService.processSlot(schedule, slot.instant(), "OFF", true);
            }
        }
    }

    /** Daily retention cleanup for schedule_runs (default: 90 days, 03:00). */
    @Scheduled(cron = "${schedule.cleanup-cron:0 0 3 * * *}")
    public void cleanup() {
        if (!executionEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                Instant.now().minusSeconds(runsRetentionDays * 86_400), ZoneOffset.UTC);
        executionService.cleanup(cutoff);
    }
}
