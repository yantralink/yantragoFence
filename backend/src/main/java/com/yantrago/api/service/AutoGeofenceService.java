package com.yantrago.api.service;

import com.yantrago.api.model.Geofence;
import com.yantrago.api.repository.GeofenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Auto-creates a geofence using the first GPS location as the center.
 *
 * When enabled (geofence.auto-create.enabled=true), if a machine receives its
 * first GPS location and has no geofence, one is automatically created with the
 * configured default radius. This protects machines from day one without
 * requiring manual setup.
 *
 * Per AGENTS.md rule 7: orgId is resolved from the device record, never from
 * the message payload (RabbitMQ consumer has no HTTP/JWT context).
 */
@Service
public class AutoGeofenceService {

    private static final Logger log = LoggerFactory.getLogger(AutoGeofenceService.class);

    private final GeofenceRepository geofenceRepository;
    private final boolean autoCreateEnabled;
    private final int defaultRadiusMeters;
    private final String nameTemplate;

    public AutoGeofenceService(
            GeofenceRepository geofenceRepository,
            @Value("${geofence.auto-create.enabled:false}") boolean autoCreateEnabled,
            @Value("${geofence.auto-create.default-radius-meters:200}") int defaultRadiusMeters,
            @Value("${geofence.auto-create.name-template:Auto-Geofence}") String nameTemplate) {
        this.geofenceRepository = geofenceRepository;
        this.autoCreateEnabled = autoCreateEnabled;
        this.defaultRadiusMeters = defaultRadiusMeters;
        this.nameTemplate = nameTemplate;
    }

    /**
     * If auto-creation is enabled and the machine has no geofence (active or
     * inactive), creates one centered on the given GPS coordinates.
     *
     * @param orgId      organization ID (from device record)
     * @param machineId   machine ID (from device record)
     * @param latitude   current GPS latitude
     * @param longitude  current GPS longitude
     */
    @Transactional
    public void autoCreateIfMissing(UUID orgId, UUID machineId, double latitude, double longitude) {
        if (!autoCreateEnabled) {
            return;
        }
        if (orgId == null || machineId == null) {
            return;
        }

        // Check if any geofence exists for this machine (active or inactive)
        Optional<Geofence> existing = geofenceRepository
                .findByOrganizationIdAndMachineId(orgId, machineId);
        if (existing.isPresent()) {
            return; // already has a geofence — don't auto-create
        }

        // Create a new geofence at the current location
        Geofence geofence = new Geofence();
        geofence.setOrganizationId(orgId);
        geofence.setMachineId(machineId);
        geofence.setName(nameTemplate);
        geofence.setLatitude(latitude);
        geofence.setLongitude(longitude);
        geofence.setRadiusMeters(defaultRadiusMeters);
        geofence.setIsActive(true);

        geofenceRepository.save(geofence);
        log.info("Auto-created geofence for machine={} at lat={},lng={} radius={}m",
                machineId, latitude, longitude, defaultRadiusMeters);
    }
}
