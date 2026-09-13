package com.yantrago.api.service;

import com.yantrago.api.dto.geofence.GeofenceDto;
import com.yantrago.api.dto.geofence.GeofenceRequest;
import com.yantrago.api.model.Customer;
import com.yantrago.api.model.Geofence;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.GeofenceRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for GeofenceService — Phase 11 customer isolation.
 *
 * Verifies:
 * - Customer role sees only geofences for their own machines.
 * - Admin role sees all geofences in the org.
 * - Customer cannot create geofence for another customer's machine.
 * - Customer can create geofence for their own machine.
 * - Admin can create geofence for any machine.
 *
 * Per AGENTS.md rule 12: tests for new logic.
 */
class GeofenceServiceTest {

    private GeofenceRepository geofenceRepository;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private PermissionEvaluator permissionEvaluator;
    private CustomerRepository customerRepository;
    private MachineRepository machineRepository;
    private JdbcTemplate jdbcTemplate;

    private GeofenceService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID customerUserId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID geofenceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        geofenceRepository = mock(GeofenceRepository.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        customerRepository = mock(CustomerRepository.class);
        machineRepository = mock(MachineRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        service = new GeofenceService(geofenceRepository, ownerContextService,
                tenantGuard, permissionEvaluator, customerRepository, machineRepository,
                jdbcTemplate);

        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("admin lists all geofences in org")
    void listGeofences_admin_returnsAll() {
        setAdminAuth();
        Geofence g1 = makeGeofence(geofenceId, machineId);
        when(geofenceRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(g1));

        List<GeofenceDto> result = service.listGeofences();

        assertEquals(1, result.size());
        verify(geofenceRepository).findByOrganizationIdOrderByCreatedAtDesc(orgId);
        verify(geofenceRepository, never()).findByOrganizationIdAndCustomerMachines(any(), any());
    }

    @Test
    @DisplayName("customer lists only geofences for their machines")
    void listGeofences_customer_returnsOwnOnly() {
        setCustomerAuth();
        Geofence g1 = makeGeofence(geofenceId, machineId);
        when(geofenceRepository.findByOrganizationIdAndCustomerMachines(orgId, customerId))
                .thenReturn(List.of(g1));

        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.of(customer));

        List<GeofenceDto> result = service.listGeofences();

        assertEquals(1, result.size());
        verify(geofenceRepository).findByOrganizationIdAndCustomerMachines(orgId, customerId);
        verify(geofenceRepository, never()).findByOrganizationIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("customer cannot create geofence for another customer's machine")
    void createGeofence_customerOtherMachine_throwsSecurityException() {
        setCustomerAuth();
        GeofenceRequest request = makeRequest(machineId);

        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.of(customer));

        UUID otherCustomerId = UUID.randomUUID();
        Machine machine = mock(Machine.class);
        when(machine.getCustomerId()).thenReturn(otherCustomerId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        assertThrows(SecurityException.class, () -> service.createGeofence(request));
        verify(geofenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("customer can create geofence for their own machine")
    void createGeofence_customerOwnMachine_succeeds() {
        setCustomerAuth();
        GeofenceRequest request = makeRequest(machineId);

        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        when(customerRepository.findByUserId(customerUserId)).thenReturn(Optional.of(customer));

        Machine machine = mock(Machine.class);
        when(machine.getCustomerId()).thenReturn(customerId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        when(geofenceRepository.findActiveByMachineId(orgId, machineId)).thenReturn(Optional.empty());
        when(geofenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GeofenceDto result = service.createGeofence(request);

        assertNotNull(result);
        verify(geofenceRepository).save(any());
    }

    @Test
    @DisplayName("admin can create geofence for any machine")
    void createGeofence_admin_succeeds() {
        setAdminAuth();
        GeofenceRequest request = makeRequest(machineId);

        when(geofenceRepository.findActiveByMachineId(orgId, machineId)).thenReturn(Optional.empty());
        when(geofenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GeofenceDto result = service.createGeofence(request);

        assertNotNull(result);
        verify(geofenceRepository).save(any());
        verify(machineRepository, never()).findById(any());
    }

    // --- Helpers ---

    private void setAdminAuth() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setCustomerAuth() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                customerUserId, null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Geofence makeGeofence(UUID id, UUID machineId) {
        Geofence g = new Geofence();
        g.setId(id);
        g.setOrganizationId(orgId);
        g.setMachineId(machineId);
        g.setName("Test");
        g.setLatitude(18.6);
        g.setLongitude(73.7);
        g.setRadiusMeters(200);
        g.setIsActive(true);
        return g;
    }

    private GeofenceRequest makeRequest(UUID machineId) {
        GeofenceRequest r = new GeofenceRequest();
        r.setMachineId(machineId);
        r.setName("Test Geofence");
        r.setLatitude(18.6);
        r.setLongitude(73.7);
        r.setRadiusMeters(200);
        r.setIsActive(true);
        return r;
    }
}
