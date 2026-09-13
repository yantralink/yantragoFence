package com.yantrago.api.controller;

import com.yantrago.api.dto.theft.TheftProtectionStatusDto;
import com.yantrago.api.service.TheftProtectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Theft Protection toggle endpoints — customer self-service enable/disable.
 *
 * Per Phase 11 design: customers enable theft protection after installing
 * their machine on the farm. This prevents false alerts during transport
 * from shop to farm.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 *
 * Endpoints:
 *   GET    /api/v1/machines/{machineId}/theft-protection         — get status
 *   POST   /api/v1/machines/{machineId}/theft-protection/enable  — enable
 *   POST   /api/v1/machines/{machineId}/theft-protection/disable — disable
 */
@RestController
@RequestMapping("/api/v1/machines/{machineId}/theft-protection")
public class TheftProtectionController {

    private final TheftProtectionService theftProtectionService;

    public TheftProtectionController(TheftProtectionService theftProtectionService) {
        this.theftProtectionService = theftProtectionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('geofence:read') or hasRole('super_admin')")
    public ResponseEntity<TheftProtectionStatusDto> getStatus(@PathVariable UUID machineId) {
        return ResponseEntity.ok(theftProtectionService.getStatus(machineId));
    }

    @PostMapping("/enable")
    @PreAuthorize("hasAuthority('geofence:write') or hasRole('super_admin')")
    public ResponseEntity<TheftProtectionStatusDto> enable(@PathVariable UUID machineId) {
        return ResponseEntity.ok(theftProtectionService.enable(machineId));
    }

    @PostMapping("/disable")
    @PreAuthorize("hasAuthority('geofence:write') or hasRole('super_admin')")
    public ResponseEntity<TheftProtectionStatusDto> disable(@PathVariable UUID machineId) {
        return ResponseEntity.ok(theftProtectionService.disable(machineId));
    }
}
