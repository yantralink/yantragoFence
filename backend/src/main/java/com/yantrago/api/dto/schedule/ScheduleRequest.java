package com.yantrago.api.dto.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Request DTO for creating/updating a machine's ON/OFF schedule (upsert).
 *
 * Cycle semantics: daysOfWeek applies to the ON event; the paired OFF fires
 * at the next offTime strictly after the ON instant (next day when
 * offTime <= onTime).
 *
 * Per AGENTS.md rule 12: validation via Bean Validation; service re-validates
 * cross-field rules (onTime != offTime, valid IANA timezone).
 */
public class ScheduleRequest {

    @NotNull
    private LocalTime onTime;

    @NotNull
    private LocalTime offTime;

    @Min(value = 1, message = "daysOfWeek must select at least one day")
    @Max(value = 127, message = "daysOfWeek bitmask must be between 1 and 127")
    private int daysOfWeek = 127;

    @NotBlank
    @Size(max = 50)
    private String timezone = "Asia/Kolkata";

    private Boolean enabled = true;

    public LocalTime getOnTime() { return onTime; }
    public void setOnTime(LocalTime onTime) { this.onTime = onTime; }
    public LocalTime getOffTime() { return offTime; }
    public void setOffTime(LocalTime offTime) { this.offTime = offTime; }
    public int getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(int daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
