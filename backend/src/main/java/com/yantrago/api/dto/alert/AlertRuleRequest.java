package com.yantrago.api.dto.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for creating/updating alert rules.
 *
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 12: validation via Bean Validation.
 */
public class AlertRuleRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 50)
    private String alertType;

    @NotBlank
    private String conditionConfig; // JSON string

    @NotBlank
    @Size(max = 20)
    private String severity;

    private UUID machineId; // null = org-wide rule

    @Min(0)
    @Max(10080) // max 1 week
    private Integer sustainMinutes = 0;

    @Min(0)
    @Max(10080)
    private Integer recoveryMinutes = 5;

    @Min(0)
    @Max(10080)
    private Integer escalationMinutes;

    @Size(max = 20)
    private String escalationSeverity;

    private Boolean isActive = true;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getConditionConfig() { return conditionConfig; }
    public void setConditionConfig(String conditionConfig) { this.conditionConfig = conditionConfig; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public UUID getMachineId() { return machineId; }
    public void setMachineId(UUID machineId) { this.machineId = machineId; }
    public Integer getSustainMinutes() { return sustainMinutes; }
    public void setSustainMinutes(Integer sustainMinutes) { this.sustainMinutes = sustainMinutes; }
    public Integer getRecoveryMinutes() { return recoveryMinutes; }
    public void setRecoveryMinutes(Integer recoveryMinutes) { this.recoveryMinutes = recoveryMinutes; }
    public Integer getEscalationMinutes() { return escalationMinutes; }
    public void setEscalationMinutes(Integer escalationMinutes) { this.escalationMinutes = escalationMinutes; }
    public String getEscalationSeverity() { return escalationSeverity; }
    public void setEscalationSeverity(String escalationSeverity) { this.escalationSeverity = escalationSeverity; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
