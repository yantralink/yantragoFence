package com.yantrago.api.security;

import com.yantrago.api.model.Customer;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.service.OwnerContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Centralized machine-access authorization policy.
 *
 * Combines organization membership (tenant isolation) with customer-level
 * assignment checks. Replaces the scattered, fail-open
 * {@code getCustomerIdIfCustomer} + {@code validateMachineOwnership} pattern
 * that was duplicated across MachineService, CommandService, AlertRuleService,
 * GeofenceService, and TheftProtectionService.
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 8: never trust tenant_id/organization_id from the frontend.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 *
 * Audit SEC-02: customer-level object authorization was missing on physical
 * commands and machine data. A customer with another machine's UUID in the
 * same organization could reach commands and sensitive data outside their
 * assignment.
 *
 * Audit SEC-07: missing tenant context was treated as platform administrator.
 * This policy fails closed: a customer whose Customer record is absent or
 * inactive is denied, not elevated to admin.
 */
@Component
public class MachineAccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(MachineAccessPolicy.class);
    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private final MachineRepository machineRepository;
    private final CustomerRepository customerRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public MachineAccessPolicy(MachineRepository machineRepository,
                               CustomerRepository customerRepository,
                               OwnerContextService ownerContextService,
                               TenantGuard tenantGuard) {
        this.machineRepository = machineRepository;
        this.customerRepository = customerRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    /**
     * Validates that the current authenticated user can access the given machine.
     *
     * Checks (in order):
     * 1. Organization membership (via TenantGuard)
     * 2. If the user has ROLE_CUSTOMER:
     *    a. Look up the Customer record by userId — absent → DENY (fail-closed)
     *    b. Customer isActive == false → DENY
     *    c. Machine customerId == null → DENY (machine not assigned to anyone)
     *    d. !customer.id.equals(machine.customerId) → DENY (assigned to different customer)
     * 3. Admin / org_admin / super_admin → org check only
     *
     * @param machine the machine to validate access for (must be non-null)
     * @throws SecurityException if access is denied
     */
    public void validateAccess(Machine machine) {
        if (machine == null) {
            throw new SecurityException("Access denied: machine is null");
        }

        // 1. Organization membership (tenant isolation)
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        // 2. Customer-level assignment check
        if (!isCustomerRole()) {
            // Admin / org_admin / super_admin — org check is sufficient
            return;
        }

        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Access denied: cannot resolve user identity");
        }

        // Fail-closed: a customer with no Customer record is denied, not elevated
        Optional<Customer> customerOpt = customerRepository.findByUserId(userId);
        if (customerOpt.isEmpty()) {
            log.warn("Access denied: user={} has ROLE_CUSTOMER but no Customer record", userId);
            throw new SecurityException("Access denied: customer record not found");
        }

        Customer customer = customerOpt.get();
        if (!Boolean.TRUE.equals(customer.getIsActive())) {
            log.warn("Access denied: customer={} is inactive", customer.getId());
            throw new SecurityException("Access denied: customer account is inactive");
        }

        UUID machineCustomerId = machine.getCustomerId();
        if (machineCustomerId == null) {
            log.warn("Access denied: machine={} has no customer assignment", machine.getId());
            throw new SecurityException("Access denied: this machine is not assigned to you");
        }

        if (!customer.getId().equals(machineCustomerId)) {
            log.warn("Access denied: customer={} attempted access to machine={} assigned to customer={}",
                    customer.getId(), machine.getId(), machineCustomerId);
            throw new SecurityException("Access denied: this machine is not assigned to you");
        }
    }

    /**
     * Loads a machine by ID and validates access.
     *
     * @param machineId the machine ID to load and validate
     * @return the loaded Machine entity
     * @throws IllegalArgumentException if the machine is not found
     * @throws SecurityException if access is denied
     */
    public Machine loadAndValidate(UUID machineId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        validateAccess(machine);
        return machine;
    }

    /**
     * Returns true if the current user has the ROLE_CUSTOMER authority.
     */
    private boolean isCustomerRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_CUSTOMER::equals);
    }

    /**
     * Returns the current user's UUID from the SecurityContext, or null.
     */
    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof UUID uuid ? uuid : null;
    }
}
