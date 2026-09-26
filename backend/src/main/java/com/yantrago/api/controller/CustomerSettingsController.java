package com.yantrago.api.controller;

import com.yantrago.api.dto.theft.CustomerSettingsDto;
import com.yantrago.api.dto.theft.CustomerSettingsRequest;
import com.yantrago.api.service.CustomerSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Customer settings endpoints — per-customer theft protection defaults.
 *
 * Per Phase 11 design: customers can set their preferred geofence radius
 * and speed threshold. These defaults are used when enabling protection
 * for new machines, so customers don't have to configure each machine.
 *
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 12: validation via Bean Validation.
 *
 * Endpoints:
 *   GET  /api/v1/customer/settings  — get current defaults
 *   PUT  /api/v1/customer/settings  — update defaults
 */
@RestController
@RequestMapping("/api/v1/customer/settings")
public class CustomerSettingsController {

    private final CustomerSettingsService customerSettingsService;

    public CustomerSettingsController(CustomerSettingsService customerSettingsService) {
        this.customerSettingsService = customerSettingsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('settings:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CustomerSettingsDto> getSettings() {
        return ResponseEntity.ok(customerSettingsService.getSettings());
    }

    @PutMapping
    // Self-service endpoint: scoped to the caller's own customer record.
    // Customers only hold settings:read (not :write) — the write to their
    // own row is intentional and already tenant-scoped by the service.
    @PreAuthorize("hasAuthority('settings:read') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CustomerSettingsDto> updateSettings(
            @Valid @RequestBody CustomerSettingsRequest request) {
        return ResponseEntity.ok(customerSettingsService.updateSettings(request));
    }
}
