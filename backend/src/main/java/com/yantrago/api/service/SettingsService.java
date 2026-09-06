package com.yantrago.api.service;

import com.yantrago.api.model.MachineSetting;
import com.yantrago.api.model.SystemSetting;
import com.yantrago.api.repository.SettingsRepository;
import com.yantrago.api.security.TenantGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Settings service — manages machine settings and system settings.
 *
 * Machine settings are per-machine key-value pairs (e.g. reporting_interval, geofence_radius).
 * System settings are per-organization or global key-value pairs (e.g. default_alert_threshold).
 *
 * All queries filter by organization_id from OwnerContextService.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepository settingsRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final EntityManager entityManager;

    public SettingsService(SettingsRepository settingsRepository,
                           OwnerContextService ownerContextService,
                           TenantGuard tenantGuard,
                           EntityManager entityManager) {
        this.settingsRepository = settingsRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.entityManager = entityManager;
    }

    // ===== Machine Settings =====

    @Transactional(readOnly = true)
    public List<MachineSetting> getMachineSettings(UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();
        return settingsRepository.findByOrganizationIdAndMachineId(orgId, machineId);
    }

    @Transactional(readOnly = true)
    public MachineSetting getMachineSetting(UUID machineId, String key) {
        UUID orgId = ownerContextService.getOrganizationId();
        return settingsRepository.findByOrganizationIdAndMachineIdAndSettingKey(orgId, machineId, key)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Setting not found: machineId=" + machineId + " key=" + key));
    }

    @Transactional
    public MachineSetting upsertMachineSetting(UUID machineId, String key, String value, String dataType, String description) {
        UUID orgId = ownerContextService.getOrganizationId();

        MachineSetting setting = settingsRepository
                .findByOrganizationIdAndMachineIdAndSettingKey(orgId, machineId, key)
                .orElseGet(() -> {
                    MachineSetting s = new MachineSetting();
                    s.setOrganizationId(orgId);
                    s.setMachineId(machineId);
                    s.setSettingKey(key);
                    return s;
                });

        setting.setSettingValue(value);
        if (dataType != null) setting.setDataType(dataType);
        if (description != null) setting.setDescription(description);

        setting = settingsRepository.save(setting);
        log.info("Upserted machine setting machineId={} key={} value={}", machineId, key, value);
        return setting;
    }

    @Transactional
    public void deleteMachineSetting(UUID machineId, String key) {
        UUID orgId = ownerContextService.getOrganizationId();
        MachineSetting setting = settingsRepository
                .findByOrganizationIdAndMachineIdAndSettingKey(orgId, machineId, key)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Setting not found: machineId=" + machineId + " key=" + key));
        settingsRepository.delete(setting);
        log.info("Deleted machine setting machineId={} key={}", machineId, key);
    }

    // ===== System Settings =====

    @Transactional(readOnly = true)
    public List<SystemSetting> getSystemSettings() {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SystemSetting> cq = cb.createQuery(SystemSetting.class);
        Root<SystemSetting> root = cq.from(SystemSetting.class);

        if (orgId != null) {
            // Tenant user: see org-specific + global settings (organization_id = orgId OR organization_id IS NULL)
            Predicate orgPredicate = cb.or(
                    cb.equal(root.get("organizationId"), orgId),
                    cb.isNull(root.get("organizationId"))
            );
            cq.where(orgPredicate);
        }
        // Super_admin: see all settings (no filter)

        return entityManager.createQuery(cq).getResultList();
    }

    @Transactional(readOnly = true)
    public SystemSetting getSystemSetting(String key) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SystemSetting> cq = cb.createQuery(SystemSetting.class);
        Root<SystemSetting> root = cq.from(SystemSetting.class);

        Predicate keyPredicate = cb.equal(root.get("settingKey"), key);
        if (orgId != null) {
            Predicate orgPredicate = cb.or(
                    cb.equal(root.get("organizationId"), orgId),
                    cb.isNull(root.get("organizationId"))
            );
            cq.where(keyPredicate, orgPredicate);
        } else {
            cq.where(keyPredicate);
        }

        List<SystemSetting> results = entityManager.createQuery(cq).getResultList();
        if (results.isEmpty()) {
            throw new IllegalArgumentException("System setting not found: " + key);
        }
        // Prefer org-specific over global
        return results.stream()
                .filter(s -> orgId != null && orgId.equals(s.getOrganizationId()))
                .findFirst()
                .orElse(results.get(0));
    }

    @Transactional
    public SystemSetting upsertSystemSetting(String key, String value, String dataType, String description, Boolean isSensitive) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();

        // Try to find existing org-specific setting
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SystemSetting> cq = cb.createQuery(SystemSetting.class);
        Root<SystemSetting> root = cq.from(SystemSetting.class);
        Predicate keyPredicate = cb.equal(root.get("settingKey"), key);
        Predicate orgPredicate = orgId != null
                ? cb.equal(root.get("organizationId"), orgId)
                : cb.isNull(root.get("organizationId"));
        cq.where(keyPredicate, orgPredicate);

        List<SystemSetting> existing = entityManager.createQuery(cq).getResultList();

        SystemSetting setting = existing.isEmpty() ? new SystemSetting() : existing.get(0);
        setting.setOrganizationId(orgId);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        if (dataType != null) setting.setDataType(dataType);
        if (description != null) setting.setDescription(description);
        if (isSensitive != null) setting.setIsSensitive(isSensitive);

        setting = entityManager.merge(setting);
        log.info("Upserted system setting key={} orgId={}", key, orgId);
        return setting;
    }
}
