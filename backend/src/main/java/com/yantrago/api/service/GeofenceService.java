package com.yantrago.api.service;

import com.yantrago.api.dto.geofence.GeofenceDto;
import com.yantrago.api.dto.geofence.GeofenceRequest;
import com.yantrago.api.model.Geofence;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.GeofenceRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Geofence CRUD service with tenant AND customer isolation.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 * Per AGENTS.md rule 21: returns DTOs, never JPA entities.
 *
 * Customer-level isolation (Phase 11):
 * - Customer role: only sees/manages geofences for their own machines.
 * - Admin/org_admin role: sees all geofences in the org (oversight).
 *
 * One active geofence per machine — creating a new geofence for a machine
 * that already has one will deactivate the old one.
 */
@Service
public class GeofenceService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceService.class);

    private final GeofenceRepository geofenceRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final CustomerRepository customerRepository;
    private final MachineRepository machineRepository;
    private final JdbcTemplate jdbcTemplate;

    public GeofenceService(GeofenceRepository geofenceRepository,
                            OwnerContextService ownerContextService,
                            TenantGuard tenantGuard,
                            PermissionEvaluator permissionEvaluator,
                            CustomerRepository customerRepository,
                            MachineRepository machineRepository,
                            JdbcTemplate jdbcTemplate) {
        this.geofenceRepository = geofenceRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.customerRepository = customerRepository;
        this.machineRepository = machineRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<GeofenceDto> listGeofences() {
        UUID orgId = ownerContextService.getOrganizationId();

        // Customer-role users: only see geofences for their own machines
        UUID customerId = getCustomerIdIfCustomer();
        if (customerId != null) {
            return geofenceRepository
                    .findByOrganizationIdAndCustomerMachines(orgId, customerId)
                    .stream()
                    .map(this::toDto)
                    .toList();
        }

        // Admin / org_admin: see all geofences in org
        return geofenceRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public GeofenceDto getGeofence(UUID geofenceId) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());
        validateGeofenceOwnership(geofence);
        return toDto(geofence);
    }

    @Transactional(readOnly = true)
    public GeofenceDto getGeofenceForMachine(UUID machineId) {
        UUID orgId = ownerContextService.getOrganizationId();
        validateMachineOwnership(orgId, machineId);
        return geofenceRepository.findByOrganizationIdAndMachineId(orgId, machineId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public GeofenceDto createGeofence(GeofenceRequest request) {
        UUID orgId = ownerContextService.getOrganizationId();
        validateMachineOwnership(orgId, request.getMachineId());

        // Deactivate existing active geofence for this machine (one active per machine)
        geofenceRepository.findActiveByMachineId(orgId, request.getMachineId())
                .ifPresent(existing -> {
                    existing.setIsActive(false);
                    geofenceRepository.save(existing);
                    log.info("Deactivated previous geofence {} for machine={}",
                            existing.getId(), request.getMachineId());
                });

        Geofence geofence = new Geofence();
        geofence.setOrganizationId(orgId);
        geofence.setMachineId(request.getMachineId());
        geofence.setName(request.getName());
        geofence.setLatitude(request.getLatitude());
        geofence.setLongitude(request.getLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        geofence.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        geofence = geofenceRepository.save(geofence);
        log.info("Created geofence id={} machine={} org={} lat={} lng={} radius={}m",
                geofence.getId(), geofence.getMachineId(), orgId,
                geofence.getLatitude(), geofence.getLongitude(), geofence.getRadiusMeters());
        return toDto(geofence);
    }

    @Transactional
    public GeofenceDto updateGeofence(UUID geofenceId, GeofenceRequest request) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());
        validateMachineOwnership(geofence.getOrganizationId(), request.getMachineId());
        validateGeofenceOwnership(geofence);

        geofence.setMachineId(request.getMachineId());
        geofence.setName(request.getName());
        geofence.setLatitude(request.getLatitude());
        geofence.setLongitude(request.getLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        if (request.getIsActive() != null) {
            geofence.setIsActive(request.getIsActive());
        }

        geofence = geofenceRepository.save(geofence);
        log.info("Updated geofence id={} machine={}", geofence.getId(), geofence.getMachineId());
        return toDto(geofence);
    }

    @Transactional
    public void deleteGeofence(UUID geofenceId) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());
        validateGeofenceOwnership(geofence);
        geofenceRepository.delete(geofence);
        log.info("Deleted geofence id={}", geofenceId);
    }

    @Transactional
    public GeofenceDto toggleGeofence(UUID geofenceId, boolean active) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found: " + geofenceId));
        tenantGuard.validateTenantAccess(geofence.getOrganizationId());
        validateGeofenceOwnership(geofence);
        geofence.setIsActive(active);
        geofence = geofenceRepository.save(geofence);
        log.info("Toggled geofence id={} active={}", geofenceId, active);
        return toDto(geofence);
    }

    private GeofenceDto toDto(Geofence g) {
        String customerName = resolveCustomerName(g.getMachineId());
        return new GeofenceDto(
                g.getId(),
                g.getOrganizationId(),
                g.getMachineId(),
                g.getName(),
                g.getLatitude(),
                g.getLongitude(),
                g.getRadiusMeters(),
                g.getIsActive(),
                g.getCreatedAt(),
                g.getUpdatedAt(),
                customerName
        );
    }

    /**
     * Resolves the customer name for a machine via a lightweight query.
     * Returns null if the machine is unassigned or customer not found.
     */
    private String resolveCustomerName(UUID machineId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT c.name FROM machines m JOIN customers c ON m.customer_id = c.id " +
                    "WHERE m.id = ?",
                    String.class, machineId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the customer ID if the current user has the 'customer' role,
     * otherwise null. Used for customer-level isolation.
     */
    private UUID getCustomerIdIfCustomer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        boolean isCustomer = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_CUSTOMER".equals(a));
        if (!isCustomer) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof UUID userId)) {
            return null;
        }
        return customerRepository.findByUserId(userId)
                .map(com.yantrago.api.model.Customer::getId)
                .orElse(null);
    }

    /**
     * Validates that the current customer owns the specified machine.
     * Admin/org_admin roles bypass this check (they have org-wide access).
     *
     * @param orgId      organization ID
     * @param machineId   machine ID to validate
     * @throws SecurityException if the customer does not own this machine
     */
    private void validateMachineOwnership(UUID orgId, UUID machineId) {
        UUID customerId = getCustomerIdIfCustomer();
        if (customerId == null) {
            return; // admin role — org-wide access
        }
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        if (!customerId.equals(machine.getCustomerId())) {
            throw new SecurityException(
                    "Access denied: machine " + machineId + " is not assigned to you");
        }
    }

    /**
     * Validates that the current customer owns the machine associated with
     * the given geofence. Admin/org_admin roles bypass this check.
     */
    private void validateGeofenceOwnership(Geofence geofence) {
        UUID customerId = getCustomerIdIfCustomer();
        if (customerId == null) {
            return; // admin role — org-wide access
        }
        Machine machine = machineRepository.findById(geofence.getMachineId())
                .orElseThrow(() -> new SecurityException(
                        "Access denied: machine not found for geofence"));
        if (!customerId.equals(machine.getCustomerId())) {
            throw new SecurityException(
                    "Access denied: this geofence belongs to another customer's machine");
        }
    }
}
