package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Execution record for one scheduled ON/OFF slot.
 *
 * The unique constraint (schedule_id, scheduled_slot, command_type) is the
 * duplicate-execution guard: inserting a run claims the slot atomically, so
 * the scheduler can tick frequently and survive restarts without double-firing.
 *
 * Status: PENDING | DISPATCHED | ACKED | FAILED | MISSED
 *   PENDING    — slot claimed, command being created
 *   DISPATCHED — machine_command created and published to the gateway
 *   ACKED      — device confirmed (command reached DONE)
 *   FAILED     — all attempts exhausted
 *   MISSED     — slot not claimed in time (never fired late, ON safety cap)
 */
@Entity
@Table(name = "schedule_runs", indexes = {
        @jakarta.persistence.Index(name = "idx_schedule_runs_schedule", columnList = "schedule_id, scheduled_slot")
})
public class ScheduleRun extends BaseEntity {

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "scheduled_slot", nullable = false)
    private LocalDateTime scheduledSlot;

    @Column(name = "command_type", nullable = false, length = 10)
    private String commandType;

    @Column(name = "command_id")
    private UUID commandId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }
    public LocalDateTime getScheduledSlot() { return scheduledSlot; }
    public void setScheduledSlot(LocalDateTime scheduledSlot) { this.scheduledSlot = scheduledSlot; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }
    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
}
