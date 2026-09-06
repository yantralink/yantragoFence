package com.yantrago.api.service;

import com.yantrago.api.dto.machine.CreateMachineRequest;
import com.yantrago.api.dto.machine.MachineDto;
import com.yantrago.api.dto.machine.MachineStatusDto;
import com.yantrago.api.dto.machine.UpdateMachineRequest;
import com.yantrago.api.model.Customer;
import com.yantrago.api.model.Device;
import com.yantrago.api.model.Machine;
import com.yantrago.api.model.MachineAssignment;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineAssignmentRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Machine CRUD service.
 *
 * Super admin: can create/edit/delete machines, assign machines to organizations.
 * Org users: can view machines in their org, assign/unassign machines to customers.
 *
 * Machine ID (human-readable, e.g. YG000001) is auto-generated on creation.
 * Device record (IMEI, SIM, protocol) is created alongside the machine.
 */
@Service
public class MachineService {

    private static final Logger log = LoggerFactory.getLogger(MachineService.class);

    private final MachineRepository machineRepository;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;
    private final MachineAssignmentRepository machineAssignmentRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public MachineService(MachineRepository machineRepository,
                          DeviceRepository deviceRepository,
                          CustomerRepository customerRepository,
                          MachineAssignmentRepository machineAssignmentRepository,
                          OwnerContextService ownerContextService,
                          TenantGuard tenantGuard) {
        this.machineRepository = machineRepository;
        this.deviceRepository = deviceRepository;
        this.customerRepository = customerRepository;
        this.machineAssignmentRepository = machineAssignmentRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public Page<MachineDto> listMachines(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationIdOrNull();
        if (orgId == null) {
            // Super admin: list all machines
            return machineRepository.findAll(pageable).map(this::toDto);
        }
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
        // Validate IMEI uniqueness across platform
        if (request.getImei() != null && !request.getImei().isBlank()) {
            deviceRepository.findByImei(request.getImei()).ifPresent(d -> {
                throw new IllegalArgumentException("Device with this IMEI already exists: " + request.getImei());
            });
        }

        // Generate human-readable machine ID
        String machineId = generateMachineId();

        Machine machine = new Machine();
        machine.setMachineId(machineId);
        machine.setOrganizationId(null); // unassigned inventory — super admin assigns to org later
        machine.setCustomerId(null);
        machine.setName(request.getName());
        machine.setSerialNumber(request.getSerialNumber());
        machine.setModel(request.getModel());
        machine.setStatus("IN_STOCK");
        machine.setIsOnline(false);

        machine = machineRepository.save(machine);

        // Create device record bound to this machine
        Device device = new Device();
        device.setOrganizationId(null);
        device.setMachineId(machine.getId());
        device.setImei(request.getImei());
        device.setSimNumber(request.getSimNumber());
        device.setProtocolType(request.getProtocolType());
        device.setFirmwareVersion(request.getFirmwareVersion());
        device.setIsActive(true);
        deviceRepository.save(device);

        log.info("Created machine machineId={} id={}", machine.getMachineId(), machine.getId());
        return toDto(machine);
    }

    @Transactional
    public MachineDto updateMachine(UUID id, UpdateMachineRequest request) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        // Super admin can update any machine; org users cannot (enforced by controller @PreAuthorize)

        if (request.getName() != null) machine.setName(request.getName());
        if (request.getSerialNumber() != null) machine.setSerialNumber(request.getSerialNumber());
        if (request.getModel() != null) machine.setModel(request.getModel());

        machine = machineRepository.save(machine);

        // Update device fields if provided
        if (request.getImei() != null || request.getSimNumber() != null ||
            request.getProtocolType() != null || request.getFirmwareVersion() != null) {
            Device device = deviceRepository.findByMachineId(machine.getId()).orElse(null);
            if (device != null) {
                if (request.getImei() != null) {
                    // Check IMEI uniqueness if changing
                    if (!request.getImei().equals(device.getImei())) {
                        deviceRepository.findByImei(request.getImei()).ifPresent(d -> {
                            throw new IllegalArgumentException("Device with this IMEI already exists: " + request.getImei());
                        });
                        device.setImei(request.getImei());
                    }
                }
                if (request.getSimNumber() != null) device.setSimNumber(request.getSimNumber());
                if (request.getProtocolType() != null) device.setProtocolType(request.getProtocolType());
                if (request.getFirmwareVersion() != null) device.setFirmwareVersion(request.getFirmwareVersion());
                deviceRepository.save(device);
            }
        }

        log.info("Updated machine id={}", machine.getId());
        return toDto(machine);
    }

