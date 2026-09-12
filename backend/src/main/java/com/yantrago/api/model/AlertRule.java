package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alert_rules", indexes = {
        @jakarta.persistence.Index(name = "idx_alert_rules_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_alert_rules_machine_id", columnList = "machine_id")
})
public class AlertRule extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "machine_id")
    private java.util.UUID machineId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_config", nullable = false, columnDefinition = "jsonb")
    private String conditionConfig;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "WARNING";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // --- Phase 2 lifecycle config (V23) ---

    @Column(name = "sustain_minutes", nullable = false)
    private Integer sustainMinutes = 0;

    @Column(name = "recovery_minutes", nullable = false)
    private Integer recoveryMinutes = 5;

    @Column(name = "escalation_minutes")
    private Integer escalationMinutes;

    @Column(name = "escalation_severity", length = 20)
    private String escalationSeverity;

    @Column(name = "rule_version", nullable = false)
    private Integer ruleVersion = 1;

    @Column(name = "updated_by")
    private java.util.UUID updatedBy;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getMachineId() { return machineId; }
    public void setMachineId(java.util.UUID machineId) { this.machineId = machineId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getConditionConfig() { return conditionConfig; }
    public void setConditionConfig(String conditionConfig) { this.conditionConfig = conditionConfig; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Integer getSustainMinutes() { return sustainMinutes; }
    public void setSustainMinutes(Integer sustainMinutes) { this.sustainMinutes = sustainMinutes; }
    public Integer getRecoveryMinutes() { return recoveryMinutes; }
    public void setRecoveryMinutes(Integer recoveryMinutes) { this.recoveryMinutes = recoveryMinutes; }
    public Integer getEscalationMinutes() { return escalationMinutes; }
    public void setEscalationMinutes(Integer escalationMinutes) { this.escalationMinutes = escalationMinutes; }
    public String getEscalationSeverity() { return escalationSeverity; }
    public void setEscalationSeverity(String escalationSeverity) { this.escalationSeverity = escalationSeverity; }
    public Integer getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(Integer ruleVersion) { this.ruleVersion = ruleVersion; }
    public java.util.UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(java.util.UUID updatedBy) { this.updatedBy = updatedBy; }
}
