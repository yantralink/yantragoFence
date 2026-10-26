package com.yantrago.api.controller;

import com.yantrago.api.dto.schedule.ScheduleDto;
import com.yantrago.api.dto.schedule.ScheduleEnabledRequest;
import com.yantrago.api.dto.schedule.ScheduleRequest;
import com.yantrago.api.dto.schedule.ScheduleRunDto;
import com.yantrago.api.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Automatic fence ON/OFF schedule endpoints — one schedule per machine.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: schedule:read / schedule:write permissions required.
 *
 * GET    /api/v1/machines/{machineId}/schedule          → 200 ScheduleDto | 204 none
 * PUT    /api/v1/machines/{machineId}/schedule          → upsert
 * PATCH  /api/v1/machines/{machineId}/schedule/enabled  → { "enabled": bool }
 * DELETE /api/v1/machines/{machineId}/schedule          → delete
 * GET    /api/v1/machines/{machineId}/schedule/runs     → paged run history
 */
@RestController
@RequestMapping("/api/v1/machines/{machineId}/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('schedule:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ScheduleDto> getSchedule(@PathVariable UUID machineId) {
        ScheduleDto dto = scheduleService.getSchedule(machineId);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.noContent().build();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('schedule:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ScheduleDto> upsertSchedule(@PathVariable UUID machineId,
                                                      @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.upsertSchedule(machineId, request));
    }

    @PatchMapping("/enabled")
    @PreAuthorize("hasAuthority('schedule:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ScheduleDto> setEnabled(@PathVariable UUID machineId,
                                                  @Valid @RequestBody ScheduleEnabledRequest request) {
        return ResponseEntity.ok(scheduleService.setEnabled(machineId, request.getEnabled()));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('schedule:write') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID machineId) {
        scheduleService.deleteSchedule(machineId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('schedule:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<ScheduleRunDto>> listRuns(@PathVariable UUID machineId,
                                                         Pageable pageable) {
        return ResponseEntity.ok(scheduleService.listRuns(machineId, pageable));
    }
}
