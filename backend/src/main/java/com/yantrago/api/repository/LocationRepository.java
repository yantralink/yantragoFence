package com.yantrago.api.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Location data access via JdbcTemplate for high-volume time-series writes
 * (location_history — partitioned by month).
 * Batch inserts are used for performance (AGENTS.md rule 18).
 * Pattern adapted from HarvestTracker's LocationPersistenceService.
 */
@Repository
public class LocationRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void batchInsertLocationHistory(List<Object[]> rows) {
        String sql = "INSERT INTO location_history (id, organization_id, device_id, machine_id, imei, " +
                "latitude, longitude, speed, course, recorded_at, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows);
    }

    public void upsertDeviceLocation(UUID deviceId, UUID organizationId, UUID machineId,
                                     double latitude, double longitude, Double speed, Double course,
                                     LocalDateTime recordedAt) {
        String sql = "INSERT INTO device_locations (device_id, organization_id, machine_id, latitude, longitude, " +
                "speed, course, recorded_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now()) " +
                "ON CONFLICT (device_id) DO UPDATE SET " +
                "organization_id = EXCLUDED.organization_id, " +
                "machine_id = EXCLUDED.machine_id, " +
                "latitude = EXCLUDED.latitude, " +
                "longitude = EXCLUDED.longitude, " +
                "speed = EXCLUDED.speed, " +
                "course = EXCLUDED.course, " +
                "recorded_at = EXCLUDED.recorded_at, " +
                "updated_at = now()";
        jdbcTemplate.update(sql, deviceId, organizationId, machineId, latitude, longitude, speed, course, recordedAt);
    }

    public Map<String, Object> findCurrentLocation(UUID organizationId, UUID deviceId) {
        String sql = "SELECT * FROM device_locations WHERE organization_id = ? AND device_id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, organizationId, deviceId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Map<String, Object>> findLocationHistory(UUID organizationId, UUID machineId,
                                                          LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT recorded_at, latitude, longitude, speed, course FROM location_history " +
                "WHERE organization_id = ? AND machine_id = ? AND recorded_at BETWEEN ? AND ? " +
                "ORDER BY recorded_at ASC";
        return jdbcTemplate.queryForList(sql, organizationId, machineId, from, to);
    }
}
