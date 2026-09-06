package com.yantrago.api.controller;

import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.service.LocationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Location endpoints — query current and historical GPS data for a machine.
 *
 * GET /api/v1/locations/{machineId} — current location
 * GET /api/v1/locations/{machineId}/history?from=...&to=... — location history
 */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/{machineId}")
    public ResponseEntity<LocationDto> getCurrentLocation(@PathVariable UUID machineId) {
        LocationDto location = locationService.getCurrentLocation(machineId);
        if (location == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(location);
    }

    @GetMapping("/{machineId}/history")
    public ResponseEntity<List<LocationDto>> getLocationHistory(
            @PathVariable UUID machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        // Default to last 24 hours if not specified
        if (to == null) to = LocalDateTime.now();
        if (from == null) from = to.minusHours(24);

        return ResponseEntity.ok(locationService.getLocationHistory(machineId, from, to));
    }
}
