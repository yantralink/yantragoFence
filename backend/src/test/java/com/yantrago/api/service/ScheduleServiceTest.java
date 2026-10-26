package com.yantrago.api.service;

import com.yantrago.api.dto.schedule.ScheduleDto;
import com.yantrago.api.dto.schedule.ScheduleRequest;
import com.yantrago.api.model.Device;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineSchedule;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineScheduleRepository;
import com.yantrago.api.repository.ScheduleRunRepository;
import com.yantrago.api.security.MachineAccessPolicy;
import com.yantrago.api.security.PermissionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScheduleService — schedule CRUD with tenant isolation,
 * validation, and device-binding checks.
 *
 * Per AGENTS.md rule 7: organization_id from the validated machine, never
 * from the request body.
 */
class ScheduleServiceTest {

    private MachineScheduleRepository scheduleRepository;
    private ScheduleRunRepository scheduleRunRepository;
    private DeviceRepository deviceRepository;
    private MachineAccessPolicy machineAccessPolicy;
    private PermissionEvaluator permissionEvaluator;
    private ScheduleService scheduleService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(MachineScheduleRepository.class);
        scheduleRunRepository = mock(ScheduleRunRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        machineAccessPolicy = mock(MachineAccessPolicy.class);
        permissionEvaluator = mock(PermissionEvaluator.class);

        scheduleService = new ScheduleService(
                scheduleRepository, scheduleRunRepository, deviceRepository,
                machineAccessPolicy, permissionEvaluator, new ScheduleTimeService());
    }

    private Machine machine() {
        Machine m = new Machine();
        m.setId(machineId);
        m.setOrganizationId(orgId);
        m.setSerialNumber("IMEI123456");
        return m;
    }

    private Device boundDevice() {
        Device d = new Device();
        d.setId(UUID.randomUUID());
        d.setOrganizationId(orgId);
        d.setImei("IMEI123456");
        d.setMachineId(machineId);
        return d;
    }

    private ScheduleRequest validRequest() {
        ScheduleRequest r = new ScheduleRequest();
        r.setOnTime(LocalTime.of(18, 0));
        r.setOffTime(LocalTime.of(7, 0));
        r.setDaysOfWeek(127);
        r.setTimezone("Asia/Kolkata");
        r.setEnabled(true);
        return r;
    }

    private void stubAccessAndDevice() {
        when(machineAccessPolicy.loadAndValidate(machineId)).thenReturn(machine());
        when(deviceRepository.findByMachineId(machineId)).thenReturn(Optional.of(boundDevice()));
    }

