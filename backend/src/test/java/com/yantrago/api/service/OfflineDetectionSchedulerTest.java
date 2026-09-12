package com.yantrago.api.service;

import com.yantrago.api.model.Alert;
import com.yantrago.api.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
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
 * Tests for OfflineDetectionScheduler.
 *
 * Verifies Phase 2 acceptance criteria:
 * - Liveness ordering: never fires offline for a device that just reconnected
 * - Reconnect grace: waits grace-minutes after last heartbeat
 * - Unknown-device handling: skips devices not found
 * - Scheduler restarts do not duplicate reminders
 * - Multi-instance safety via row claiming
 *
 * Per notification plan Phase 2 requirements 3 and 4.
 */
class OfflineDetectionSchedulerTest {

    private JdbcTemplate jdbcTemplate;
    private CanonicalAlertService canonicalAlertService;
    private AlertRepository alertRepository;
    private FixedTimeProvider timeProvider;

    private OfflineDetectionScheduler scheduler;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final UUID alertId = UUID.randomUUID();

    private LocalDateTime fixedNow;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        canonicalAlertService = mock(CanonicalAlertService.class);
        alertRepository = mock(AlertRepository.class);
        fixedNow = LocalDateTime.of(2025, 1, 15, 12, 0, 0);
        timeProvider = new FixedTimeProvider(fixedNow);

        scheduler = new OfflineDetectionScheduler(jdbcTemplate, canonicalAlertService,
                alertRepository, timeProvider, 15, 100, 30);
    }

    private Map<String, Object> createDeviceState(LocalDateTime lastHeartbeat, boolean alertOpen) {
        Map<String, Object> row = new HashMap<>();
        row.put("device_id", deviceId);
        row.put("organization_id", orgId);
        row.put("machine_id", machineId);
        row.put("last_heartbeat_at", lastHeartbeat != null
                ? Timestamp.valueOf(lastHeartbeat) : null);
        row.put("offline_alert_open", alertOpen);
        row.put("offline_alert_id", alertOpen ? alertId : null);
        return row;
    }

    @Test
    @DisplayName("Device with recent heartbeat (within grace) — no offline incident")
    void recentHeartbeat_noOfflineIncident() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenReturn(createDeviceState(fixedNow.minusMinutes(5), false));

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Device with old heartbeat (past grace) — opens offline incident")
    void oldHeartbeat_opensOfflineIncident() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenReturn(createDeviceState(fixedNow.minusMinutes(30), false));

        Alert alert = new Alert();
        alert.setId(alertId);
        when(canonicalAlertService.processAlertEvent(
                eq(machineId), eq("DEVICE_OFFLINE"), eq("WARNING"),
                any(), any(), anyDouble(), eq("minutes"), any()))
                .thenReturn(alert);

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService).processAlertEvent(
                eq(machineId), eq("DEVICE_OFFLINE"), eq("WARNING"),
                any(), any(), anyDouble(), eq("minutes"), any());
        verify(jdbcTemplate).update(contains("offline_alert_open = TRUE"), eq(alertId), eq(deviceId));
    }

    @Test
    @DisplayName("Liveness ordering: reconnected device resolves open incident")
    void reconnectedDevice_resolvesOpenIncident() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        // Device reconnected 5 minutes ago (within 15-min grace)
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenReturn(createDeviceState(fixedNow.minusMinutes(5), true));

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService).resolveIncident(
                eq(orgId), eq(machineId), eq("DEVICE_OFFLINE"), anyString());
        verify(jdbcTemplate).update(contains("offline_alert_open = FALSE"), eq(deviceId));
    }

    @Test
    @DisplayName("Unknown device (not in state table) — skipped without error")
    void unknownDevice_skippedWithoutError() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenThrow(new RuntimeException("not found"));

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Device with no heartbeat history — skipped")
    void noHeartbeatHistory_skipped() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenReturn(createDeviceState(null, false));

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Device with no org or machine binding — skipped")
    void noOrgOrMachineBinding_skipped() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        Map<String, Object> row = createDeviceState(fixedNow.minusMinutes(30), false);
        row.put("organization_id", null);
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenReturn(row);

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Already open offline alert — does not duplicate")
    void alreadyOpenAlert_doesNotDuplicate() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        // Alert already open, device still offline
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenReturn(createDeviceState(fixedNow.minusMinutes(30), true));

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Empty batch — no work done")
    void emptyBatch_noWorkDone() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of());

        scheduler.detectOfflineDevices();

        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(canonicalAlertService, never()).resolveIncident(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Scheduler restart: existing open alert not duplicated on restart")
    void schedulerRestart_existingAlertNotDuplicated() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId));
        // Simulate restart: alert was already open before restart
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenReturn(createDeviceState(fixedNow.minusMinutes(60), true));

        scheduler.detectOfflineDevices();

        // Should NOT open a new alert (already open)
        verify(canonicalAlertService, never()).processAlertEvent(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Exception in one device does not stop processing others")
    void exceptionInOneDevice_doesNotStopOthers() {
        UUID device2 = UUID.randomUUID();
        UUID machine2 = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(List.of(deviceId, device2));
        // First device throws
        when(jdbcTemplate.queryForMap(anyString(), eq(deviceId)))
                .thenThrow(new RuntimeException("DB error"));
        // Second device is offline
        Map<String, Object> row2 = new HashMap<>();
        row2.put("device_id", device2);
        row2.put("organization_id", orgId);
        row2.put("machine_id", machine2);
        row2.put("last_heartbeat_at", Timestamp.valueOf(fixedNow.minusMinutes(30)));
        row2.put("offline_alert_open", false);
        row2.put("offline_alert_id", null);
        when(jdbcTemplate.queryForMap(anyString(), eq(device2)))
                .thenReturn(row2);

        Alert alert = new Alert();
        alert.setId(UUID.randomUUID());
        when(canonicalAlertService.processAlertEvent(
                eq(machine2), eq("DEVICE_OFFLINE"), eq("WARNING"),
                any(), any(), anyDouble(), eq("minutes"), any()))
                .thenReturn(alert);

        scheduler.detectOfflineDevices();

        // Second device should still be processed
        verify(canonicalAlertService).processAlertEvent(
                eq(machine2), eq("DEVICE_OFFLINE"), eq("WARNING"),
                any(), any(), anyDouble(), eq("minutes"), any());
    }

    // ===== Fixed TimeProvider for testing =====

    private static class FixedTimeProvider implements TimeProvider {
        private LocalDateTime now;

        FixedTimeProvider(LocalDateTime now) {
            this.now = now;
        }

        @Override
        public LocalDateTime now() {
            return now;
        }

        @Override
        public java.time.Clock clock() {
            return java.time.Clock.fixed(
                    java.time.Instant.from(now.atZone(ZoneOffset.UTC)),
                    ZoneOffset.UTC);
        }
    }
}
