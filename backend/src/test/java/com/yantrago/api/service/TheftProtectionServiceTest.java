package com.yantrago.api.service;

import com.yantrago.api.dto.theft.TheftProtectionStatusDto;
import com.yantrago.api.model.AlertRule;
import com.yantrago.api.model.Customer;
import com.yantrago.api.model.Geofence;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.AlertRuleRepository;
import com.yantrago.api.repository.CustomerRepository;
import com.yantrago.api.repository.GeofenceRepository;
import com.yantrago.api.repository.MachineRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for TheftProtectionService — Phase 11.
 *
 * Verifies:
 * - enable() creates geofence at current GPS + activates rule
 * - disable() deactivates geofence + rule
 * - enable() throws if no GPS available
 * - customer cannot enable for another customer's machine
 * - status returns correct protection state
 *
 * Per AGENTS.md rule 12: tests for new logic.
 */
class TheftProtectionServiceTest {

    private MachineRepository machineRepository;
    private GeofenceRepository geofenceRepository;
    private AlertRuleRepository alertRuleRepository;
    private CustomerRepository customerRepository;
    private CustomerSettingsService customerSettingsService;
    private OwnerContextService ownerContextService;
    private TenantGuard tenantGuard;
    private JdbcTemplate jdbcTemplate;

    private TheftProtectionService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        machineRepository = mock(MachineRepository.class);
        geofenceRepository = mock(GeofenceRepository.class);
        alertRuleRepository = mock(AlertRuleRepository.class);
        customerRepository = mock(CustomerRepository.class);
        customerSettingsService = mock(CustomerSettingsService.class);
        ownerContextService = mock(OwnerContextService.class);
        tenantGuard = mock(TenantGuard.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        service = new TheftProtectionService(machineRepository, geofenceRepository,
                alertRuleRepository, customerRepository, customerSettingsService,
                ownerContextService, tenantGuard, jdbcTemplate);

        when(ownerContextService.getOrganizationId()).thenReturn(orgId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("enable creates geofence and activates rule when GPS available")
    void enable_withGps_createsGeofenceAndActivatesRule() {
        Machine machine = mock(Machine.class);
        when(machine.getOrganizationId()).thenReturn(orgId);
        when(machine.getCustomerId()).thenReturn(customerId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        setCustomerAuth();

        when(jdbcTemplate.queryForMap(anyString(), eq(machineId)))
                .thenReturn(Map.of("latitude", 18.6, "longitude", 73.7));

        when(geofenceRepository.findActiveByMachineId(orgId, machineId))
                .thenReturn(Optional.empty());
        when(geofenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRule rule = mock(AlertRule.class);
        when(rule.getIsActive()).thenReturn(false);
        when(alertRuleRepository.findAllRulesByTypeAndMachine(orgId, machineId, "MACHINE_MOVING"))
                .thenReturn(List.of(rule));

        com.yantrago.api.model.CustomerSettings settings =
                mock(com.yantrago.api.model.CustomerSettings.class);
        when(settings.getDefaultGeofenceRadiusMeters()).thenReturn(200);
        when(settings.getDefaultSpeedThresholdKmh()).thenReturn(10);
        when(customerSettingsService.getOrCreateDefaults(customerId)).thenReturn(settings);

        TheftProtectionStatusDto result = service.enable(machineId);

        assertNotNull(result);
        verify(geofenceRepository).save(any());
        verify(alertRuleRepository).save(any());
    }

    @Test
    @DisplayName("enable throws IllegalStateException when no GPS available")
    void enable_noGps_throwsIllegalStateException() {
        Machine machine = mock(Machine.class);
        when(machine.getOrganizationId()).thenReturn(orgId);
        when(machine.getCustomerId()).thenReturn(customerId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        setCustomerAuth();

        when(jdbcTemplate.queryForMap(anyString(), eq(machineId)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        assertThrows(IllegalStateException.class, () -> service.enable(machineId));
        verify(geofenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("disable deactivates geofence and rule")
    void disable_deactivatesGeofenceAndRule() {
        Machine machine = mock(Machine.class);
        when(machine.getOrganizationId()).thenReturn(orgId);
        when(machine.getCustomerId()).thenReturn(customerId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        setCustomerAuth();

        // Use real Geofence object so setIsActive(false) actually changes state
        Geofence geofence = new Geofence();
        geofence.setId(UUID.randomUUID());
        geofence.setOrganizationId(orgId);
        geofence.setMachineId(machineId);
        geofence.setIsActive(true);
        when(geofenceRepository.findActiveByMachineId(orgId, machineId))
                .thenReturn(Optional.of(geofence));

        // Use real AlertRule so setIsActive(false) actually changes state
        AlertRule rule = new AlertRule();
        rule.setId(UUID.randomUUID());
        rule.setOrganizationId(orgId);
        rule.setMachineId(machineId);
        rule.setAlertType("MACHINE_MOVING");
        rule.setIsActive(true);
        when(alertRuleRepository.findAllRulesByTypeAndMachine(orgId, machineId, "MACHINE_MOVING"))
                .thenReturn(List.of(rule));

        TheftProtectionStatusDto result = service.disable(machineId);

        assertNotNull(result);
        assertFalse(result.protectionEnabled());
        assertFalse(geofence.getIsActive());
        assertFalse(rule.getIsActive());
        verify(geofenceRepository).save(geofence);
        verify(alertRuleRepository).save(rule);
    }

    @Test
    @DisplayName("customer cannot enable for another customer's machine")
    void enable_otherCustomerMachine_throwsSecurityException() {
        Machine machine = mock(Machine.class);
        when(machine.getOrganizationId()).thenReturn(orgId);
        UUID otherCustomerId = UUID.randomUUID();
        when(machine.getCustomerId()).thenReturn(otherCustomerId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        setCustomerAuth();

        assertThrows(SecurityException.class, () -> service.enable(machineId));
        verify(geofenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("status returns disabled when no geofence or rule active")
    void getStatus_noProtection_returnsDisabled() {
        Machine machine = mock(Machine.class);
        when(machine.getOrganizationId()).thenReturn(orgId);
        when(machine.getCustomerId()).thenReturn(customerId);
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        setCustomerAuth();

        when(geofenceRepository.findActiveByMachineId(orgId, machineId))
                .thenReturn(Optional.empty());
        when(alertRuleRepository.findAllRulesByTypeAndMachine(orgId, machineId, "MACHINE_MOVING"))
                .thenReturn(List.of());

        TheftProtectionStatusDto result = service.getStatus(machineId);

        assertFalse(result.protectionEnabled());
        assertFalse(result.geofenceActive());
        assertFalse(result.movementRuleActive());
    }

    // --- Helpers ---

    private void setCustomerAuth() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                customerId, null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        when(customerRepository.findByUserId(customerId)).thenReturn(Optional.of(customer));
    }
}
