package com.yantrago.api.security;

import com.yantrago.api.model.Customer;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.service.OwnerContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for MachineAccessPolicy (SEC-02).
 *
 * Verifies the customer-level authorization matrix:
 * - Super admin: org check bypassed
 * - Admin / org_admin: org check only, no customer assignment check
 * - Customer: org check + customer assignment check (fail-closed)
 * - Customer with no Customer record: DENY (fail-closed, not elevated)
 * - Customer with inactive Customer record: DENY
 * - Machine with null customerId: DENY for customers
 * - Machine assigned to different customer: DENY
 * - Machine assigned to same customer: ALLOW
 * - Cross-organization access: DENY
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 8: never trust tenant_id/organization_id from the frontend.
 * Per AGENTS.md rule 9: sensitive operations require authorization.
 */
class MachineAccessPolicyTest {

    private MachineRepository machineRepository;
    private CustomerRepository customerRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private MachineAccessPolicy policy;

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();
    private final UUID customer1Id = UUID.randomUUID();
    private final UUID customer2Id = UUID.randomUUID();
    private final UUID customerUserId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        machineRepository = mock(MachineRepository.class);
        customerRepository = mock(CustomerRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = new TenantGuard(ownerContextService);
        policy = new MachineAccessPolicy(machineRepository, customerRepository,
                ownerContextService, tenantGuard);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- Super admin ---

    @Test
    @DisplayName("super_admin can access any machine in any org")
    void superAdmin_canAccessAnyMachine() {
        setSuperAdminAuth();
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(null);
        Machine machine = makeMachine(machineId, orgB, customer2Id);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        assertDoesNotThrow(() -> policy.validateAccess(machine));
    }

    // --- Admin / org_admin ---

    @Test
    @DisplayName("admin can access any machine in their org (no customer assignment check)")
    void admin_canAccessAnyMachineInOrg() {
        setAdminAuth();
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgA, customer1Id);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        assertDoesNotThrow(() -> policy.loadAndValidate(machineId));
    }

    @Test
    @DisplayName("admin cannot access machine in different org")
    void admin_cannotAccessCrossOrgMachine() {
        setAdminAuth();
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgB, customer2Id);

        assertThrows(SecurityException.class, () -> policy.validateAccess(machine));
    }

    // --- Customer: happy path ---

    @Test
    @DisplayName("customer can access machine assigned to them in their org")
    void customer_canAccessOwnMachine() {
        setCustomerAuth(customerUserId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgA, customer1Id);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));
        Customer customer = makeCustomer(customer1Id, customerUserId, true);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.of(customer));

        assertDoesNotThrow(() -> policy.loadAndValidate(machineId));
    }

    // --- Customer: denied cases (SEC-02) ---

    @Test
    @DisplayName("customer cannot access machine assigned to different customer in same org")
    void customer_cannotAccessOtherCustomerMachine() {
        setCustomerAuth(customerUserId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgA, customer2Id);
        Customer customer = makeCustomer(customer1Id, customerUserId, true);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.of(customer));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> policy.validateAccess(machine));
        assertTrue(ex.getMessage().contains("not assigned to you"));
    }

    @Test
    @DisplayName("customer cannot access machine in different org")
    void customer_cannotAccessCrossOrgMachine() {
        setCustomerAuth(customerUserId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgB, customer1Id);

        assertThrows(SecurityException.class, () -> policy.validateAccess(machine));
    }

    @Test
    @DisplayName("customer cannot access machine with null customerId (unassigned)")
    void customer_cannotAccessUnassignedMachine() {
        setCustomerAuth(customerUserId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgA, null);
        Customer customer = makeCustomer(customer1Id, customerUserId, true);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.of(customer));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> policy.validateAccess(machine));
        assertTrue(ex.getMessage().contains("not assigned"));
    }

    // --- Customer: fail-closed (SEC-07) ---

    @Test
    @DisplayName("SEC-02 fail-closed: customer with no Customer record is DENY, not elevated to admin")
    void customer_noCustomerRecord_failClosed() {
        setCustomerAuth(customerUserId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgA, customer1Id);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.empty());

        SecurityException ex = assertThrows(SecurityException.class,
                () -> policy.validateAccess(machine));
        assertTrue(ex.getMessage().contains("customer record not found"));
    }

    @Test
    @DisplayName("SEC-02 fail-closed: customer with inactive Customer record is DENY")
    void customer_inactiveCustomer_failClosed() {
        setCustomerAuth(customerUserId);
        when(ownerContextService.getOrganizationIdOrNull()).thenReturn(orgA);
        Machine machine = makeMachine(machineId, orgA, customer1Id);
        Customer customer = makeCustomer(customer1Id, customerUserId, false);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.of(customer));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> policy.validateAccess(machine));
        assertTrue(ex.getMessage().contains("inactive"));
    }

    @Test
    @DisplayName("SEC-02: null machine throws SecurityException")
    void validateAccess_nullMachine_throws() {
        assertThrows(SecurityException.class, () -> policy.validateAccess(null));
    }

    @Test
    @DisplayName("loadAndValidate throws IllegalArgumentException when machine not found")
    void loadAndValidate_machineNotFound_throws() {
        when(machineRepository.findById(machineId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> policy.loadAndValidate(machineId));
    }

    // --- Helpers ---

    private Machine makeMachine(UUID id, UUID orgId, UUID customerId) {
        Machine m = new Machine();
        m.setId(id);
        m.setOrganizationId(orgId);
        m.setCustomerId(customerId);
        return m;
    }

    private Customer makeCustomer(UUID id, UUID userId, boolean active) {
        Customer c = new Customer();
        c.setId(id);
        c.setUserId(userId);
        c.setIsActive(active);
        return c;
    }

    private void setSuperAdminAuth() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null,
                List.of(new SimpleGrantedAuthority("ROLE_super_admin")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setAdminAuth() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setCustomerAuth(UUID userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
