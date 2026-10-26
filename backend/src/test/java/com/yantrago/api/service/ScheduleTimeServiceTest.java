package com.yantrago.api.service;

import com.yantrago.api.model.MachineSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScheduleTimeService — the slot math behind automatic
 * fence ON/OFF scheduling.
 *
 * Cycle semantics: days_of_week applies to ON; paired OFF is the next
 * off_time strictly after the ON instant (next day when offTime <= onTime).
 */
class ScheduleTimeServiceTest {

    private final ScheduleTimeService service = new ScheduleTimeService();
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private MachineSchedule schedule(String on, String off, int days, String tz) {
        MachineSchedule s = new MachineSchedule();
        s.setOnTime(LocalTime.parse(on));
        s.setOffTime(LocalTime.parse(off));
        s.setDaysOfWeek(days);
        s.setTimezone(tz);
        return s;
    }

    private Instant at(String date, String time, ZoneId zone) {
        return ZonedDateTime.of(
                java.time.LocalDate.parse(date),
                LocalTime.parse(time), zone).toInstant();
    }

    // ---------- nextOnInstant ----------

    @Test
    @DisplayName("nextOn: same-day ON when current time is before onTime")
    void nextOn_sameDay() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant from = at("2026-10-26", "10:00", IST); // Monday
        Optional<Instant> next = service.nextOnInstant(s, from);
        assertTrue(next.isPresent());
        assertEquals(at("2026-10-26", "18:00", IST), next.get());
    }

    @Test
    @DisplayName("nextOn: rolls to next day when onTime already passed")
    void nextOn_nextDay() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant from = at("2026-10-26", "19:00", IST);
        assertEquals(at("2026-10-27", "18:00", IST), service.nextOnInstant(s, from).orElseThrow());
    }

    @Test
    @DisplayName("nextOn: skips unselected days (Mon-only schedule from Friday)")
    void nextOn_skipsUnselectedDays() {
        MachineSchedule s = schedule("18:00", "07:00", ScheduleTimeService.dayBit(java.time.DayOfWeek.MONDAY), "Asia/Kolkata");
        Instant friday = at("2026-10-23", "20:00", IST); // Friday after ON time
        // Next Monday is 2026-10-26
        assertEquals(at("2026-10-26", "18:00", IST), service.nextOnInstant(s, friday).orElseThrow());
    }

    // ---------- pairedOffInstant / nextOffInstant ----------

    @Test
    @DisplayName("pairedOff: cross-midnight OFF fires next calendar day")
    void pairedOff_crossMidnight() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant monOn = at("2026-10-26", "18:00", IST); // Monday 18:00
        // OFF must be Tuesday 07:00 even though days_of_week only governs ON
        assertEquals(at("2026-10-27", "07:00", IST), service.pairedOffInstant(s, monOn));
    }

    @Test
    @DisplayName("pairedOff: Mon-only ON fires OFF on Tuesday (cycle semantics)")
    void pairedOff_monToTue() {
        int monOnly = ScheduleTimeService.dayBit(java.time.DayOfWeek.MONDAY);
        MachineSchedule s = schedule("18:00", "07:00", monOnly, "Asia/Kolkata");
        Instant monOn = at("2026-10-26", "18:00", IST);
        assertEquals(at("2026-10-27", "07:00", IST), service.pairedOffInstant(s, monOn));
    }

    @Test
    @DisplayName("pairedOff: same-day when offTime > onTime")
    void pairedOff_sameDay() {
        MachineSchedule s = schedule("06:00", "18:30", 127, "Asia/Kolkata");
        Instant on = at("2026-10-26", "06:00", IST);
        assertEquals(at("2026-10-26", "18:30", IST), service.pairedOffInstant(s, on));
    }

    @Test
    @DisplayName("nextOff: reports today's morning OFF when fence went ON last night")
    void nextOff_reportsPendingMorningOff() {
        // ON 18:00 Mon → OFF 07:00 Tue. At Tue 06:00 the next OFF is 07:00 today,
        // NOT tomorrow — even though nextOnInstant is Tue 18:00.
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant tue6am = at("2026-10-27", "06:00", IST);
        assertEquals(at("2026-10-27", "07:00", IST), service.nextOffInstant(s, tue6am).orElseThrow());
    }

    @Test
    @DisplayName("nextOff: mid-day reports next day's morning OFF")
    void nextOff_midDay() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant tue12pm = at("2026-10-27", "12:00", IST);
        assertEquals(at("2026-10-28", "07:00", IST), service.nextOffInstant(s, tue12pm).orElseThrow());
    }

    // ---------- latestOnInstant ----------

    @Test
    @DisplayName("latestOn: returns today's ON slot when past onTime")
    void latestOn_sameDay() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant tue7pm = at("2026-10-27", "19:00", IST);
        assertEquals(at("2026-10-27", "18:00", IST), service.latestOnInstant(s, tue7pm).orElseThrow());
    }

    @Test
    @DisplayName("latestOn: before today's ON returns yesterday's slot")
    void latestOn_beforeTodayOn() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant tue6am = at("2026-10-27", "06:00", IST);
        assertEquals(at("2026-10-26", "18:00", IST), service.latestOnInstant(s, tue6am).orElseThrow());
    }

    // ---------- dueSlots ----------

    @Test
    @DisplayName("dueSlots: emits ON slot inside window")
    void dueSlots_onInWindow() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant start = at("2026-10-26", "17:50", IST);
        Instant end = at("2026-10-26", "18:10", IST);
        List<ScheduleTimeService.DueSlot> slots = service.dueSlots(s, start, end);
        assertEquals(1, slots.size());
        assertEquals(ScheduleTimeService.SlotType.ON, slots.get(0).type());
        assertEquals(at("2026-10-26", "18:00", IST), slots.get(0).instant());
    }

    @Test
    @DisplayName("dueSlots: emits OFF slot inside window even when its ON was before the window")
    void dueSlots_offInWindowOnOutside() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "Asia/Kolkata");
        Instant start = at("2026-10-27", "06:50", IST);
        Instant end = at("2026-10-27", "07:10", IST);
        List<ScheduleTimeService.DueSlot> slots = service.dueSlots(s, start, end);
        assertEquals(1, slots.size());
        assertEquals(ScheduleTimeService.SlotType.OFF, slots.get(0).type());
        assertEquals(at("2026-10-27", "07:00", IST), slots.get(0).instant());
    }

    @Test
    @DisplayName("dueSlots: window covering both ON and OFF of same-day schedule emits both")
    void dueSlots_bothSlots() {
        MachineSchedule s = schedule("06:00", "18:00", 127, "Asia/Kolkata");
        Instant start = at("2026-10-26", "05:50", IST);
        Instant end = at("2026-10-26", "18:10", IST);
        List<ScheduleTimeService.DueSlot> slots = service.dueSlots(s, start, end);
        assertEquals(2, slots.size());
        assertEquals(ScheduleTimeService.SlotType.ON, slots.get(0).type());
        assertEquals(ScheduleTimeService.SlotType.OFF, slots.get(1).type());
    }

    @Test
    @DisplayName("dueSlots: unselected day produces no slots")
    void dueSlots_unselectedDay() {
        int monOnly = ScheduleTimeService.dayBit(java.time.DayOfWeek.MONDAY);
        MachineSchedule s = schedule("18:00", "07:00", monOnly, "Asia/Kolkata");
        // Tuesday window: ON is Monday-only, but the paired OFF still fires Tuesday
        Instant start = at("2026-10-27", "17:50", IST);
        Instant end = at("2026-10-27", "18:10", IST);
        assertTrue(service.dueSlots(s, start, end).isEmpty());
    }

    @Test
    @DisplayName("dueSlots: Mon-only schedule emits the paired OFF on Tuesday window")
    void dueSlots_pairedOffOnUnselectedDay() {
        int monOnly = ScheduleTimeService.dayBit(java.time.DayOfWeek.MONDAY);
        MachineSchedule s = schedule("18:00", "07:00", monOnly, "Asia/Kolkata");
        Instant start = at("2026-10-27", "06:50", IST);
        Instant end = at("2026-10-27", "07:10", IST);
        List<ScheduleTimeService.DueSlot> slots = service.dueSlots(s, start, end);
        assertEquals(1, slots.size());
        assertEquals(ScheduleTimeService.SlotType.OFF, slots.get(0).type());
    }

    // ---------- timezone handling ----------

    @Test
    @DisplayName("non-IST timezone: America/New_York computes instants in that zone")
    void nonIstTimezone() {
        MachineSchedule s = schedule("18:00", "07:00", 127, "America/New_York");
        ZoneId ny = ZoneId.of("America/New_York");
        Instant from = ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, ny).toInstant();
        assertEquals(ZonedDateTime.of(2026, 10, 26, 18, 0, 0, 0, ny).toInstant(),
                service.nextOnInstant(s, from).orElseThrow());
    }

    @Test
    @DisplayName("DST spring-forward: a time inside the gap resolves to the first valid instant")
    void dstSpringForward() {
        // US DST 2026: March 8, 02:00 → 03:00. 02:30 does not exist that day.
        MachineSchedule s = schedule("02:30", "07:00", 127, "America/New_York");
        ZoneId ny = ZoneId.of("America/New_York");
        Instant from = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ny).toInstant();
        Optional<Instant> next = service.nextOnInstant(s, from);
        assertTrue(next.isPresent());
        // ZonedDateTime.of resolves the non-existent 02:30 forward to 03:30 EDT
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, ny).toInstant(), next.get());
    }

    @Test
    @DisplayName("DST fall-back: repeated wall-clock time resolves to first occurrence")
    void dstFallBack() {
        // US DST 2026: Nov 1, 02:00 → 01:00. 01:30 occurs twice.
        MachineSchedule s = schedule("01:30", "07:00", 127, "America/New_York");
        ZoneId ny = ZoneId.of("America/New_York");
        Instant from = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, ny).toInstant();
        Instant expected = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, ny).toInstant();
        assertEquals(expected, service.nextOnInstant(s, from).orElseThrow());
    }

    // ---------- validation ----------

    @Test
    @DisplayName("validateTimes rejects equal on/off times")
    void validateTimes_equalTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateTimes(LocalTime.of(18, 0), LocalTime.of(18, 0), 127, "Asia/Kolkata"));
    }

    @Test
    @DisplayName("validateTimes rejects empty day bitmask")
    void validateTimes_emptyDays() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateTimes(LocalTime.of(18, 0), LocalTime.of(7, 0), 0, "Asia/Kolkata"));
    }

    @Test
    @DisplayName("validateTimes rejects invalid timezone")
    void validateTimes_badTimezone() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateTimes(LocalTime.of(18, 0), LocalTime.of(7, 0), 127, "Mars/Olympus"));
    }

    @Test
    @DisplayName("validateTimes accepts a valid schedule")
    void validateTimes_valid() {
        assertDoesNotThrow(() ->
                service.validateTimes(LocalTime.of(18, 0), LocalTime.of(7, 0), 127, "Asia/Kolkata"));
    }
}
