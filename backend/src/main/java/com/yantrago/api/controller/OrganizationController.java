package com.yantrago.api.controller;

import com.yantrago.api.dto.organization.CreateOrganizationRequest;
import com.yantrago.api.dto.organization.OrganizationDto;
import com.yantrago.api.dto.organization.UpdateOrganizationRequest;
import com.yantrago.api.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Organization CRUD + lifecycle endpoints.
 * Creating/updating/deleting/activating/deactivating organizations requires super_admin role.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<Page<OrganizationDto>> listOrganizations(Pageable pageable) {
        return ResponseEntity.ok(organizationService.listOrganizations(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationDto> getOrganization(@PathVariable UUID id) {
        return ResponseEntity.ok(organizationService.getOrganization(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationDto> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.createOrganization(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationDto> updateOrganization(@PathVariable UUID id,
                                                              @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateOrganization(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteOrganization(@PathVariable UUID id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationDto> activateOrganization(@PathVariable UUID id) {
        return ResponseEntity.ok(organizationService.activateOrganization(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationDto> deactivateOrganization(@PathVariable UUID id) {
        return ResponseEntity.ok(organizationService.deactivateOrganization(id));
    }
}
