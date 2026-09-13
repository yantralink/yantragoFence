package com.yantrago.api.service;

import com.yantrago.api.dto.geofence.GeofenceDto;
import com.yantrago.api.dto.geofence.GeofenceRequest;
import com.yantrago.api.model.Geofence;
import com.yantrago.api.repository.GeofenceRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Geofence CRUD service with tenant isolation.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 21: returns DTOs, never JPA entities.
 *
 * One active geofence per machine — creating a new geofence for a machine
 * that already has one will deactivate the old one.
 */
@Service
public class GeofenceService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceService.class);

    private final GeofenceRepository geofenceRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;

    public GeofenceService(GeofenceRepository geofenceRepository,
                            OwnerContextService ownerContextService,
                            TenantGuard tenantGuard,
                            PermissionEvaluator permissionEvaluator) {
        this.geofenceRepository = geofenceRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Transactional(readOnly = true)
    public List<GeofenceDto> listGeofences() {
        UUID orgId = ownerContextService.getOrganizationId();
        return geofenceRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public GeofenceDto getGeofence(UUID geofenceId) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());
        return toDto(geofence);
    }

    @Transactional(readOnly = true)
    public GeofenceDto getGeofenceForMachine(UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();
        return geofenceRepository.findByOrganizationIdAndMachineId(orgId, machineId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public GeofenceDto createGeofence(GeofenceRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Deactivate existing active geofence for this machine (one active per machine)
        geofenceRepository.findActiveByMachineId(orgId, request.getMachineId())
                .ifPresent(existing -> {
                    existing.setIsActive(false);
                    geofenceRepository.save(existing);
                    log.info("Deactivated previous geofence {} for machine={}",
                            existing.getId(), request.getMachineId());
                });

        Geofence geofence = new Geofence();
        geofence.setOrganizationId(orgId);
        geofence.setMachineId(request.getMachineId());
        geofence.setName(request.getName());
        geofence.setLatitude(request.getLatitude());
        geofence.setLongitude(request.getLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        geofence.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        geofence = geofenceRepository.save(geofence);
        log.info("Created geofence id={} machine={} org={} lat={} lng={} radius={}m",
                geofence.getId(), geofence.getMachineId(), orgId,
                geofence.getLatitude(), geofence.getLongitude(), geofence.getRadiusMeters());
        return toDto(geofence);
    }

    @Transactional
    public GeofenceDto updateGeofence(UUID geofenceId, GeofenceRequest request) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());

        geofence.setMachineId(request.getMachineId());
        geofence.setName(request.getName());
        geofence.setLatitude(request.getLatitude());
        geofence.setLongitude(request.getLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        if (request.getIsActive() != null) {
            geofence.setIsActive(request.getIsActive());
        }

        geofence = geofenceRepository.save(geofence);
        log.info("Updated geofence id={} machine={}", geofence.getId(), geofence.getMachineId());
        return toDto(geofence);
    }

    @Transactional
    public void deleteGeofence(UUID geofenceId) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());
        geofenceRepository.delete(geofence);
        log.info("Deleted geofence id={}", geofenceId);
    }

    @Transactional
    public GeofenceDto toggleGeofence(UUID geofenceId, boolean active) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());
        geofence.setIsActive(active);
        geofence = geofenceRepository.save(geofence);
        log.info("Toggled geofence id={} active={}", geofenceId, active);
        return toDto(geofence);
    }

    private GeofenceDto toDto(Geofence g) {
        return new GeofenceDto(
                g.getId(),
                g.getOrganizationId(),
                g.getMachineId(),
                g.getName(),
                g.getLatitude(),
                g.getLongitude(),
                g.getRadiusMeters(),
                g.getIsActive(),
                g.getCreatedAt(),
                g.getUpdatedAt()
        );
    }
}
