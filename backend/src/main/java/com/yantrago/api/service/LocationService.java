package com.yantrago.api.service;

import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.LocationRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Location service — stores GPS data and queries location history.
 * Uses LocationRepository (JdbcTemplate) for partitioned time-series tables.
 * Uses LocationPersistenceService for batch inserts.
 *
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository locationRepository;
    private final LocationPersistenceService locationPersistenceService;
    private final MachineRepository machineRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public LocationService(LocationRepository locationRepository,
                           LocationPersistenceService locationPersistenceService,
                           MachineRepository machineRepository,
                           OwnerContextService ownerContextService,
                           TenantGuard tenantGuard) {
        this.locationRepository = locationRepository;
        this.locationPersistenceService = locationPersistenceService;
        this.machineRepository = machineRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    /**
     * Upserts the current device location (device_locations table).
     * Also adds to the location history buffer for batch insertion.
     */
    @Transactional
    public void updateLocation(UUID deviceId, UUID machineId, Double latitude, Double longitude,
                                Double speed, Double course, LocalDateTime recordedAt) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Upsert current location
        locationRepository.upsertDeviceLocation(deviceId, orgId, machineId, latitude, longitude,
                speed, course, recordedAt);

        // Add to history buffer for batch insert
        locationPersistenceService.addToBuffer(
                UUID.randomUUID(), orgId, deviceId, machineId, null,
                latitude, longitude, speed, course, recordedAt);

        log.debug("Updated location for device={} lat={} lon={}", deviceId, latitude, longitude);
    }

    /**
     * Gets the current location for a machine's device.
     */
    @Transactional(readOnly = true)
    public LocationDto getCurrentLocation(UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Validate machine belongs to tenant
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        // Find device bound to this machine
        if (machine.getSerialNumber() == null) {
            return null;
        }

        // Query current location via JdbcTemplate
        Map<String, Object> row = locationRepository.findCurrentLocation(orgId, null);
        if (row == null) {
            return null;
        }

        return mapToLocationDto(row);
    }

    /**
     * Gets location history for a machine within a time range.
     */
    @Transactional(readOnly = true)
    public List<LocationDto> getLocationHistory(UUID machineId, LocalDateTime from, LocalDateTime to) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Validate machine belongs to tenant
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        List<Map<String, Object>> rows = locationRepository.findLocationHistory(orgId, machineId, from, to);

        return rows.stream()
                .map(this::mapHistoryRowToDto)
                .toList();
    }

    private LocationDto mapToLocationDto(Map<String, Object> row) {
        return new LocationDto(
                (UUID) row.get("device_id"),
                (UUID) row.get("machine_id"),
                ((Number) row.get("latitude")).doubleValue(),
                ((Number) row.get("longitude")).doubleValue(),
                row.get("speed") != null ? ((Number) row.get("speed")).doubleValue() : null,
                row.get("course") != null ? ((Number) row.get("course")).doubleValue() : null,
                (LocalDateTime) row.get("recorded_at"),
                (LocalDateTime) row.get("updated_at")
        );
    }

    private LocationDto mapHistoryRowToDto(Map<String, Object> row) {
        return new LocationDto(
                null,
                null,
                ((Number) row.get("latitude")).doubleValue(),
                ((Number) row.get("longitude")).doubleValue(),
                row.get("speed") != null ? ((Number) row.get("speed")).doubleValue() : null,
                row.get("course") != null ? ((Number) row.get("course")).doubleValue() : null,
                (LocalDateTime) row.get("recorded_at"),
                null
        );
    }
}
