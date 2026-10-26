package com.yantrago.api.service;

import com.yantrago.api.model.MachineSchedule;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure, stateless schedule slot math. All times are computed in the
 * schedule's timezone (wall-clock), stored/compared as UTC {@link Instant}s.
 *
 * Cycle semantics (see docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md):
 * the days_of_week bitmask (Mon=1 ... Sun=64) applies to the ON event; the
 * paired OFF is the next occurrence of off_time strictly after the ON
 * instant — i.e. next calendar day when off_time <= on_time. Example:
 * ON Mon 18:00 / OFF 07:00 → OFF fires Tuesday 07:00 even if Tuesday is
 * not a selected day.
 *
 * ZonedDateTime-based math keeps DST transitions correct: a wall-clock time
 * that falls in a DST gap resolves forward to the first valid instant; a
 * time that occurs twice resolves to the first occurrence.
 */
@Service
public class ScheduleTimeService {

    public static final int ALL_DAYS = 127; // Mon..Sun all bits set

    public enum SlotType { ON, OFF }

    /** A fire instant due within a window. Instant is absolute (UTC). */
    public record DueSlot(SlotType type, Instant instant) {}

    /**
     * Bit for a day of week: Monday=1, Tuesday=2, ... Sunday=64.
     */
    public static int dayBit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    /**
     * Next ON fire instant strictly after {@code from}, or empty if none
     * within 14 days (defensive bound; a non-zero bitmask always yields one).
     */
    public Optional<Instant> nextOnInstant(MachineSchedule s, Instant from) {
        ZoneId zone = zoneOf(s);
        LocalDate start = ZonedDateTime.ofInstant(from, zone).toLocalDate();
        for (int i = 0; i < 14; i++) {
            LocalDate day = start.plusDays(i);
            if (!isSelectedDay(s, day)) continue;
            Instant candidate = ZonedDateTime.of(day, s.getOnTime(), zone).toInstant();
            if (candidate.isAfter(from)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Next OFF fire instant strictly after {@code from}.
     * Enumerates ON slots (including those before {@code from}) and takes the
     * earliest paired OFF still in the future — so when the fence was turned
     * ON last night, the upcoming morning OFF is reported, not next day's.
     */
    public Optional<Instant> nextOffInstant(MachineSchedule s, Instant from) {
        ZoneId zone = zoneOf(s);
        LocalDate start = ZonedDateTime.ofInstant(from, zone).toLocalDate();
        // A paired OFF lands at most 1 day after its ON, so scanning ON days
        // starting 1 day before `from` covers every OFF that can be >= from.
        for (int i = -1; i < 14; i++) {
            LocalDate day = start.plusDays(i);
            if (!isSelectedDay(s, day)) continue;
            Instant on = ZonedDateTime.of(day, s.getOnTime(), zone).toInstant();
            Instant off = pairedOffInstant(s, on);
            if (off.isAfter(from)) {
                return Optional.of(off);
            }
        }
        return Optional.empty();
    }

    /**
     * The OFF instant paired with a given ON instant — the next occurrence of
     * offTime strictly after the ON, in the schedule's timezone.
     */
    public Instant pairedOffInstant(MachineSchedule s, Instant onInstant) {
        ZoneId zone = zoneOf(s);
        LocalDate onDate = ZonedDateTime.ofInstant(onInstant, zone).toLocalDate();
        LocalDate offDate = s.getOffTime().isAfter(s.getOnTime())
                ? onDate
                : onDate.plusDays(1);
        return ZonedDateTime.of(offDate, s.getOffTime(), zone).toInstant();
    }

    /**
     * The ON slot paired with a given OFF instant — the inverse of
     * {@link #pairedOffInstant}. Empty when the computed ON day is not a
     * selected day (the OFF is not part of any scheduled cycle).
     */
    public Optional<Instant> pairedOnInstant(MachineSchedule s, Instant offInstant) {
        ZoneId zone = zoneOf(s);
        LocalDate offDate = ZonedDateTime.ofInstant(offInstant, zone).toLocalDate();
        LocalDate onDate = s.getOffTime().isAfter(s.getOnTime())
                ? offDate
                : offDate.minusDays(1);
        if (!isSelectedDay(s, onDate)) {
            return Optional.empty();
        }
        Instant on = ZonedDateTime.of(onDate, s.getOnTime(), zone).toInstant();
        return pairedOffInstant(s, on).equals(offInstant) ? Optional.of(on) : Optional.empty();
    }

    /**
     * Most recent ON slot at or before {@code at}, or empty if none within
     * 8 days back. Used by the scheduler to bound OFF catch-up to the
     * current cycle (a stale OFF must not fire once a newer ON slot passed).
     */
    public Optional<Instant> latestOnInstant(MachineSchedule s, Instant at) {
        ZoneId zone = zoneOf(s);
        LocalDate start = ZonedDateTime.ofInstant(at, zone).toLocalDate();
        for (int i = 0; i < 8; i++) {
            LocalDate day = start.minusDays(i);
            if (!isSelectedDay(s, day)) continue;
            Instant candidate = ZonedDateTime.of(day, s.getOnTime(), zone).toInstant();
            if (!candidate.isAfter(at)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * All ON and OFF slots due within [windowStart, windowEnd] (inclusive).
     * OFF slots are emitted independently of whether their ON was claimed —
     * firing a late OFF is the fail-safe direction.
     */
    public List<DueSlot> dueSlots(MachineSchedule s, Instant windowStart, Instant windowEnd) {
        ZoneId zone = zoneOf(s);
        List<DueSlot> slots = new ArrayList<>();
        // ON days may start 1 day before the window: their paired OFF can
        // still land inside it (cross-midnight case).
        LocalDate first = ZonedDateTime.ofInstant(windowStart, zone).toLocalDate().minusDays(1);
        LocalDate last = ZonedDateTime.ofInstant(windowEnd, zone).toLocalDate();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (!isSelectedDay(s, day)) continue;
            Instant on = ZonedDateTime.of(day, s.getOnTime(), zone).toInstant();
            if (within(on, windowStart, windowEnd)) {
                slots.add(new DueSlot(SlotType.ON, on));
            }
            Instant off = pairedOffInstant(s, on);
            if (within(off, windowStart, windowEnd)) {
                slots.add(new DueSlot(SlotType.OFF, off));
            }
        }
        slots.sort(Comparator.comparing(DueSlot::instant));
        return slots;
    }

    private boolean isSelectedDay(MachineSchedule s, LocalDate day) {
        int mask = s.getDaysOfWeek() == null ? 0 : s.getDaysOfWeek();
        return (mask & dayBit(day.getDayOfWeek())) != 0;
    }

    private boolean within(Instant t, Instant start, Instant end) {
        return !t.isBefore(start) && !t.isAfter(end);
    }

    private ZoneId zoneOf(MachineSchedule s) {
        String tz = s.getTimezone();
        return ZoneId.of(tz == null || tz.isBlank() ? "Asia/Kolkata" : tz);
    }

    /**
     * Validates a timezone string.
     * @return the parsed ZoneId
     * @throws IllegalArgumentException if invalid
     */
    public ZoneId requireValidZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("Timezone is required");
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }

    /**
     * Validates schedule time fields.
     * @throws IllegalArgumentException on invalid input
     */
    public void validateTimes(LocalTime onTime, LocalTime offTime, Integer daysOfWeek, String timezone) {
        if (onTime == null || offTime == null) {
            throw new IllegalArgumentException("onTime and offTime are required");
        }
        if (onTime.equals(offTime)) {
            throw new IllegalArgumentException("onTime and offTime must differ");
        }
        if (daysOfWeek == null || daysOfWeek < 1 || daysOfWeek > ALL_DAYS) {
            throw new IllegalArgumentException("daysOfWeek must select at least one day (1-127)");
        }
        requireValidZone(timezone);
    }
}
