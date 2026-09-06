package com.yantrago.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "machine_settings", indexes = {
        @jakarta.persistence.Index(name = "idx_machine_settings_organization_id", columnList = "organization_id"),
        @jakarta.persistence.Index(name = "idx_machine_settings_machine_id", columnList = "machine_id")
})
public class MachineSetting extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "machine_id", nullable = false)
    private java.util.UUID machineId;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String settingValue;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType = "STRING";

    @Column(name = "description", length = 500)
    private String description;

    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID organizationId) { this.organizationId = organizationId; }
    public java.util.UUID getMachineId() { return machineId; }
    public void setMachineId(java.util.UUID machineId) { this.machineId = machineId; }
    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
