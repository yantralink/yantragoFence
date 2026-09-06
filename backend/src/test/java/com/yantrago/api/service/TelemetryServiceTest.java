package com.yantrago.api.service;

import com.yantrago.api.dto.telemetry.TelemetryDto;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.repository.TelemetryRepository;
import com.yantrago.api.security.TenantGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TelemetryService.
 *
 * Per AGENTS.md rule 7: all queries filter by organization_id from OwnerContextService.
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
class TelemetryServiceTest {

    private TelemetryRepository telemetryRepository;
    private MachineRepository machineRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private TelemetryService telemetryService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        telemetryRepository = mock(TelemetryRepository.class);
        machineRepository = mock(MachineRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        telemetryService = new TelemetryService(
                telemetryRepository, machineRepository, ownerContextService, tenantGuard
        );
    }

    @Test
    @DisplayName("getTelemetry should validate machine belongs to tenant")
    void getTelemetry_shouldValidateTenantAccess() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        Machine machine = new Machine();
        machine.setId(machineId);
        machine.setOrganizationId(orgId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));
        when(telemetryRepository.findVoltageByMachineId(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of());
        when(telemetryRepository.findBatteryByMachineId(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of());
        when(telemetryRepository.findGsmByMachineId(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of());

        LocalDateTime from = LocalDateTime.now().minusHours(1);
        LocalDateTime to = LocalDateTime.now();
        telemetryService.getTelemetry(machineId, from, to);

        verify(tenantGuard).validateTenantAccess(orgId);
    }

    @Test
    @DisplayName("getTelemetry should throw when machine not found")
    void getTelemetry_shouldThrowWhenMachineNotFound() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> telemetryService.getTelemetry(machineId, LocalDateTime.now().minusHours(1), LocalDateTime.now()));
    }

    @Test
    @DisplayName("getTelemetry should return voltage, battery, and GSM readings")
    void getTelemetry_shouldReturnAllReadings() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        Machine machine = new Machine();
        machine.setId(machineId);
        machine.setOrganizationId(orgId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        Map<String, Object> voltageRow = new HashMap<>();
        voltageRow.put("recorded_at", LocalDateTime.now());
        voltageRow.put("voltage", 12.5);

        Map<String, Object> batteryRow = new HashMap<>();
        batteryRow.put("recorded_at", LocalDateTime.now());
        batteryRow.put("battery_pct", 85.0);

        Map<String, Object> gsmRow = new HashMap<>();
        gsmRow.put("recorded_at", LocalDateTime.now());
        gsmRow.put("gsm_signal", 20);

        when(telemetryRepository.findVoltageByMachineId(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of(voltageRow));
        when(telemetryRepository.findBatteryByMachineId(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of(batteryRow));
        when(telemetryRepository.findGsmByMachineId(eq(orgId), eq(machineId), any(), any()))
                .thenReturn(List.of(gsmRow));

        LocalDateTime from = LocalDateTime.now().minusHours(1);
        LocalDateTime to = LocalDateTime.now();
        TelemetryDto result = telemetryService.getTelemetry(machineId, from, to);

        assertEquals(1, result.getVoltageReadings().size());
        assertEquals(12.5, result.getVoltageReadings().get(0).getVoltage());
        assertEquals(1, result.getBatteryReadings().size());
        assertEquals(85.0, result.getBatteryReadings().get(0).getBatteryPct());
        assertEquals(1, result.getGsmReadings().size());
        assertEquals(20, result.getGsmReadings().get(0).getGsmSignal());
    }

    @Test
    @DisplayName("storeVoltageReadings should delegate to repository batch insert")
    void storeVoltageReadings_shouldDelegateToRepository() {
        List<Object[]> rows = Collections.singletonList(
                new Object[]{UUID.randomUUID(), orgId, UUID.randomUUID(), machineId, "IMEI123", 12.5, LocalDateTime.now(), LocalDateTime.now()}
        );
        telemetryService.storeVoltageReadings(rows);
        verify(telemetryRepository).batchInsertVoltageReadings(rows);
    }

    @Test
    @DisplayName("storeBatteryReadings should delegate to repository batch insert")
    void storeBatteryReadings_shouldDelegateToRepository() {
        List<Object[]> rows = Collections.singletonList(
                new Object[]{UUID.randomUUID(), orgId, UUID.randomUUID(), machineId, "IMEI123", 85.0, LocalDateTime.now(), LocalDateTime.now()}
        );
        telemetryService.storeBatteryReadings(rows);
        verify(telemetryRepository).batchInsertBatteryReadings(rows);
    }

    @Test
    @DisplayName("storeGsmReadings should delegate to repository batch insert")
    void storeGsmReadings_shouldDelegateToRepository() {
        List<Object[]> rows = Collections.singletonList(
                new Object[]{UUID.randomUUID(), orgId, UUID.randomUUID(), machineId, "IMEI123", 20, LocalDateTime.now(), LocalDateTime.now()}
        );
        telemetryService.storeGsmReadings(rows);
        verify(telemetryRepository).batchInsertGsmReadings(rows);
    }

    @Test
    @DisplayName("getTelemetry should throw when tenant guard denies access")
    void getTelemetry_shouldThrowWhenTenantGuardDenies() {
        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
        Machine machine = new Machine();
        machine.setId(machineId);
        machine.setOrganizationId(UUID.randomUUID()); // Different org
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));
        doThrow(new SecurityException("Cross-tenant access denied"))
                .when(tenantGuard).validateTenantAccess(machine.getOrganizationId());

        assertThrows(SecurityException.class,
                () -> telemetryService.getTelemetry(machineId, LocalDateTime.now().minusHours(1), LocalDateTime.now()));
    }
}
