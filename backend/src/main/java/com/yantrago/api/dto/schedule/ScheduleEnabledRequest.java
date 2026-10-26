package com.yantrago.api.dto.schedule;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for enabling/disabling a machine's schedule.
 */
public class ScheduleEnabledRequest {

    @NotNull
    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
