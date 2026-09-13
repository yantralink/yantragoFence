package com.yantrago.api.controller;

import com.yantrago.api.dto.geofence.GeofenceDto;
import com.yantrago.api.dto.geofence.GeofenceRequest;
import com.yantrago.api.service.GeofenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Geofence management endpoints — CRUD for machine geo-fences.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 12: validation, logging, security checks.
 */
@RestController
@RequestMapping("/api/v1/geofences")
public class GeofenceController {

    private final GeofenceService geofenceService;

    public GeofenceController(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('geofence:read') or hasRole('super_admin')")
    public ResponseEntity<List<GeofenceDto>> listGeofences() {
        return ResponseEntity.ok(geofenceService.listGeofences());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('geofence:read') or hasRole('super_admin')")
    public ResponseEntity<GeofenceDto> getGeofence(@PathVariable UUID id) {
        return ResponseEntity.ok(geofenceService.getGeofence(id));
    }

    @GetMapping("/machine/{machineId}")
    @PreAuthorize("hasAuthority('geofence:read') or hasRole('super_admin')")
    public ResponseEntity<GeofenceDto> getGeofenceForMachine(@PathVariable UUID machineId) {
        GeofenceDto dto = geofenceService.getGeofenceForMachine(machineId);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('geofence:write') or hasRole('super_admin')")
    public ResponseEntity<GeofenceDto> createGeofence(@Valid @RequestBody GeofenceRequest request) {
        return ResponseEntity.ok(geofenceService.createGeofence(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('geofence:write') or hasRole('super_admin')")
    public ResponseEntity<GeofenceDto> updateGeofence(@PathVariable UUID id,
                                                      @Valid @RequestBody GeofenceRequest request) {
        return ResponseEntity.ok(geofenceService.updateGeofence(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('geofence:delete') or hasRole('super_admin')")
    public ResponseEntity<Void> deleteGeofence(@PathVariable UUID id) {
        geofenceService.deleteGeofence(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('geofence:write') or hasRole('super_admin')")
    public ResponseEntity<GeofenceDto> activateGeofence(@PathVariable UUID id) {
        return ResponseEntity.ok(geofenceService.toggleGeofence(id, true));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('geofence:write') or hasRole('super_admin')")
    public ResponseEntity<GeofenceDto> deactivateGeofence(@PathVariable UUID id) {
        return ResponseEntity.ok(geofenceService.toggleGeofence(id, false));
    }
}