    @Test
    @DisplayName("upsert should create a new schedule when none exists")
    void upsert_createsNew() {
        stubAccessAndDevice();
        when(permissionEvaluator.getCurrentUserId()).thenReturn(userId);
        when(scheduleRepository.findByMachineId(machineId)).thenReturn(Optional.empty());
        when(scheduleRepository.save(any())).thenAnswer(inv -> {
            MachineSchedule s = inv.getArgument(0);
            if (s.getId() == null) s.setId(scheduleId);
            return s;
        });
        when(scheduleRunRepository.findTopByScheduleIdOrderByScheduledSlotDesc(any()))
                .thenReturn(Optional.empty());

        ScheduleDto dto = scheduleService.upsertSchedule(machineId, validRequest());

        ArgumentCaptor<MachineSchedule> captor = ArgumentCaptor.forClass(MachineSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        MachineSchedule saved = captor.getValue();
        assertEquals(orgId, saved.getOrganizationId());   // from validated machine, not request
        assertEquals(machineId, saved.getMachineId());
        assertEquals(userId, saved.getCreatedBy());
        assertEquals(LocalTime.of(18, 0), saved.getOnTime());
        assertTrue(dto.enabled());
        // nextOn/nextOff are server-computed for enabled schedules
        assertNotNull(dto.nextOnAt());
        assertNotNull(dto.nextOffAt());
    }

    @Test
    @DisplayName("upsert should update the existing schedule (one per machine)")
    void upsert_updatesExisting() {
        stubAccessAndDevice();
        MachineSchedule existing = new MachineSchedule();
        existing.setId(scheduleId);
        existing.setOrganizationId(orgId);
        existing.setMachineId(machineId);
        existing.setCreatedBy(UUID.randomUUID());
        existing.setOnTime(LocalTime.of(6, 0));
        existing.setOffTime(LocalTime.of(19, 0));
        when(scheduleRepository.findByMachineId(machineId)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRunRepository.findTopByScheduleIdOrderByScheduledSlotDesc(any()))
                .thenReturn(Optional.empty());

        ScheduleDto dto = scheduleService.upsertSchedule(machineId, validRequest());

        verify(scheduleRepository).save(existing);
        assertEquals(LocalTime.of(18, 0), existing.getOnTime());
        assertEquals(LocalTime.of(7, 0), existing.getOffTime());
        // createdBy must not be overwritten on update
        assertNotNull(existing.getCreatedBy());
        assertNotEquals(userId, existing.getCreatedBy());
    }

    @Test
    @DisplayName("upsert should reject onTime == offTime")
    void upsert_rejectsEqualTimes() {
        stubAccessAndDevice();
        ScheduleRequest r = validRequest();
        r.setOffTime(LocalTime.of(18, 0));

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.upsertSchedule(machineId, r));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("upsert should reject invalid timezone")
    void upsert_rejectsBadTimezone() {
        stubAccessAndDevice();
        ScheduleRequest r = validRequest();
        r.setTimezone("Not/AZone");

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.upsertSchedule(machineId, r));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("upsert should reject when no device with IMEI is bound to the machine")
    void upsert_rejectsNoDevice() {
        when(machineAccessPolicy.loadAndValidate(machineId)).thenReturn(machine());
        when(deviceRepository.findByMachineId(machineId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.upsertSchedule(machineId, validRequest()));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("upsert should enforce machine access policy")
    void upsert_enforcesAccessPolicy() {
        when(machineAccessPolicy.loadAndValidate(machineId))
                .thenThrow(new SecurityException("Access denied: this machine is not assigned to you"));

        assertThrows(SecurityException.class,
                () -> scheduleService.upsertSchedule(machineId, validRequest()));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("getSchedule returns null when none configured")
    void getSchedule_nullWhenAbsent() {
        when(machineAccessPolicy.loadAndValidate(machineId)).thenReturn(machine());
        when(scheduleRepository.findByMachineId(machineId)).thenReturn(Optional.empty());

        assertNull(scheduleService.getSchedule(machineId));
    }

    @Test
    @DisplayName("setEnabled toggles the flag")
    void setEnabled_toggles() {
        when(machineAccessPolicy.loadAndValidate(machineId)).thenReturn(machine());
        MachineSchedule existing = new MachineSchedule();
        existing.setId(scheduleId);
        existing.setOrganizationId(orgId);
        existing.setMachineId(machineId);
        existing.setOnTime(LocalTime.of(18, 0));
        existing.setOffTime(LocalTime.of(7, 0));
        existing.setDaysOfWeek(127);
        existing.setTimezone("Asia/Kolkata");
        existing.setEnabled(true);
        when(scheduleRepository.findByMachineId(machineId)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRunRepository.findTopByScheduleIdOrderByScheduledSlotDesc(any()))
                .thenReturn(Optional.empty());

        ScheduleDto dto = scheduleService.setEnabled(machineId, false);

        assertFalse(dto.enabled());
        // Disabled schedule must not advertise next runs
        assertNull(dto.nextOnAt());
        assertNull(dto.nextOffAt());
    }

    @Test
    @DisplayName("setEnabled throws when no schedule exists")
    void setEnabled_noSchedule() {
        when(machineAccessPolicy.loadAndValidate(machineId)).thenReturn(machine());
        when(scheduleRepository.findByMachineId(machineId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.setEnabled(machineId, false));
    }

    @Test
    @DisplayName("deleteSchedule removes the schedule")
    void deleteSchedule_removes() {
        when(machineAccessPolicy.loadAndValidate(machineId)).thenReturn(machine());
        MachineSchedule existing = new MachineSchedule();
        existing.setId(scheduleId);
        existing.setMachineId(machineId);
        when(scheduleRepository.findByMachineId(machineId)).thenReturn(Optional.of(existing));

        scheduleService.deleteSchedule(machineId);

        verify(scheduleRepository).delete(existing);
    }
}
