package com.yantrago.api.controller;

import com.yantrago.api.dto.telemetry.TelemetryDto;
import com.yantrago.api.service.TelemetryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Telemetry endpoints — query voltage, battery, GSM readings for a machine.
 *
 * GET /api/v1/telemetry/{machineId}?from=2026-09-01T00:00:00&to=2026-09-05T00:00:00
 */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GetMapping("/{machineId}")
    public ResponseEntity<TelemetryDto> getTelemetry(
            @PathVariable UUID machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        // Default to last 24 hours if not specified
        if (to == null) to = LocalDateTime.now();
        if (from == null) from = to.minusHours(24);

        return ResponseEntity.ok(telemetryService.getTelemetry(machineId, from, to));
    }
}
