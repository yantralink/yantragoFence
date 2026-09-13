package com.yantrago.api.controller;

import com.yantrago.api.dto.theft.CustomerSettingsDto;
import com.yantrago.api.dto.theft.CustomerSettingsRequest;
import com.yantrago.api.service.CustomerSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<CustomerSettingsDto> getSettings() {
        return ResponseEntity.ok(customerSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<CustomerSettingsDto> updateSettings(
            @Valid @RequestBody CustomerSettingsRequest request) {
        return ResponseEntity.ok(customerSettingsService.updateSettings(request));
    }
}
