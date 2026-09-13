package com.yantrago.api.service;

import com.yantrago.api.dto.geofence.GeofenceDto;
import com.yantrago.api.dto.theft.TheftProtectionStatusDto;
import com.yantrago.api.model.AlertRule;
import com.yantrago.api.model.Geofence;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.AlertRuleRepository;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.GeofenceRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Theft Protection toggle service — enables/disables theft protection for a machine.
 *
 * Per Phase 11 design: theft protection is customer-controlled. When the customer
 * enables it, a geofence is created at the machine's current GPS location and
 * the MACHINE_MOVING alert rule is activated. When disabled, both are deactivated.
 *
 * This prevents false alerts during transport from shop to farm — the customer
 * enables protection only after installing the machine at its final location.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 */
@Service
public class TheftProtectionService {

    private static final Logger log = LoggerFactory.getLogger(TheftProtectionService.class);

    private static final int DEFAULT_RADIUS_METERS = 200;
    private static final int DEFAULT_SPEED_THRESHOLD_KMH = 10;
    private static final String DEFAULT_GEOFENCE_NAME = "My Farm Geofence";

    private final MachineRepository machineRepository;
    private final GeofenceRepository geofenceRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSettingsService customerSettingsService;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final JdbcTemplate jdbcTemplate;

    public TheftProtectionService(MachineRepository machineRepository,
                                    GeofenceRepository geofenceRepository,
                                    AlertRuleRepository alertRuleRepository,
                                    CustomerRepository customerRepository,
                                    CustomerSettingsService customerSettingsService,
                                    OwnerContextService ownerContextService,
                                    TenantGuard tenantGuard,
                                    JdbcTemplate jdbcTemplate) {
        this.machineRepository = machineRepository;
        this.geofenceRepository = geofenceRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.customerRepository = customerRepository;
        this.customerSettingsService = customerSettingsService;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Enables theft protection for a machine:
     * 1. Creates a geofence at the machine's current GPS location (default 200m radius)
     * 2. Activates the MACHINE_MOVING alert rule
     *
     * @param machineId the machine to protect
     * @return status DTO showing the protection state
     */
    @Transactional
    public TheftProtectionStatusDto enable(UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        validateCustomerOwnership(machine);

        // Get the machine's current GPS location
        double[] gps = getLatestGps(machineId);
        if (gps == null) {
            throw new IllegalStateException(
                    "No GPS location available for this machine yet. " +
                    "Wait for the device to send its first location, then enable protection.");
        }
        double latitude = gps[0];
        double longitude = gps[1];

        // Get customer defaults (or system defaults if customer hasn't set any)
        int radiusMeters = DEFAULT_RADIUS_METERS;
        int speedThreshold = DEFAULT_SPEED_THRESHOLD_KMH;
        UUID customerId = getCustomerIdIfCustomer();
        if (customerId != null) {
            com.yantrago.api.model.CustomerSettings settings =
                    customerSettingsService.getOrCreateDefaults(customerId);
            radiusMeters = settings.getDefaultGeofenceRadiusMeters();
            speedThreshold = settings.getDefaultSpeedThresholdKmh();
        }
        final int finalRadiusMeters = radiusMeters;
        final int finalSpeedThreshold = speedThreshold;

        // Create or update geofence at current GPS location
        Geofence geofence = geofenceRepository.findActiveByMachineId(orgId, machineId)
                .map(existing -> {
                    // Update existing geofence in place (avoids unique constraint violation)
                    existing.setLatitude(latitude);
                    existing.setLongitude(longitude);
                    existing.setRadiusMeters(finalRadiusMeters);
                    existing.setIsActive(true);
                    return existing;
                })
                .orElseGet(() -> {
                    // No existing geofence — create a new one
                    Geofence g = new Geofence();
                    g.setOrganizationId(orgId);
                    g.setMachineId(machineId);
                    g.setName(DEFAULT_GEOFENCE_NAME);
                    g.setLatitude(latitude);
                    g.setLongitude(longitude);
                    g.setRadiusMeters(finalRadiusMeters);
                    g.setIsActive(true);
                    return g;
                });
        geofenceRepository.save(geofence);
        geofenceRepository.flush();

        // Activate the MACHINE_MOVING alert rule and update threshold to match defaults
        List<AlertRule> rules = alertRuleRepository.findAllRulesByTypeAndMachine(
                orgId, machineId, "MACHINE_MOVING");
        String conditionConfig = String.format(
                "{\"metric\":\"speed\",\"operator\":\"GT\",\"threshold\":%d}", speedThreshold);
        for (AlertRule rule : rules) {
            if (!rule.getIsActive()) {
                rule.setIsActive(true);
            }
            rule.setConditionConfig(conditionConfig);
            alertRuleRepository.save(rule);
            log.info("Activated MACHINE_MOVING rule {} for machine={} threshold={}km/h",
                    rule.getId(), machineId, speedThreshold);
        }

        log.info("Theft protection ENABLED for machine={} at lat={},lng={} radius={}m speedThreshold={}km/h",
                machineId, latitude, longitude, radiusMeters, speedThreshold);
        return getStatus(machineId);
    }

    /**
     * Disables theft protection for a machine:
     * 1. Deactivates the geofence
     * 2. Deactivates the MACHINE_MOVING alert rule
     *
     * @param machineId the machine to unprotect
     * @return status DTO showing the protection state
     */
    @Transactional
    public TheftProtectionStatusDto disable(UUID machineId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        validateCustomerOwnership(machine);
        UUID orgId = machine.getOrganizationId();

        // Deactivate geofence
        geofenceRepository.findActiveByMachineId(orgId, machineId)
                .ifPresent(geofence -> {
                    geofence.setIsActive(false);
                    geofenceRepository.save(geofence);
                    log.info("Deactivated geofence {} for machine={}", geofence.getId(), machineId);
                });

        // Deactivate MACHINE_MOVING rule
        List<AlertRule> rules = alertRuleRepository.findAllRulesByTypeAndMachine(
                orgId, machineId, "MACHINE_MOVING");
        for (AlertRule rule : rules) {
            if (rule.getIsActive()) {
                rule.setIsActive(false);
                alertRuleRepository.save(rule);
                log.info("Deactivated MACHINE_MOVING rule {} for machine={}", rule.getId(), machineId);
            }
        }

        log.info("Theft protection DISABLED for machine={}", machineId);
        return getStatus(machineId);
    }

    /**
     * Returns the current theft protection status for a machine.
     */
    @Transactional(readOnly = true)
    public TheftProtectionStatusDto getStatus(UUID machineId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        validateCustomerOwnership(machine);
        UUID orgId = machine.getOrganizationId();

        // Check if geofence is active
        Geofence activeGeofence = geofenceRepository
                .findActiveByMachineId(orgId, machineId).orElse(null);
        boolean geofenceActive = activeGeofence != null;

        // Check if MACHINE_MOVING rule is active
        boolean movementRuleActive = alertRuleRepository
                .findAllRulesByTypeAndMachine(orgId, machineId, "MACHINE_MOVING")
                .stream()
                .anyMatch(AlertRule::getIsActive);

        boolean protectionEnabled = geofenceActive && movementRuleActive;

        // Get latest GPS for context
        double[] gps = getLatestGps(machineId);

        return new TheftProtectionStatusDto(
                machineId,
                protectionEnabled,
                geofenceActive,
                movementRuleActive,
                activeGeofence != null ? activeGeofence.getId() : null,
                activeGeofence != null ? activeGeofence.getLatitude() : null,
                activeGeofence != null ? activeGeofence.getLongitude() : null,
                activeGeofence != null ? activeGeofence.getRadiusMeters() : null,
                gps != null ? gps[0] : null,
                gps != null ? gps[1] : null
        );
    }

    /**
     * Fetches the latest GPS coordinates for a machine from device_locations.
     * Returns null if no GPS data is available yet.
     */
    private double[] getLatestGps(UUID machineId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT latitude, longitude FROM device_locations " +
                    "WHERE machine_id = ? ORDER BY recorded_at DESC LIMIT 1",
                    machineId);
            double lat = ((Number) row.get("latitude")).doubleValue();
            double lng = ((Number) row.get("longitude")).doubleValue();
            return new double[]{lat, lng};
        } catch (Exception e) {
            log.debug("No GPS data found for machineId={}: {}", machineId, e.getMessage());
            return null;
        }
    }

    /**
     * Validates that the current customer owns the specified machine.
     * Admin/org_admin roles bypass this check.
     */
    private void validateCustomerOwnership(Machine machine) {
        UUID customerId = getCustomerIdIfCustomer();
        if (customerId == null) {
            return; // admin role — org-wide access
        }
        if (!customerId.equals(machine.getCustomerId())) {
            throw new SecurityException(
                    "Access denied: machine " + machine.getId() + " is not assigned to you");
        }
    }

    /**
     * Returns the customer ID if the current user has the 'customer' role.
     */
    private UUID getCustomerIdIfCustomer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        boolean isCustomer = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_CUSTOMER".equals(a));
        if (!isCustomer) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof UUID userId)) {
            return null;
        }
        return customerRepository.findByUserId(userId)
                .map(c -> c.getId())
                .orElse(null);
    }
}
