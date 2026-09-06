package com.yantrago.api.repository;

import com.yantrago.api.model.MachineSetting;
import com.yantrago.api.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingsRepository extends JpaRepository<MachineSetting, UUID> {

    List<MachineSetting> findByOrganizationIdAndMachineId(UUID organizationId, UUID machineId);

    Optional<MachineSetting> findByOrganizationIdAndMachineIdAndSettingKey(UUID organizationId, UUID machineId, String settingKey);

    // SystemSetting queries — separate interface would be cleaner, but the plan
    // lists a single SettingsRepository, so we expose system setting lookups via
    // JdbcTemplate in the service layer for now.
}
