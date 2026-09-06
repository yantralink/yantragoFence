package com.yantrago.api.controller;

import com.yantrago.api.dto.machine.CreateMachineRequest;
import com.yantrago.api.dto.machine.MachineDto;
import com.yantrago.api.dto.machine.MachineStatusDto;
import com.yantrago.api.dto.machine.UpdateMachineRequest;
import com.yantrago.api.service.MachineService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Machine CRUD endpoints.
 * Supports filtering by customer via query param: GET /api/v1/machines?customerId={uuid}
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

    @PostMapping
    public ResponseEntity<MachineDto> createMachine(@Valid @RequestBody CreateMachineRequest request) {
        return ResponseEntity.ok(machineService.createMachine(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MachineDto> updateMachine(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateMachineRequest request) {
        return ResponseEntity.ok(machineService.updateMachine(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMachine(@PathVariable UUID id) {
        machineService.deleteMachine(id);
        return ResponseEntity.noContent().build();
    }
}
