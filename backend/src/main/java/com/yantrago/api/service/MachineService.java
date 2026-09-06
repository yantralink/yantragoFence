package com.yantrago.api.service;

import com.yantrago.api.dto.machine.CreateMachineRequest;
import com.yantrago.api.dto.machine.MachineDto;
import com.yantrago.api.dto.machine.MachineStatusDto;
import com.yantrago.api.dto.machine.UpdateMachineRequest;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Machine CRUD service.
 * All queries filter by organization_id from OwnerContextService.
 * Machine assignment to customers is validated against the same tenant.
 */
@Service
public class MachineService {

    private static final Logger log = LoggerFactory.getLogger(MachineService.class);

    private final MachineRepository machineRepository;
    private final CustomerRepository customerRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public MachineService(MachineRepository machineRepository,
                           CustomerRepository customerRepository,
                           OwnerContextService ownerContextService,
                           TenantGuard tenantGuard) {
        this.machineRepository = machineRepository;
        this.customerRepository = customerRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public Page<MachineDto> listMachines(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return machineRepository.findByOrganizationId(orgId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<MachineDto> listMachinesByCustomer(UUID customerId, Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return machineRepository.findByOrganizationIdAndCustomerId(orgId, customerId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public MachineDto getMachine(UUID id) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        return toDto(machine);
    }

    @Transactional(readOnly = true)
    public MachineStatusDto getMachineStatus(UUID id) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        return new MachineStatusDto(machine.getId(), machine.getStatus(), machine.getIsOnline(), machine.getLastSeenAt());
    }

    @Transactional
    public MachineDto createMachine(CreateMachineRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();

        // Validate serial number uniqueness within tenant
        if (request.getSerialNumber() != null && !request.getSerialNumber().isBlank()) {
            machineRepository.findByOrganizationIdAndSerialNumber(orgId, request.getSerialNumber())
                    .ifPresent(m -> {
                        throw new IllegalArgumentException("Machine with this serial number already exists in this organization");
                    });
        }

        // Validate customer belongs to same tenant
        if (request.getCustomerId() != null) {
            customerRepository.findById(request.getCustomerId())
                    .ifPresentOrElse(
                            c -> tenantGuard.validateTenantAccess(c.getOrganizationId()),
                            () -> { throw new IllegalArgumentException("Customer not found: " + request.getCustomerId()); }
                    );
        }

        Machine machine = new Machine();
        machine.setOrganizationId(orgId);
        machine.setCustomerId(request.getCustomerId());
        machine.setName(request.getName());
        machine.setSerialNumber(request.getSerialNumber());
        machine.setModel(request.getModel());
        machine.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        machine.setIsOnline(false);

        machine = machineRepository.save(machine);
        log.info("Created machine id={} orgId={}", machine.getId(), orgId);
        return toDto(machine);
    }

    @Transactional
    public MachineDto updateMachine(UUID id, UpdateMachineRequest request) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        if (request.getCustomerId() != null) {
            customerRepository.findById(request.getCustomerId())
                    .ifPresentOrElse(
                            c -> tenantGuard.validateTenantAccess(c.getOrganizationId()),
                            () -> { throw new IllegalArgumentException("Customer not found: " + request.getCustomerId()); }
                    );
            machine.setCustomerId(request.getCustomerId());
        }
        if (request.getName() != null) machine.setName(request.getName());
        if (request.getSerialNumber() != null) machine.setSerialNumber(request.getSerialNumber());
        if (request.getModel() != null) machine.setModel(request.getModel());
        if (request.getStatus() != null) machine.setStatus(request.getStatus());

        machine = machineRepository.save(machine);
        log.info("Updated machine id={}", machine.getId());
        return toDto(machine);
    }

    @Transactional
    public void deleteMachine(UUID id) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());
        machineRepository.delete(machine);
        log.info("Deleted machine id={}", id);
    }

    private MachineDto toDto(Machine m) {
        return new MachineDto(
                m.getId(),
                m.getOrganizationId(),
                m.getCustomerId(),
                m.getName(),
                m.getSerialNumber(),
                m.getModel(),
                m.getStatus(),
                m.getIsOnline(),
                m.getLastSeenAt(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
