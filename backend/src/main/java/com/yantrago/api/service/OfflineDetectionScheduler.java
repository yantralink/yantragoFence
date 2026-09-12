package com.yantrago.api.service;

import com.yantrago.api.model.Alert;
import com.yantrago.api.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded multi-instance-safe offline detection scheduler.
 *
 * Uses row claiming (SKIP LOCKED + lease) to allow safe parallel execution.
 * Detects devices that haven't sent a heartbeat within the grace period and
 * creates DEVICE_OFFLINE incidents via CanonicalAlertService.
 *
 * Liveness ordering: never fires offline for a device that just reconnected.
 * Reconnect grace: waits grace-minutes after last heartbeat before marking offline.
 * Unknown-device handling: skips devices not found in the devices table.
 *
 * Per notification plan Phase 2: scheduler restarts do not duplicate reminders.
 */
@Component
public class OfflineDetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OfflineDetectionScheduler.class);
    private static final String ALERT_TYPE = "DEVICE_OFFLINE";

    private final JdbcTemplate jdbcTemplate;
    private final CanonicalAlertService canonicalAlertService;
    private final AlertRepository alertRepository;
    private final TimeProvider timeProvider;

    private final int graceMinutes;
    private final int batchSize;
    private final int leaseSeconds;

    public OfflineDetectionScheduler(JdbcTemplate jdbcTemplate,
                                      CanonicalAlertService canonicalAlertService,
                                      AlertRepository alertRepository,
                                      TimeProvider timeProvider,
                                      @Value("${alert.offline.grace-minutes:15}") int graceMinutes,
                                      @Value("${alert.offline.batch-size:100}") int batchSize,
                                      @Value("${alert.offline.lease-seconds:30}") int leaseSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.canonicalAlertService = canonicalAlertService;
        this.alertRepository = alertRepository;
        this.timeProvider = timeProvider;
        this.graceMinutes = graceMinutes;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * Runs every minute (configurable). Claims a batch of devices to evaluate,
     * checks their last heartbeat against the grace period, and opens or
     * resolves DEVICE_OFFLINE incidents.
     */
    @Scheduled(fixedDelayString = "${alert.offline.check-interval-ms:60000}")
    public void detectOfflineDevices() {
        LocalDateTime now = timeProvider.now();
        LocalDateTime graceCutoff = now.minusMinutes(graceMinutes);

        // Claim a batch of devices to evaluate using row claiming
        List<UUID> deviceIds = claimDevicesToEvaluate(now);
        if (deviceIds.isEmpty()) {
            return;
        }

        log.debug("Offline detection: claimed {} devices to evaluate", deviceIds.size());

        for (UUID deviceId : deviceIds) {
            try {
                evaluateDevice(deviceId, now, graceCutoff);
            } catch (Exception e) {
                log.error("Offline detection failed for device={}: {}", deviceId, e.getMessage(), e);
            }
        }
    }

    /**
     * Syncs new active devices into offline_detection_state and refreshes
     * heartbeat data for existing rows. Called before claiming to ensure
     * all active devices have a state row to claim against.
     */
    private void syncDeviceState() {
        jdbcTemplate.update(
                "INSERT INTO offline_detection_state (device_id, organization_id, machine_id, last_heartbeat_at) " +
                        "SELECT d.id, d.organization_id, d.machine_id, d.last_seen_at " +
                        "FROM devices d " +
                        "WHERE d.is_active = true AND d.machine_id IS NOT NULL " +
                        "ON CONFLICT (device_id) DO UPDATE SET " +
                        "  last_heartbeat_at = excluded.last_heartbeat_at, " +
                        "  machine_id = excluded.machine_id"
        );
    }

    /**
     * Claims a batch of devices using CTE with ORDER BY + SKIP LOCKED + lease
     * for multi-instance safety. Only claims up to batchSize devices whose
     * lease is not held, ordered by last_evaluated_at (NULLS FIRST so
     * never-evaluated devices are prioritised).
     */
    private List<UUID> claimDevicesToEvaluate(LocalDateTime now) {
        LocalDateTime leaseUntil = now.plusSeconds(leaseSeconds);

        // First, ensure all active devices have a state row with fresh heartbeat
        syncDeviceState();

        // Claim a batch using CTE with ORDER BY for deterministic, reliable row claiming
        return jdbcTemplate.queryForList(
                "WITH claimable AS (" +
                        "  SELECT device_id FROM offline_detection_state" +
                        "  WHERE claim_lease_until IS NULL OR claim_lease_until <= ?" +
                        "  ORDER BY last_evaluated_at NULLS FIRST" +
                        "  LIMIT ?" +
                        "  FOR UPDATE SKIP LOCKED" +
                        ") UPDATE offline_detection_state SET claim_lease_until = ?" +
                        "  FROM claimable WHERE offline_detection_state.device_id = claimable.device_id" +
                        "  RETURNING offline_detection_state.device_id",
                UUID.class,
                now, batchSize, leaseUntil
        );
    }

    /**
     * Evaluates a single device for offline detection.
     *
     * Liveness ordering: if the device has a heartbeat within the grace period,
     * it's online — resolve any open offline incident.
     * If the heartbeat is older than the grace period, the device is offline —
     * open an incident if none is open.
     */
    private void evaluateDevice(UUID deviceId, LocalDateTime now, LocalDateTime graceCutoff) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                    "SELECT s.device_id, s.organization_id, s.machine_id, s.last_heartbeat_at, " +
                            "s.offline_alert_open, s.offline_alert_id " +
                            "FROM offline_detection_state s WHERE s.device_id = ?",
                    deviceId
            );
        } catch (Exception e) {
            log.debug("Device {} not found in offline_detection_state (unknown device)", deviceId);
            return;
        }

        UUID orgId = (UUID) row.get("organization_id");
        UUID machineId = (UUID) row.get("machine_id");
        if (orgId == null || machineId == null) {
            log.debug("Device {} has no org or machine binding — skipping", deviceId);
            return;
        }

        java.sql.Timestamp lastHeartbeatTs = (java.sql.Timestamp) row.get("last_heartbeat_at");
        LocalDateTime lastHeartbeat = lastHeartbeatTs != null
                ? lastHeartbeatTs.toLocalDateTime()
                : null;
        boolean alertOpen = (boolean) row.get("offline_alert_open");

        // Update last evaluated time
        jdbcTemplate.update(
                "UPDATE offline_detection_state SET last_evaluated_at = ? WHERE device_id = ?",
                now, deviceId
        );

        if (lastHeartbeat == null) {
            // Device has never sent a heartbeat — can't determine offline status
            log.debug("Device {} has no heartbeat history — skipping", deviceId);
            return;
        }

        boolean isOnline = lastHeartbeat.isAfter(graceCutoff);

        if (isOnline) {
            // Device is online — resolve any open offline incident
            if (alertOpen) {
                log.info("Device {} reconnected — resolving DEVICE_OFFLINE for machine {}",
                        deviceId, machineId);
                canonicalAlertService.resolveIncident(orgId, machineId, ALERT_TYPE,
                        "Device reconnected");
                jdbcTemplate.update(
                        "UPDATE offline_detection_state SET offline_alert_open = FALSE, " +
                                "offline_alert_id = NULL WHERE device_id = ?",
                        deviceId
                );
            }
        } else {
            // Device is offline — open incident if none is open
            if (!alertOpen) {
                long minutesSinceHeartbeat = java.time.Duration.between(lastHeartbeat, now).toMinutes();
                String message = String.format("Device offline: no heartbeat for %d minutes (grace: %d min)",
                        minutesSinceHeartbeat, graceMinutes);
                Alert alert = canonicalAlertService.processAlertEvent(
                        machineId,
                        ALERT_TYPE,
                        "WARNING",
                        message,
                        now.toInstant(ZoneOffset.UTC),
                        (double) minutesSinceHeartbeat,
                        "minutes",
                        null // scheduler source — no external source event ID
                );
                if (alert != null) {
                    jdbcTemplate.update(
                            "UPDATE offline_detection_state SET offline_alert_open = TRUE, " +
                                    "offline_alert_id = ? WHERE device_id = ?",
                            alert.getId(), deviceId
                    );
                    log.info("Opened DEVICE_OFFLINE incident for device={} machine={} ({}min since heartbeat)",
                            deviceId, machineId, minutesSinceHeartbeat);
                }
            }
        }
    }
}
