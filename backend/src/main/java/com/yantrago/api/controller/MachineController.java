package com.yantrago.api.controller;

import com.yantrago.api.dto.machine.CreateMachineRequest;
import com.yantrago.api.dto.machine.MachineDto;
import com.yantrago.api.dto.machine.MachineStatusDto;
import com.yantrago.api.dto.machine.TelemetryLatestDto;
import com.yantrago.api.dto.machine.UpdateMachineRequest;
import com.yantrago.api.service.MachineService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Machine CRUD + assignment endpoints.
 *
 * Super admin: create/edit/delete machines, assign to organizations.
 * Org users: view machines in their org, assign/unassign machines to customers.
 */
@RestController
@RequestMapping("/api/v1/machines")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public ResponseEntity<Page<MachineDto>> listMachines(
            @RequestParam(required = false) UUID customerId,
            Pageable pageable) {
        if (customerId != null) {
            return ResponseEntity.ok(machineService.listMachinesByCustomer(customerId, pageable));
        }
        return ResponseEntity.ok(machineService.listMachines(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MachineDto> getMachine(@PathVariable UUID id) {
        return ResponseEntity.ok(machineService.getMachine(id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<MachineStatusDto> getMachineStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(machineService.getMachineStatus(id));
    }

    @GetMapping("/{id}/telemetry/latest")
    public ResponseEntity<TelemetryLatestDto> getLatestTelemetry(@PathVariable UUID id) {
        return ResponseEntity.ok(machineService.getLatestTelemetry(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<MachineDto> createMachine(@Valid @RequestBody CreateMachineRequest request) {
        return ResponseEntity.ok(machineService.createMachine(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<MachineDto> updateMachine(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateMachineRequest request) {
        return ResponseEntity.ok(machineService.updateMachine(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteMachine(@PathVariable UUID id) {
        machineService.deleteMachine(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign-org")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<MachineDto> assignToOrganization(@PathVariable UUID id,
                                                           @RequestBody Map<String, UUID> body) {
        UUID orgId = body.get("organizationId");
        if (orgId == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        return ResponseEntity.ok(machineService.assignMachineToOrganization(id, orgId));
    }

    @PostMapping("/{id}/assign-customer")
    public ResponseEntity<MachineDto> assignToCustomer(@PathVariable UUID id,
                                                       @RequestBody Map<String, UUID> body) {
        UUID customerId = body.get("customerId");
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        return ResponseEntity.ok(machineService.assignMachineToCustomer(id, customerId));
    }

    @PostMapping("/{id}/unassign-customer")
    public ResponseEntity<MachineDto> unassignFromCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(machineService.unassignMachineFromCustomer(id));
    }
}
