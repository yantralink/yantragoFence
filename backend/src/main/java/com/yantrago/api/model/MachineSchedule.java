package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Automatic ON/OFF schedule for a fencing machine (one per machine).
 *
 * Cycle semantics: daysOfWeek (bitmask Mon=1 ... Sun=64) applies to the ON
 * event; the paired OFF is the next occurrence of offTime strictly after the
 * ON instant — next calendar day when offTime <= onTime.
 */
@Entity
@Table(name = "machine_schedules", indexes = {
        @jakarta.persistence.Index(name = "idx_machine_schedules_org", columnList = "organization_id")
})
public class MachineSchedule extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "machine_id", nullable = false, unique = true)
    private UUID machineId;

    @Column(name = "on_time", nullable = false)
    private LocalTime onTime;

    @Column(name = "off_time", nullable = false)
    private LocalTime offTime;

    @Column(name = "days_of_week", nullable = false)
    private Integer daysOfWeek = 127;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Asia/Kolkata";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_by")
    private UUID createdBy;

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public LocalTime getOnTime() { return onTime; }
    public void setOnTime(LocalTime onTime) { this.onTime = onTime; }
    public LocalTime getOffTime() { return offTime; }
    public void setOffTime(LocalTime offTime) { this.offTime = offTime; }
    public Integer getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(Integer daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
