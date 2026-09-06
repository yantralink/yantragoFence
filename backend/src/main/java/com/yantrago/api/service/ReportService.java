package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Report service — aggregates data from multiple tables for reporting.
 *
 * Supported report types:
 * - telemetry: voltage, battery, GSM readings summary
 * - location: location history summary
 * - alerts: alert summary
 * - commands: command lifecycle summary
 * - recharges: recharge history summary
 * - audit: audit log summary
 *
 * All queries filter by organization_id from OwnerContextService (tenant isolation).
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final JdbcTemplate jdbcTemplate;
    private final OwnerContextService ownerContextService;

    public ReportService(JdbcTemplate jdbcTemplate, OwnerContextService ownerContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ownerContextService = ownerContextService;
    }

    /**
     * Generates a telemetry report for the given time range.
     */
    public List<Map<String, Object>> generateTelemetryReport(LocalDateTime from, LocalDateTime to, UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();

        StringBuilder sql = new StringBuilder(
                "SELECT machine_id, " +
                "COUNT(*) as reading_count, " +
                "AVG(voltage) as avg_voltage, " +
                "MIN(voltage) as min_voltage, " +
                "MAX(voltage) as max_voltage " +
                "FROM voltage_readings " +
                "WHERE organization_id = ? AND recorded_at BETWEEN ? AND ? "
        );
        List<Object> params = new ArrayList<>();
        params.add(orgId);
        params.add(from);
        params.add(to);

        if (machineId != null) {
            sql.append("AND machine_id = ? ");
            params.add(machineId);
        }
        sql.append("GROUP BY machine_id ORDER BY machine_id");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Generates an alerts report for the given time range.
     */
    public List<Map<String, Object>> generateAlertsReport(LocalDateTime from, LocalDateTime to, UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();

        StringBuilder sql = new StringBuilder(
                "SELECT alert_type, severity, COUNT(*) as alert_count, " +
                "SUM(CASE WHEN is_acknowledged THEN 1 ELSE 0 END) as acknowledged_count " +
                "FROM alerts " +
                "WHERE organization_id = ? AND triggered_at BETWEEN ? AND ? "
        );
        List<Object> params = new ArrayList<>();
        params.add(orgId);
        params.add(from);
        params.add(to);

        if (machineId != null) {
            sql.append("AND machine_id = ? ");
            params.add(machineId);
        }
        sql.append("GROUP BY alert_type, severity ORDER BY alert_type, severity");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Generates a commands report for the given time range.
     */
    public List<Map<String, Object>> generateCommandsReport(LocalDateTime from, LocalDateTime to, UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();

        StringBuilder sql = new StringBuilder(
                "SELECT command_type, status, COUNT(*) as command_count " +
                "FROM machine_commands " +
                "WHERE organization_id = ? AND created_at BETWEEN ? AND ? "
        );
        List<Object> params = new ArrayList<>();
        params.add(orgId);
        params.add(from);
        params.add(to);

        if (machineId != null) {
            sql.append("AND machine_id = ? ");
            params.add(machineId);
        }
        sql.append("GROUP BY command_type, status ORDER BY command_type, status");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Generates a recharges report for the given time range.
     */
    public List<Map<String, Object>> generateRechargesReport(LocalDateTime from, LocalDateTime to) {
        UUID orgId = ownerContextService.getOrganizationId();

        String sql = "SELECT device_id, provider, plan_name, " +
                "SUM(amount) as total_amount, currency, COUNT(*) as recharge_count " +
                "FROM recharges " +
                "WHERE organization_id = ? AND recharged_at BETWEEN ? AND ? " +
                "GROUP BY device_id, provider, plan_name, currency " +
                "ORDER BY total_amount DESC";

        return jdbcTemplate.queryForList(sql, orgId, from, to);
    }

    /**
     * Generates an audit log report for the given time range.
     */
    public List<Map<String, Object>> generateAuditReport(LocalDateTime from, LocalDateTime to) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();

        if (orgId == null) {
            // Super_admin: see all audit logs
            String sql = "SELECT action, resource_type, COUNT(*) as action_count " +
                    "FROM audit_logs WHERE created_at BETWEEN ? AND ? " +
                    "GROUP BY action, resource_type ORDER BY action_count DESC";
            return jdbcTemplate.queryForList(sql, from, to);
        }

        String sql = "SELECT action, resource_type, COUNT(*) as action_count " +
                "FROM audit_logs WHERE organization_id = ? AND created_at BETWEEN ? AND ? " +
                "GROUP BY action, resource_type ORDER BY action_count DESC";
        return jdbcTemplate.queryForList(sql, orgId, from, to);
    }

    /**
     * Generates a location report for the given time range.
     */
    public List<Map<String, Object>> generateLocationReport(LocalDateTime from, LocalDateTime to, UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();

        StringBuilder sql = new StringBuilder(
                "SELECT machine_id, COUNT(*) as location_count, " +
                "MIN(recorded_at) as first_seen, MAX(recorded_at) as last_seen " +
                "FROM location_history " +
                "WHERE organization_id = ? AND recorded_at BETWEEN ? AND ? "
        );
        List<Object> params = new ArrayList<>();
        params.add(orgId);
        params.add(from);
        params.add(to);

        if (machineId != null) {
            sql.append("AND machine_id = ? ");
            params.add(machineId);
        }
        sql.append("GROUP BY machine_id ORDER BY machine_id");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Generates a report based on the report type.
     */
    public Map<String, Object> generateReport(String reportType, LocalDateTime from, LocalDateTime to, UUID machineId) {
        log.info("Generating report type={} from={} to={} machineId={}", reportType, from, to, machineId);

        Map<String, Object> report = new HashMap<>();
        report.put("reportType", reportType);
        report.put("from", from.toString());
        report.put("to", to.toString());
        report.put("generatedAt", LocalDateTime.now().toString());

        List<Map<String, Object>> data = switch (reportType) {
            case "telemetry" -> generateTelemetryReport(from, to, machineId);
            case "location" -> generateLocationReport(from, to, machineId);
            case "alerts" -> generateAlertsReport(from, to, machineId);
            case "commands" -> generateCommandsReport(from, to, machineId);
            case "recharges" -> generateRechargesReport(from, to);
            case "audit" -> generateAuditReport(from, to);
            default -> throw new IllegalArgumentException("Unknown report type: " + reportType);
        };

        report.put("data", data);
        report.put("rowCount", data.size());
        return report;
    }
}
