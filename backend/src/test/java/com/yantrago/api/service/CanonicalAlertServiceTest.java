package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yantrago.api.model.Alert;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.repository.MachineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CanonicalAlertService.
 *
 * Verifies:
 * - Deduplication: same (org, machine, alertType) updates existing open incident
 * - New incident creation when no open incident exists
 * - Outbox event written in same transaction
 * - Machine not found returns null
 *
 * Per notification plan Phase 1 acceptance criteria.
 */
class CanonicalAlertServiceTest {

    private AlertRepository alertRepository;
    private MachineRepository machineRepository;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private CanonicalAlertService canonicalAlertService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final String alertType = "LOW_BATTERY";
    private final String severity = "WARNING";
    private final String message = "Battery below 20%";

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        machineRepository = mock(MachineRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        canonicalAlertService = new CanonicalAlertService(
                alertRepository, machineRepository, jdbcTemplate, objectMapper);
    }

    private Machine createMachine(UUID orgId) {
        Machine machine = new Machine();
        machine.setId(machineId);
        machine.setOrganizationId(orgId);
        return machine;
    }

    @Test
    @DisplayName("processAlertEvent should create new open incident when none exists")
    void processAlertEvent_shouldCreateNewIncident() {
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(createMachine(orgId)));
        when(alertRepository.findOpenIncident(orgId, machineId, alertType)).thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        Alert result = canonicalAlertService.processAlertEvent(
                machineId, alertType, severity, message, Instant.now(), 15.0, "%", null);

        assertNotNull(result);
        assertEquals("OPEN", result.getIncidentState());
        assertEquals(1, result.getOccurrenceCount());
        assertEquals(orgId, result.getOrganizationId());
        assertEquals(alertType, result.getAlertType());
        verify(alertRepository).save(any(Alert.class));
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("processAlertEvent should update existing open incident (deduplication)")
    void processAlertEvent_shouldUpdateExistingIncident() {
        Alert existing = new Alert();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(orgId);
        existing.setMachineId(machineId);
        existing.setAlertType(alertType);
        existing.setSeverity("WARNING");
        existing.setMessage(message);
        existing.setIncidentState("OPEN");
        existing.setOccurrenceCount(1);
        existing.setTriggeredAt(java.time.LocalDateTime.now());
        existing.setFirstObservedAt(java.time.LocalDateTime.now());
        existing.setLastObservedAt(java.time.LocalDateTime.now());

        when(machineRepository.findById(machineId)).thenReturn(Optional.of(createMachine(orgId)));
        when(alertRepository.findOpenIncident(orgId, machineId, alertType)).thenReturn(Optional.of(existing));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        Alert result = canonicalAlertService.processAlertEvent(
                machineId, alertType, "CRITICAL", "Battery below 10%", Instant.now(), 8.0, "%", null);

        assertNotNull(result);
        assertEquals("OPEN", result.getIncidentState());
        assertEquals(2, result.getOccurrenceCount());
        assertEquals("CRITICAL", result.getSeverity());
        assertEquals("Battery below 10%", result.getMessage());
        verify(alertRepository).save(any(Alert.class));
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("processAlertEvent should return null when machine not found")
    void processAlertEvent_shouldReturnNullWhenMachineNotFound() {
        when(machineRepository.findById(machineId)).thenReturn(Optional.empty());

        Alert result = canonicalAlertService.processAlertEvent(
                machineId, alertType, severity, message, Instant.now(), null, null, null);

        assertNull(result);
        verify(alertRepository, never()).save(any(Alert.class));
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("resolveIncident should set state to RESOLVED and write outbox event")
    void resolveIncident_shouldResolveAndWriteOutbox() {
        Alert existing = new Alert();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(orgId);
        existing.setMachineId(machineId);
        existing.setAlertType(alertType);
        existing.setSeverity("WARNING");
        existing.setMessage(message);
        existing.setIncidentState("OPEN");
        existing.setOccurrenceCount(3);
        existing.setTriggeredAt(java.time.LocalDateTime.now());
        existing.setFirstObservedAt(java.time.LocalDateTime.now());
        existing.setLastObservedAt(java.time.LocalDateTime.now());

        when(alertRepository.findOpenIncident(orgId, machineId, alertType)).thenReturn(Optional.of(existing));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        Alert result = canonicalAlertService.resolveIncident(orgId, machineId, alertType, "Battery recovered");

        assertNotNull(result);
        assertEquals("RESOLVED", result.getIncidentState());
        assertNotNull(result.getResolvedAt());
        assertEquals("Battery recovered", result.getMessage());
        verify(alertRepository).save(any(Alert.class));
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("resolveIncident should return null when no open incident exists")
    void resolveIncident_shouldReturnNullWhenNoOpenIncident() {
        when(alertRepository.findOpenIncident(orgId, machineId, alertType)).thenReturn(Optional.empty());

        Alert result = canonicalAlertService.resolveIncident(orgId, machineId, alertType, "recovered");

        assertNull(result);
        verify(alertRepository, never()).save(any(Alert.class));
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }
}
