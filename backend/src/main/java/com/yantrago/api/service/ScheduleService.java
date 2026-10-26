package com.yantrago.api.service;

import com.yantrago.api.dto.schedule.ScheduleDto;
import com.yantrago.api.dto.schedule.ScheduleRequest;
import com.yantrago.api.dto.schedule.ScheduleRunDto;
import com.yantrago.api.model.Device;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineSchedule;
import com.yantrago.api.model.ScheduleRun;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineScheduleRepository;
import com.yantrago.api.repository.ScheduleRunRepository;
import com.yantrago.api.security.MachineAccessPolicy;
import com.yantrago.api.security.PermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Schedule CRUD service — one ON/OFF schedule per machine.
 *
 * Per AGENTS.md rule 7: organization_id comes from the JWT/validated machine,
 * never from the request body.
 * Per AGENTS.md rule 9: machine access is enforced via MachineAccessPolicy
 * (org membership + customer assignment), the same policy used by commands.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final MachineScheduleRepository scheduleRepository;
    private final ScheduleRunRepository scheduleRunRepository;
    private final DeviceRepository deviceRepository;
    private final MachineAccessPolicy machineAccessPolicy;
    private final PermissionEvaluator permissionEvaluator;
    private final ScheduleTimeService timeService;

    public ScheduleService(MachineScheduleRepository scheduleRepository,
                           ScheduleRunRepository scheduleRunRepository,
                           DeviceRepository deviceRepository,
                           MachineAccessPolicy machineAccessPolicy,
                           PermissionEvaluator permissionEvaluator,
                           ScheduleTimeService timeService) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleRunRepository = scheduleRunRepository;
        this.deviceRepository = deviceRepository;
        this.machineAccessPolicy = machineAccessPolicy;
        this.permissionEvaluator = permissionEvaluator;
        this.timeService = timeService;
    }

    /**
     * Returns the machine's schedule, or null when none exists (controller
     * maps this to 204 — same convention as getGeofenceForMachine).
     */
    @Transactional(readOnly = true)
    public ScheduleDto getSchedule(UUID machineId) {
        machineAccessPolicy.loadAndValidate(machineId);
        return scheduleRepository.findByMachineId(machineId)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * Upserts the machine's schedule (create or full update — one per machine).
     * Rejects creation when no device with an IMEI is bound to the machine
     * (same pre-flight rule as CommandService.createCommand).
     */
    @Transactional
    public ScheduleDto upsertSchedule(UUID machineId, ScheduleRequest request) {
        Machine machine = machineAccessPolicy.loadAndValidate(machineId);
        timeService.validateTimes(request.getOnTime(), request.getOffTime(),
                request.getDaysOfWeek(), request.getTimezone());

        Device device = deviceRepository.findByMachineId(machine.getId()).orElse(null);
        String imei = device != null ? device.getImei() : null;
        if (imei == null || imei.isBlank()) {
            throw new IllegalArgumentException("No device with IMEI bound to machine: " + machineId);
        }

        MachineSchedule schedule = scheduleRepository.findByMachineId(machineId)
                .orElseGet(MachineSchedule::new);
        if (schedule.getId() == null) {
            // organization_id is taken from the tenant-validated machine —
            // never from the request body (rules 7, 8).
            schedule.setOrganizationId(machine.getOrganizationId());
            schedule.setMachineId(machineId);
            schedule.setCreatedBy(permissionEvaluator.getCurrentUserId());
        }
        schedule.setOnTime(request.getOnTime());
        schedule.setOffTime(request.getOffTime());
        schedule.setDaysOfWeek(request.getDaysOfWeek());
        schedule.setTimezone(request.getTimezone());
        // null = keep current state on update / default true on create —
        // an explicit "enabled": null must not silently disable a schedule
        if (request.getEnabled() != null) {
            schedule.setEnabled(request.getEnabled());
        }

        try {
            schedule = scheduleRepository.save(schedule);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent PUTs raced on the one-schedule-per-machine unique index
            throw new IllegalStateException("Schedule already exists for machine: " + machineId + " — retry the update");
        }
        log.info("Upserted schedule id={} machineId={} on={} off={} days={} tz={} enabled={}",
                schedule.getId(), machineId, schedule.getOnTime(), schedule.getOffTime(),
                schedule.getDaysOfWeek(), schedule.getTimezone(), schedule.getEnabled());
        return toDto(schedule);
    }

    @Transactional
    public ScheduleDto setEnabled(UUID machineId, boolean enabled) {
        machineAccessPolicy.loadAndValidate(machineId);
        MachineSchedule schedule = scheduleRepository.findByMachineId(machineId)
                .orElseThrow(() -> new IllegalArgumentException("No schedule configured for machine: " + machineId));
        schedule.setEnabled(enabled);
        schedule = scheduleRepository.save(schedule);
        log.info("Schedule {} for machine={} enabled={}", schedule.getId(), machineId, enabled);
        return toDto(schedule);
    }

    @Transactional
    public void deleteSchedule(UUID machineId) {
        machineAccessPolicy.loadAndValidate(machineId);
        MachineSchedule schedule = scheduleRepository.findByMachineId(machineId)
                .orElseThrow(() -> new IllegalArgumentException("No schedule configured for machine: " + machineId));
        scheduleRepository.delete(schedule);
        log.info("Deleted schedule {} for machine={}", schedule.getId(), machineId);
    }

    @Transactional(readOnly = true)
    public Page<ScheduleRunDto> listRuns(UUID machineId, Pageable pageable) {
        machineAccessPolicy.loadAndValidate(machineId);
        MachineSchedule schedule = scheduleRepository.findByMachineId(machineId)
                .orElseThrow(() -> new IllegalArgumentException("No schedule configured for machine: " + machineId));
        return scheduleRunRepository
                .findByScheduleIdOrderByScheduledSlotDesc(schedule.getId(), pageable)
                .map(this::toRunDto);
    }

    private ScheduleDto toDto(MachineSchedule s) {
        Instant now = Instant.now();
        LocalDateTime nextOn = null;
        LocalDateTime nextOff = null;
        if (Boolean.TRUE.equals(s.getEnabled())) {
            nextOn = timeService.nextOnInstant(s, now)
                    .map(i -> LocalDateTime.ofInstant(i, ZoneOffset.UTC))
                    .orElse(null);
            nextOff = timeService.nextOffInstant(s, now)
                    .map(i -> LocalDateTime.ofInstant(i, ZoneOffset.UTC))
                    .orElse(null);
        }
        ScheduleRunDto lastRun = Optional.ofNullable(s.getId())
                .flatMap(id -> scheduleRunRepository.findTopByScheduleIdOrderByScheduledSlotDesc(id))
                .map(this::toRunDto)
                .orElse(null);
        return new ScheduleDto(
                s.getId(), s.getMachineId(), s.getOnTime(), s.getOffTime(),
                s.getDaysOfWeek(), s.getTimezone(), Boolean.TRUE.equals(s.getEnabled()),
                nextOn, nextOff, lastRun, s.getCreatedAt(), s.getUpdatedAt());
    }

    private ScheduleRunDto toRunDto(ScheduleRun r) {
        return new ScheduleRunDto(r.getId(), r.getScheduledSlot(), r.getCommandType(),
                r.getStatus(), r.getCommandId(), r.getAttemptCount(), r.getError(), r.getUpdatedAt());
    }
}
