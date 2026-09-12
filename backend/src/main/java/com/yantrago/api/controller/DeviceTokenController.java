package com.yantrago.api.controller;

import com.yantrago.api.dto.push.DeviceTokenDto;
import com.yantrago.api.dto.push.DeviceTokenRequest;
import com.yantrago.api.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Device token management endpoints — register/unregister push tokens.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 12: validation, logging, security checks.
 *
 * Per notification plan Phase 5: authenticated lifecycle APIs.
 */
@RestController
@RequestMapping("/api/v1/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    /**
     * Registers (or re-activates) a device push token for the current user.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('push_token:write') or hasRole('super_admin')")
    public ResponseEntity<DeviceTokenDto> registerToken(@Valid @RequestBody DeviceTokenRequest request) {
        return ResponseEntity.ok(deviceTokenService.registerToken(request));
    }

    /**
     * Unregisters a specific device token (e.g. user removes a device).
     */
    @DeleteMapping("/{tokenId}")
    @PreAuthorize("hasAuthority('push_token:write') or hasRole('super_admin')")
    public ResponseEntity<Void> unregisterToken(@PathVariable UUID tokenId) {
        deviceTokenService.unregisterToken(tokenId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivates all device tokens for the current user (on logout).
     */
    @PostMapping("/deactivate-all")
    @PreAuthorize("hasAuthority('push_token:write') or hasRole('super_admin')")
    public ResponseEntity<Void> deactivateAll() {
        deviceTokenService.deactivateAllForUser();
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all active device tokens for the current user.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('push_token:read') or hasRole('super_admin')")
    public ResponseEntity<List<DeviceTokenDto>> listMyTokens() {
        return ResponseEntity.ok(deviceTokenService.listMyTokens());
    }
}
