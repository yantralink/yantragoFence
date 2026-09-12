package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-machine/rule observation state for restart-safe sustain/recovery windows.
 * One row per (rule_id, machine_id). Updated atomically during rule evaluation.
 */
@Entity
@Table(name = "alert_rule_states")
public class AlertRuleState {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "violation_started_at")
    private LocalDateTime violationStartedAt;

    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    @Column(name = "last_value")
    private Double lastValue;

    @Column(name = "is_violating", nullable = false)
    private Boolean isViolating = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public LocalDateTime getViolationStartedAt() { return violationStartedAt; }
    public void setViolationStartedAt(LocalDateTime violationStartedAt) { this.violationStartedAt = violationStartedAt; }
    public LocalDateTime getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(LocalDateTime lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }
    public Double getLastValue() { return lastValue; }
    public void setLastValue(Double lastValue) { this.lastValue = lastValue; }
    public Boolean getIsViolating() { return isViolating; }
    public void setIsViolating(Boolean isViolating) { this.isViolating = isViolating; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
