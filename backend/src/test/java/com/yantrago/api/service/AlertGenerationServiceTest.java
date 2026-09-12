package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yantrago.api.model.Alert;
import com.yantrago.api.model.AlertRule;
import com.yantrago.api.model.AlertRuleState;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.repository.AlertRuleRepository;
import com.yantrago.api.repository.AlertRuleStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for AlertGenerationService with controlled Clock.
 *
 * Verifies:
 * - Sustain window: condition must persist before opening incident
 * - Recovery window: condition must clear before resolving incident
 * - Brief noise: condition met then cleared before sustain -> no incident
 * - Escalation: severity escalates after escalation_minutes
 * - Restart-safe: state persists across evaluations
 *
 * Per notification plan Phase 2 acceptance criteria.
 */
class AlertGenerationServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AlertRepository alertRepository;
    private AlertRuleRepository alertRuleRepository;
    private AlertRuleStateRepository alertRuleStateRepository;
    private CanonicalAlertService canonicalAlertService;
    private ObjectMapper objectMapper;
    private FixedTimeProvider timeProvider;

    private AlertGenerationService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();

    private LocalDateTime fixedNow;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        alertRepository = mock(AlertRepository.class);
        alertRuleRepository = mock(AlertRuleRepository.class);
        alertRuleStateRepository = mock(AlertRuleStateRepository.class);
        canonicalAlertService = mock(CanonicalAlertService.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        fixedNow = LocalDateTime.of(2025, 1, 15, 12, 0, 0);
        timeProvider = new FixedTimeProvider(fixedNow);

        service = new AlertGenerationService(
                jdbcTemplate, alertRepository, alertRuleRepository,
                alertRuleStateRepository, canonicalAlertService, objectMapper, timeProvider
        );
    }

    private AlertRule createRule(int sustainMinutes, int recoveryMinutes,
                                   Integer escalationMinutes, String escalationSeverity,
                                   String metric, String operator, double threshold) {
        AlertRule rule = new AlertRule();
        rule.setId(ruleId);
        rule.setOrganizationId(orgId);
        rule.setMachineId(null); // org-wide
        rule.setName("Test Rule");
        rule.setAlertType("LOW_BATTERY");
        rule.setConditionConfig(String.format(
                "{\"metric\":\"%s\",\"operator\":\"%s\",\"threshold\":%.1f,\"windowMinutes\":5}",
                metric, operator, threshold));
        rule.setSeverity("WARNING");
        rule.setIsActive(true);
        rule.setSustainMinutes(sustainMinutes);
        rule.setRecoveryMinutes(recoveryMinutes);
        rule.setEscalationMinutes(escalationMinutes);
        rule.setEscalationSeverity(escalationSeverity);
        rule.setRuleVersion(1);
        return rule;
    }

    private void mockTelemetryValue(double value) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class),
                eq(orgId), eq(machineId), any(), any()))
                .thenReturn(value);
    }

    private void mockNoTelemetry() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class),
                eq(orgId), eq(machineId), any(), any()))
                .thenThrow(new RuntimeException("no data"));
    }

    private void mockNoOpenIncident() {
        when(alertRepository.findOpenIncident(orgId, machineId, "LOW_BATTERY"))
                .thenReturn(Optional.empty());
    }

    private void mockOpenIncident(Alert alert) {
        when(alertRepository.findOpenIncident(orgId, machineId, "LOW_BATTERY"))
                .thenReturn(Optional.of(alert));
    }

    private void mockNoState() {
        when(alertRuleStateRepository.findByRuleIdAndMachineId(ruleId, machineId))
                .thenReturn(Optional.empty());
    }

    private void mockState(AlertRuleState state) {
        when(alertRuleStateRepository.findByRuleIdAndMachineId(ruleId, machineId))
                .thenReturn(Optional.of(state));
    }

    @Test
    @DisplayName("Sustain window: first violation observation does not open incident")
    void sustainWindow_firstObservationDoesNotOpenIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();

        service.evaluateAlertsForMachine(orgId, machineId);

        // Should NOT create an alert — just record violation start
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(alertRuleStateRepository).save(any(AlertRuleState.class));
    }

    @Test
    @DisplayName("Sustain window: after sustain_minutes, opens incident")
    void sustainWindow_opensIncidentAfterSustain() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // First evaluation: record violation start
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // Advance time past sustain window
        timeProvider.setNow(fixedNow.plusMinutes(11));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();

        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow);
        state.setIsViolating(true);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("LOW_BATTERY"), eq("WARNING"),
                any(), any(), eq(15.0), eq("battery"), any());
    }

    @Test
    @DisplayName("Brief noise: condition met then cleared before sustain -> no incident")
    void briefNoise_doesNotOpenIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // First evaluation: condition met, record violation start
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // Advance time but still within sustain window
        timeProvider.setNow(fixedNow.plusMinutes(5));

        // Condition clears
        mockTelemetryValue(25.0);
        mockNoOpenIncident(); // no incident was opened

        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow);
        state.setIsViolating(true);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        // Should NOT have created an alert, and should clear violation state
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Recovery window: condition clears but incident not resolved until recovery_minutes")
    void recoveryWindow_resolvesAfterRecovery() {
        AlertRule rule = createRule(0, 10, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // Incident is open
        Alert openAlert = new Alert();
        openAlert.setId(UUID.randomUUID());
        openAlert.setOrganizationId(orgId);
        openAlert.setMachineId(machineId);
        openAlert.setAlertType("LOW_BATTERY");
        openAlert.setIncidentState("OPEN");
        openAlert.setLastObservedAt(fixedNow.minusMinutes(3));

        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow.minusMinutes(20));
        state.setIsViolating(true);

        // Condition clears but only 3 minutes since last observation (recovery = 10)
        mockTelemetryValue(25.0);
        mockOpenIncident(openAlert);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        // Should NOT resolve yet (within recovery window)
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());

        // Advance time past recovery window
        timeProvider.setNow(fixedNow.plusMinutes(8)); // 3 + 8 = 11 min since last observed
        mockTelemetryValue(25.0);
        mockOpenIncident(openAlert);
        openAlert.setLastObservedAt(fixedNow.minusMinutes(3)); // still 3 min ago relative to new now

        service.evaluateAlertsForMachine(orgId, machineId);

        // Should resolve now
        verify(canonicalAlertService).resolveIncident(eq(orgId), eq(machineId), eq("LOW_BATTERY"), any());
    }

    @Test
    @DisplayName("Escalation: severity escalates after escalation_minutes")
    void escalation_escalatesAfterMinutes() {
        AlertRule rule = createRule(0, 5, 30, "CRITICAL", "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // Violation started 35 minutes ago (past escalation threshold of 30)
        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow.minusMinutes(35));
        state.setIsViolating(true);

        // No open incident yet (sustain=0, but we simulate first open)
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        // Should open with CRITICAL severity (escalated)
        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("LOW_BATTERY"), eq("CRITICAL"),
                any(), any(), eq(15.0), eq("battery"), any());
    }

    @Test
    @DisplayName("No telemetry data: evaluation skipped without error")
    void noTelemetry_skipsEvaluation() {
        AlertRule rule = createRule(0, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));
        mockNoTelemetry();

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(alertRuleStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("No active rules: evaluation skipped")
    void noRules_skipsEvaluation() {
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of());

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Restart-safe: existing violation state is loaded and continued")
    void restartSafe_loadsExistingState() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // Simulate restart: state exists with violation started 8 min ago
        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow.minusMinutes(8));
        state.setIsViolating(true);

        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        // 8 < 10 min sustain, so no incident yet
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());

        // Advance to 11 min (past sustain)
        timeProvider.setNow(fixedNow.plusMinutes(3));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();

        service.evaluateAlertsForMachine(orgId, machineId);

        // Now should open incident
        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("LOW_BATTERY"), eq("WARNING"),
                any(), any(), eq(15.0), eq("battery"), any());
    }

    // ===== Flapping tests =====

    @Test
    @DisplayName("Flapping: condition toggles rapidly within sustain window -> no incident")
    void flapping_rapidTogglesWithinSustain_noIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // T=0: violation starts
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=3: condition clears (still within sustain)
        timeProvider.setNow(fixedNow.plusMinutes(3));
        mockTelemetryValue(25.0);
        mockNoOpenIncident();
        AlertRuleState state1 = new AlertRuleState();
        state1.setId(UUID.randomUUID());
        state1.setRuleId(ruleId);
        state1.setMachineId(machineId);
        state1.setOrganizationId(orgId);
        state1.setViolationStartedAt(fixedNow);
        state1.setIsViolating(true);
        mockState(state1);
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=5: condition violates again (new violation cycle)
        timeProvider.setNow(fixedNow.plusMinutes(5));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        AlertRuleState state2 = new AlertRuleState();
        state2.setId(UUID.randomUUID());
        state2.setRuleId(ruleId);
        state2.setMachineId(machineId);
        state2.setOrganizationId(orgId);
        state2.setViolationStartedAt(fixedNow.plusMinutes(5));
        state2.setIsViolating(false);
        mockState(state2);
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=8: condition clears again (still within new sustain)
        timeProvider.setNow(fixedNow.plusMinutes(8));
        mockTelemetryValue(25.0);
        mockNoOpenIncident();
        AlertRuleState state3 = new AlertRuleState();
        state3.setId(UUID.randomUUID());
        state3.setRuleId(ruleId);
        state3.setMachineId(machineId);
        state3.setOrganizationId(orgId);
        state3.setViolationStartedAt(fixedNow.plusMinutes(5));
        state3.setIsViolating(true);
        mockState(state3);
        service.evaluateAlertsForMachine(orgId, machineId);

        // No incident should have been opened during flapping
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Flapping: condition stabilizes after flapping -> opens incident after sustain")
    void flapping_stabilizesAfterFlapping_opensIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // T=0: violation
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=5: clears
        timeProvider.setNow(fixedNow.plusMinutes(5));
        mockTelemetryValue(25.0);
        mockNoOpenIncident();
        AlertRuleState s1 = new AlertRuleState();
        s1.setId(UUID.randomUUID());
        s1.setRuleId(ruleId);
        s1.setMachineId(machineId);
        s1.setOrganizationId(orgId);
        s1.setViolationStartedAt(fixedNow);
        s1.setIsViolating(true);
        mockState(s1);
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=6: violates again — new violation start
        timeProvider.setNow(fixedNow.plusMinutes(6));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        AlertRuleState s2 = new AlertRuleState();
        s2.setId(UUID.randomUUID());
        s2.setRuleId(ruleId);
        s2.setMachineId(machineId);
        s2.setOrganizationId(orgId);
        s2.setViolationStartedAt(fixedNow.plusMinutes(6));
        s2.setIsViolating(false);
        mockState(s2);
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=17: still violating (past 10-min sustain from T=6)
        timeProvider.setNow(fixedNow.plusMinutes(17));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        AlertRuleState s3 = new AlertRuleState();
        s3.setId(UUID.randomUUID());
        s3.setRuleId(ruleId);
        s3.setMachineId(machineId);
        s3.setOrganizationId(orgId);
        s3.setViolationStartedAt(fixedNow.plusMinutes(6));
        s3.setIsViolating(true);
        mockState(s3);
        service.evaluateAlertsForMachine(orgId, machineId);

        // Now should open incident
        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("LOW_BATTERY"), eq("WARNING"),
                any(), any(), eq(15.0), eq("battery"), any());
    }

    // ===== Window boundary tests =====

    @Test
    @DisplayName("Window boundary: exactly at sustain_minutes -> opens incident")
    void windowBoundary_exactlyAtSustain_opensIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // T=0: violation starts
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=10: exactly at sustain boundary
        timeProvider.setNow(fixedNow.plusMinutes(10));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow);
        state.setIsViolating(true);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("LOW_BATTERY"), eq("WARNING"),
                any(), any(), eq(15.0), eq("battery"), any());
    }

    @Test
    @DisplayName("Window boundary: one minute before sustain_minutes -> no incident")
    void windowBoundary_oneMinuteBeforeSustain_noIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // T=0: violation starts
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=9: one minute before sustain
        timeProvider.setNow(fixedNow.plusMinutes(9));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow);
        state.setIsViolating(true);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Window boundary: exactly at recovery_minutes -> resolves incident")
    void windowBoundary_exactlyAtRecovery_resolvesIncident() {
        AlertRule rule = createRule(0, 10, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        Alert openAlert = new Alert();
        openAlert.setId(UUID.randomUUID());
        openAlert.setOrganizationId(orgId);
        openAlert.setMachineId(machineId);
        openAlert.setAlertType("LOW_BATTERY");
        openAlert.setIncidentState("OPEN");
        openAlert.setLastObservedAt(fixedNow.minusMinutes(10));

        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow.minusMinutes(20));
        state.setIsViolating(true);

        // Condition clears, exactly 10 min since last observation
        mockTelemetryValue(25.0);
        mockOpenIncident(openAlert);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService).resolveIncident(eq(orgId), eq(machineId), eq("LOW_BATTERY"), any());
    }

    // ===== Timezone boundary tests =====

    @Test
    @DisplayName("Timezone boundary: violation spanning midnight UTC -> opens incident correctly")
    void timezoneBoundary_violationSpanningMidnightUTC_opensIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // T=23:50 UTC: violation starts
        fixedNow = LocalDateTime.of(2025, 1, 15, 23, 50, 0);
        timeProvider.setNow(fixedNow);
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=00:01 UTC next day: past 10-min sustain
        timeProvider.setNow(fixedNow.plusMinutes(11));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow);
        state.setIsViolating(true);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("LOW_BATTERY"), eq("WARNING"),
                any(), any(), eq(15.0), eq("battery"), any());
    }

    @Test
    @DisplayName("Timezone boundary: violation spanning month boundary -> opens incident correctly")
    void timezoneBoundary_violationSpanningMonthBoundary_opensIncident() {
        AlertRule rule = createRule(10, 5, null, null, "battery", "<", 20.0);
        when(alertRuleRepository.findActiveRulesForMachine(orgId, machineId))
                .thenReturn(List.of(rule));

        // T=Jan 31 23:55 UTC: violation starts
        fixedNow = LocalDateTime.of(2025, 1, 31, 23, 55, 0);
        timeProvider.setNow(fixedNow);
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        mockNoState();
        service.evaluateAlertsForMachine(orgId, machineId);

        // T=Feb 1 00:06 UTC: past 10-min sustain
        timeProvider.setNow(fixedNow.plusMinutes(11));
        mockTelemetryValue(15.0);
        mockNoOpenIncident();
        AlertRuleState state = new AlertRuleState();
        state.setId(UUID.randomUUID());
        state.setRuleId(ruleId);
        state.setMachineId(machineId);
        state.setOrganizationId(orgId);
        state.setViolationStartedAt(fixedNow);
        state.setIsViolating(true);
        mockState(state);

        service.evaluateAlertsForMachine(orgId, machineId);

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("LOW_BATTERY"), eq("WARNING"),
                any(), any(), eq(15.0), eq("battery"), any());
    }

    // ===== Fixed TimeProvider for testing =====

    private static class FixedTimeProvider implements TimeProvider {
        private LocalDateTime now;

        FixedTimeProvider(LocalDateTime now) {
            this.now = now;
        }

        void setNow(LocalDateTime now) {
            this.now = now;
        }

        @Override
        public LocalDateTime now() {
            return now;
        }

        @Override
        public Clock clock() {
            return Clock.fixed(Instant.from(now.atZone(ZoneOffset.UTC)), ZoneOffset.UTC);
        }
    }
}
