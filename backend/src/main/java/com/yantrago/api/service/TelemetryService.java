package com.yantrago.api.service;

import com.yantrago.api.dto.telemetry.BatteryDto;
import com.yantrago.api.dto.telemetry.GsmDto;
import com.yantrago.api.dto.telemetry.TelemetryDto;
import com.yantrago.api.dto.telemetry.VoltageDto;
import com.yantrago.api.repository.TelemetryRepository;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.MachineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Telemetry service — stores and queries voltage, battery, and GSM readings.
 * Uses TelemetryRepository (JdbcTemplate) for high-volume partitioned time-series tables.
 *
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryRepository telemetryRepository;
    private final MachineRepository machineRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public TelemetryService(TelemetryRepository telemetryRepository,
                            MachineRepository machineRepository,
                            OwnerContextService ownerContextService,
                            TenantGuard tenantGuard) {
        this.telemetryRepository = telemetryRepository;
        this.machineRepository = machineRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    /**
     * Queries telemetry for a machine within a time range.
     */
    @Transactional(readOnly = true)
    public TelemetryDto getTelemetry(UUID machineId, LocalDateTime from, LocalDateTime to) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Validate machine belongs to tenant
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        List<Map<String, Object>> voltageRows = telemetryRepository.findVoltageByMachineId(orgId, machineId, from, to);
        List<Map<String, Object>> batteryRows = telemetryRepository.findBatteryByMachineId(orgId, machineId, from, to);
        List<Map<String, Object>> gsmRows = telemetryRepository.findGsmByMachineId(orgId, machineId, from, to);

        List<VoltageDto> voltage = voltageRows.stream()
                .map(r -> new VoltageDto(
                        (LocalDateTime) r.get("recorded_at"),
                        ((Number) r.get("voltage")).doubleValue()))
                .collect(Collectors.toList());

        List<BatteryDto> battery = batteryRows.stream()
                .map(r -> new BatteryDto(
                        (LocalDateTime) r.get("recorded_at"),
                        ((Number) r.get("battery_pct")).doubleValue()))
                .collect(Collectors.toList());

        List<GsmDto> gsm = gsmRows.stream()
                .map(r -> new GsmDto(
                        (LocalDateTime) r.get("recorded_at"),
                        ((Number) r.get("gsm_signal")).intValue()))
                .collect(Collectors.toList());

        return new TelemetryDto(machineId.toString(), voltage, battery, gsm, from, to);
    }

    /**
     * Batch insert voltage readings (called by RabbitMQ consumer from gateway).
     */
    @Transactional
    public void storeVoltageReadings(List<Object[]> rows) {
        telemetryRepository.batchInsertVoltageReadings(rows);
        log.debug("Stored {} voltage readings", rows.size());
    }

    /**
     * Batch insert battery readings (called by RabbitMQ consumer from gateway).
     */
    @Transactional
    public void storeBatteryReadings(List<Object[]> rows) {
        telemetryRepository.batchInsertBatteryReadings(rows);
        log.debug("Stored {} battery readings", rows.size());
    }

    /**
     * Batch insert GSM readings (called by RabbitMQ consumer from gateway).
     */
    @Transactional
    public void storeGsmReadings(List<Object[]> rows) {
        telemetryRepository.batchInsertGsmReadings(rows);
        log.debug("Stored {} GSM readings", rows.size());
    }
}