    @Transactional
    public void deleteMachine(UUID id) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        // Delete associated device
        deviceRepository.findByMachineId(machine.getId()).ifPresent(deviceRepository::delete);
        machineRepository.delete(machine);
        log.info("Deleted machine id={}", id);
    }

    @Transactional
    public MachineDto assignMachineToOrganization(UUID machineId, UUID orgId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        machine.setOrganizationId(orgId);
        machine = machineRepository.save(machine);

        // Update device org
        deviceRepository.findByMachineId(machine.getId()).ifPresent(device -> {
            device.setOrganizationId(orgId);
            deviceRepository.save(device);
        });

        log.info("Assigned machine {} to organization {}", machine.getMachineId(), orgId);
        return toDto(machine);
    }

    @Transactional
    public MachineDto assignMachineToCustomer(UUID machineId, UUID customerId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));

        // Validate machine belongs to caller's org (tenant guard)
        UUID orgId = ownerContextService.getOrganizationId();
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        // Validate customer belongs to same org
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        tenantGuard.validateTenantAccess(customer.getOrganizationId());

        // Close any existing active assignment for this machine
        machineAssignmentRepository.findByMachineIdAndUnassignedAtIsNull(machine.getId())
                .ifPresent(assignment -> {
                    assignment.setUnassignedAt(LocalDateTime.now());
                    machineAssignmentRepository.save(assignment);
                });

        // Create new assignment record
        MachineAssignment assignment = new MachineAssignment();
        assignment.setOrganizationId(orgId);
        assignment.setMachineId(machine.getId());
        assignment.setCustomerId(customerId);
        assignment.setAssignedAt(LocalDateTime.now());
        machineAssignmentRepository.save(assignment);

        // Update machine
        machine.setCustomerId(customerId);
        machine.setStatus("ACTIVE");
        machine = machineRepository.save(machine);

        log.info("Assigned machine {} to customer {}", machine.getMachineId(), customerId);
        return toDto(machine);
    }

    @Transactional
    public MachineDto unassignMachineFromCustomer(UUID machineId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        // Close active assignment
        machineAssignmentRepository.findByMachineIdAndUnassignedAtIsNull(machine.getId())
                .ifPresent(assignment -> {
                    assignment.setUnassignedAt(LocalDateTime.now());
                    machineAssignmentRepository.save(assignment);
                });

        machine.setCustomerId(null);
        machine.setStatus("IN_STOCK");
        machine = machineRepository.save(machine);

        log.info("Unassigned machine {} from customer", machine.getMachineId());
        return toDto(machine);
    }

    private String generateMachineId() {
        String maxId = machineRepository.findMaxMachineId();
        if (maxId == null) {
            return "YG000001";
        }
        // Parse the numeric part and increment
        String numPart = maxId.substring(2); // remove "YG"
        int next = Integer.parseInt(numPart) + 1;
        return "YG" + String.format("%06d", next);
    }

    private MachineDto toDto(Machine m) {
        MachineDto dto = new MachineDto(
                m.getId(),
                m.getMachineId(),
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
        // Populate device fields
        deviceRepository.findByMachineId(m.getId()).ifPresent(device -> {
            dto.setImei(device.getImei());
            dto.setSimNumber(device.getSimNumber());
            dto.setProtocolType(device.getProtocolType());
            dto.setFirmwareVersion(device.getFirmwareVersion());
        });
        // Populate customer name if assigned
        if (m.getCustomerId() != null) {
            customerRepository.findById(m.getCustomerId())
                    .ifPresent(customer -> dto.setCustomerName(customer.getName()));
        }
        return dto;
    }
}
