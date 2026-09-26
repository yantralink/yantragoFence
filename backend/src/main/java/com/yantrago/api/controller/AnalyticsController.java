package com.yantrago.api.controller;

import com.yantrago.api.dto.analytics.FaultIntervalDto;
import com.yantrago.api.dto.analytics.SessionsResponse;
import com.yantrago.api.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Analytics endpoints — historical views for the mobile Analytics tab.
 *
 * Endpoints (all default to last 24h when from/to omitted):
 *   GET /api/v1/machines/{machineId}/faults   — FENCE_FAULT intervals
 *   GET /api/v1/machines/{machineId}/sessions — ignition sessions + command markers
 *
 * Per AGENTS.md rule 9: machine:read permission or SUPER_ADMIN.
 * Per rule 7: tenant isolation via JWT-derived organizationId.
 */
@RestController
@RequestMapping("/api/v1/machines")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{machineId}/faults")
    @PreAuthorize("hasAuthority('machine:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<FaultIntervalDto>> getFaultIntervals(
            @PathVariable UUID machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        if (to == null) to = LocalDateTime.now();
        if (from == null) from = to.minusHours(24);

        return ResponseEntity.ok(analyticsService.getFaultIntervals(machineId, from, to));
    }

    @GetMapping("/{machineId}/sessions")
    @PreAuthorize("hasAuthority('machine:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<SessionsResponse> getSessions(
            @PathVariable UUID machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        if (to == null) to = LocalDateTime.now();
        if (from == null) from = to.minusHours(24);

        return ResponseEntity.ok(analyticsService.getSessions(machineId, from, to));
    }
}
