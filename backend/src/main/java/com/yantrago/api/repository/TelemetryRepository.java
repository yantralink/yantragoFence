package com.yantrago.api.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Telemetry data access via JdbcTemplate for high-volume time-series writes
 * (voltage_readings, battery_readings, gsm_readings — all partitioned by month).
 * Batch inserts are used for performance (AGENTS.md rule 18).
 */
@Repository
public class TelemetryRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public TelemetryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void batchInsertVoltageReadings(List<Object[]> rows) {
        String sql = "INSERT INTO voltage_readings (id, organization_id, device_id, machine_id, imei, voltage, recorded_at, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows);
    }

    public void batchInsertBatteryReadings(List<Object[]> rows) {
        String sql = "INSERT INTO battery_readings (id, organization_id, device_id, machine_id, imei, battery_pct, recorded_at, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows);
    }

    public void batchInsertGsmReadings(List<Object[]> rows) {
        String sql = "INSERT INTO gsm_readings (id, organization_id, device_id, machine_id, imei, gsm_signal, recorded_at, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows);
    }

    public List<Map<String, Object>> findVoltageByMachineId(UUID organizationId, UUID machineId,
                                                              LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT recorded_at, voltage FROM voltage_readings " +
                "WHERE organization_id = ? AND machine_id = ? AND recorded_at BETWEEN ? AND ? " +
                "ORDER BY recorded_at ASC";
        return jdbcTemplate.queryForList(sql, organizationId, machineId, from, to);
    }

    public List<Map<String, Object>> findBatteryByMachineId(UUID organizationId, UUID machineId,
                                                              LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT recorded_at, battery_pct FROM battery_readings " +
                "WHERE organization_id = ? AND machine_id = ? AND recorded_at BETWEEN ? AND ? " +
                "ORDER BY recorded_at ASC";
        return jdbcTemplate.queryForList(sql, organizationId, machineId, from, to);
    }

    public List<Map<String, Object>> findGsmByMachineId(UUID organizationId, UUID machineId,
                                                         LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT recorded_at, gsm_signal FROM gsm_readings " +
                "WHERE organization_id = ? AND machine_id = ? AND recorded_at BETWEEN ? AND ? " +
                "ORDER BY recorded_at ASC";
        return jdbcTemplate.queryForList(sql, organizationId, machineId, from, to);
    }
}
