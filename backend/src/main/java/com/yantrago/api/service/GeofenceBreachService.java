package com.yantrago.api.service;

import com.yantrago.api.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Geo-fence breach detection service.
 *
 * Called by LocationConsumer after each GPS location update. Checks if the
 * machine has moved outside its active geofence boundary using PostGIS
 * ST_DWithin. If outside, generates a GEOFENCE_BREACH alert via
 * CanonicalAlertService. If inside and a breach was open, resolves it.
 *
 * Per AGENTS.md rule 7: orgId is resolved from the device record, never
 * from the message payload (RabbitMQ consumer has no HTTP/JWT context).
 */
@Service
public class GeofenceBreachService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceBreachService.class);

    private final JdbcTemplate jdbcTemplate;
    private final CanonicalAlertService canonicalAlertService;

    public GeofenceBreachService(JdbcTemplate jdbcTemplate,
                                  CanonicalAlertService canonicalAlertService) {
        this.jdbcTemplate = jdbcTemplate;
        this.canonicalAlertService = canonicalAlertService;
    }

    /**
     * Checks if the machine at (latitude, longitude) is within its active
     * geofence. If outside, generates a GEOFENCE_BREACH alert. If inside
     * and a breach is open, resolves it.
     *
     * @param orgId      organization ID (from device record)
     * @param machineId   machine ID (from device record)
     * @param latitude   current GPS latitude
     * @param longitude  current GPS longitude
     */
    @Transactional
    public void checkBreach(UUID orgId, UUID machineId, double latitude, double longitude) {
        if (orgId == null || machineId == null) {
            return;
        }

        // Query the active geofence for this machine and compute distance
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, name, radius_meters, " +
                    "  ST_Distance(center, ST_MakePoint(?, ?)::geography) AS distance_meters " +
                    "FROM geofences " +
                    "WHERE organization_id = ? AND machine_id = ? AND is_active = true",
                    longitude, latitude, orgId, machineId
            );
        } catch (Exception e) {
            log.error("Failed to query geofence for machine={}: {}", machineId, e.getMessage());
            return;
        }

        if (rows.isEmpty()) {
            // No active geofence for this machine — nothing to check
            return;
        }

        Map<String, Object> row = rows.get(0);
        int radiusMeters = ((Number) row.get("radius_meters")).intValue();
        double distanceMeters = ((Number) row.get("distance_meters")).doubleValue();
        String geofenceName = (String) row.get("name");

        boolean isOutside = distanceMeters > radiusMeters;

        // Check if there's an existing open GEOFENCE_BREACH incident
        boolean hasOpenIncident = hasOpenBreachIncident(orgId, machineId);

        if (isOutside && !hasOpenIncident) {
            // Machine just left the geofence — open a breach incident
            String message = String.format(
                    "Machine moved %.0fm outside geo-fence '%s' (radius: %dm). Possible theft.",
                    distanceMeters, geofenceName, radiusMeters);

            canonicalAlertService.processAlertEvent(
                    machineId,
                    "GEOFENCE_BREACH",
                    "CRITICAL",
                    message,
                    Instant.now(),
                    distanceMeters,
                    "meters",
                    null
            );
            log.info("GEOFENCE_BREACH opened for machine={} distance={}m radius={}m geofence={}",
                    machineId, String.format("%.0f", distanceMeters), radiusMeters, geofenceName);

        } else if (!isOutside && hasOpenIncident) {
            // Machine returned inside the geofence — resolve the breach
            String resolutionMessage = String.format(
                    "Machine returned within geo-fence '%s' (distance: %.0fm, radius: %dm).",
                    geofenceName, distanceMeters, radiusMeters);

            canonicalAlertService.resolveIncident(
                    orgId, machineId, "GEOFENCE_BREACH", resolutionMessage);
            log.info("GEOFENCE_BREACH resolved for machine={} distance={}m radius={}m",
                    machineId, String.format("%.0f", distanceMeters), radiusMeters);

        } else if (isOutside && hasOpenIncident) {
            // Still outside — update the existing incident with latest distance
            log.debug("Machine {} still outside geofence (distance={}m radius={}m)",
                    machineId, String.format("%.0f", distanceMeters), radiusMeters);
        }
        // If inside and no open incident — nothing to do
    }

    /**
     * Checks if there's an open GEOFENCE_BREACH incident for this machine.
     */
    private boolean hasOpenBreachIncident(UUID orgId, UUID machineId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alerts " +
                    "WHERE organization_id = ? AND machine_id = ? " +
                    "AND alert_type = 'GEOFENCE_BREACH' " +
                    "AND incident_state = 'OPEN'",
                    Integer.class, orgId, machineId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check open GEOFENCE_BREACH incident for machine={}: {}",
                    machineId, e.getMessage());
            return false;
        }
    }
}
