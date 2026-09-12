package com.yantrago.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yantrago.api.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded multi-instance-safe expiry milestone scheduler.
 *
 * Tracks SIM recharge expiry and fires alerts at configurable milestones
 * (e.g. 7 days, 3 days, 1 day before expiry, and on expiry day).
 *
 * Expiry-cycle identity: one alert per (device, valid_until) cycle.
 * Renewal cancellation: a new recharge resolves any open expiry alert for
 * the same device.
 * No-recipient behavior: alerts are created regardless of recipient
 * availability (delivery is Phase 3).
 *
 * Per notification plan Phase 2: scheduler restarts do not duplicate reminders.
 */
@Component
public class ExpiryDetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryDetectionScheduler.class);
    private static final String ALERT_TYPE = "SIM_EXPIRY";

    private final JdbcTemplate jdbcTemplate;
    private final CanonicalAlertService canonicalAlertService;
    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;

    private final int batchSize;
    private final int leaseSeconds;
    private final int[] milestoneDays;

    public ExpiryDetectionScheduler(JdbcTemplate jdbcTemplate,
                                      CanonicalAlertService canonicalAlertService,
                                      ObjectMapper objectMapper,
                                      TimeProvider timeProvider,
                                      @Value("${alert.expiry.batch-size:100}") int batchSize,
                                      @Value("${alert.expiry.lease-seconds:30}") int leaseSeconds,
                                      @Value("${alert.expiry.milestones-days:7,3,1,0}") String milestonesStr) {
        this.jdbcTemplate = jdbcTemplate;
        this.canonicalAlertService = canonicalAlertService;
        this.objectMapper = objectMapper;
        this.timeProvider = timeProvider;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.milestoneDays = Arrays.stream(milestonesStr.split(","))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    /**
     * Runs every 5 minutes (configurable). Claims a batch of expiry states to
     * evaluate and fires milestone alerts.
     */
    @Scheduled(fixedDelayString = "${alert.expiry.check-interval-ms:300000}")
    public void detectExpiryMilestones() {
        LocalDateTime now = timeProvider.now();

        // First, sync new recharges into expiry_detection_state
        syncNewRecharges(now);

        // Claim a batch of unresolved expiry states to evaluate
        List<UUID> stateIds = claimExpiryStatesToEvaluate(now);
        if (stateIds.isEmpty()) {
            return;
        }

        log.debug("Expiry detection: claimed {} states to evaluate", stateIds.size());

        for (UUID stateId : stateIds) {
            try {
                evaluateExpiryState(stateId, now);
            } catch (Exception e) {
                log.error("Expiry detection failed for state={}: {}", stateId, e.getMessage(), e);
            }
        }
    }

    /**
     * Inserts expiry_detection_state rows for recharges that have a valid_until
     * but no existing state row. This handles new recharges.
     */
    private void syncNewRecharges(LocalDateTime now) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO expiry_detection_state (organization_id, device_id, machine_id, " +
                        "recharge_id, valid_until, cycle_key) " +
                        "SELECT r.organization_id, r.device_id, d.machine_id, r.id, r.valid_until, " +
                        "d.id::text || ':' || r.valid_until::text " +
                        "FROM recharges r " +
                        "JOIN devices d ON d.id = r.device_id " +
                        "WHERE r.valid_until IS NOT NULL " +
                        "  AND NOT EXISTS (SELECT 1 FROM expiry_detection_state e " +
                        "    WHERE e.cycle_key = d.id::text || ':' || r.valid_until::text) " +
                        "ON CONFLICT (cycle_key) DO NOTHING"
        );
        if (inserted > 0) {
            log.debug("Synced {} new recharge expiry states", inserted);
        }
    }

    /**
     * Claims a batch of unresolved expiry states using CTE with ORDER BY +
     * SKIP LOCKED + lease for multi-instance safety. Orders by
     * last_evaluated_at (NULLS FIRST) so never-evaluated states are
     * prioritised.
     */
    private List<UUID> claimExpiryStatesToEvaluate(LocalDateTime now) {
        LocalDateTime leaseUntil = now.plusSeconds(leaseSeconds);

        return jdbcTemplate.queryForList(
                "WITH claimable AS (" +
                        "  SELECT id FROM expiry_detection_state" +
                        "  WHERE resolved_at IS NULL" +
                        "    AND (claim_lease_until IS NULL OR claim_lease_until <= ?)" +
                        "  ORDER BY last_evaluated_at NULLS FIRST" +
                        "  LIMIT ?" +
                        "  FOR UPDATE SKIP LOCKED" +
                        ") UPDATE expiry_detection_state SET claim_lease_until = ?" +
                        "  FROM claimable WHERE expiry_detection_state.id = claimable.id" +
                        "  RETURNING expiry_detection_state.id",
                UUID.class,
                now, batchSize, leaseUntil
        );
    }

    /**
     * Evaluates a single expiry state for milestone firing.
     *
     * For each milestone day (e.g. 7, 3, 1, 0):
     * - If days until expiry == milestone day and milestone not yet fired -> fire alert
     * - If expiry has passed (days < 0) and not yet resolved -> resolve and mark expired
     */
    private void evaluateExpiryState(UUID stateId, LocalDateTime now) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                    "SELECT id, organization_id, device_id, machine_id, recharge_id, " +
                            "valid_until, milestones_fired, alert_open, alert_id, resolved_at " +
                            "FROM expiry_detection_state WHERE id = ?",
                    stateId
            );
        } catch (Exception e) {
            log.debug("Expiry state {} not found", stateId);
            return;
        }

        UUID orgId = (UUID) row.get("organization_id");
        UUID deviceId = (UUID) row.get("device_id");
        UUID machineId = (UUID) row.get("machine_id");
        java.sql.Timestamp validUntilTs = (java.sql.Timestamp) row.get("valid_until");
        String milestonesFiredJson = row.get("milestones_fired").toString();
        boolean alertOpen = (boolean) row.get("alert_open");

        if (validUntilTs == null) {
            return;
        }

        LocalDateTime validUntil = validUntilTs.toLocalDateTime();
        long daysUntilExpiry = ChronoUnit.DAYS.between(now.toLocalDate(), validUntil.toLocalDate());

        // Check if a newer recharge exists (renewal cancellation)
        Integer newerRechargeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recharges WHERE device_id = ? AND recharged_at > ?",
                Integer.class, deviceId, validUntil
        );
        if (newerRechargeCount != null && newerRechargeCount > 0) {
            // Renewal detected — resolve any open expiry alert
            if (alertOpen) {
                log.info("Renewal detected for device={} — resolving SIM_EXPIRY", deviceId);
                if (machineId != null) {
                    canonicalAlertService.resolveIncident(orgId, machineId, ALERT_TYPE,
                            "SIM recharged — expiry cancelled");
                }
            }
            jdbcTemplate.update(
                    "UPDATE expiry_detection_state SET resolved_at = ?, alert_open = FALSE " +
                            "WHERE id = ?",
                    now, stateId
            );
            log.debug("Expiry state {} resolved due to renewal", stateId);
            return;
        }

        // Parse fired milestones
        List<Integer> firedMilestones = parseMilestones(milestonesFiredJson);

        // Check if expiry has passed
        if (daysUntilExpiry < 0) {
            // Expired — resolve if alert is open
            if (alertOpen && machineId != null) {
                canonicalAlertService.resolveIncident(orgId, machineId, ALERT_TYPE,
                        "SIM plan expired");
            }
            jdbcTemplate.update(
                    "UPDATE expiry_detection_state SET resolved_at = ?, alert_open = FALSE, " +
                            "last_evaluated_at = ? WHERE id = ?",
                    now, now, stateId
            );
            log.info("SIM expired for device={} ({} days ago)", deviceId, -daysUntilExpiry);
            return;
        }

        // Check milestones
        for (int milestone : milestoneDays) {
            if (daysUntilExpiry == milestone && !firedMilestones.contains(milestone)) {
                // Fire milestone alert
                String message;
                String severity;
                if (milestone == 0) {
                    message = "SIM plan expires today";
                    severity = "CRITICAL";
                } else if (milestone <= 1) {
                    message = String.format("SIM plan expires in %d day", milestone);
                    severity = "CRITICAL";
                } else if (milestone <= 3) {
                    message = String.format("SIM plan expires in %d days", milestone);
                    severity = "WARNING";
                } else {
                    message = String.format("SIM plan expires in %d days", milestone);
                    severity = "INFO";
                }

                if (machineId != null) {
                    Alert alert = canonicalAlertService.processAlertEvent(
                            machineId,
                            ALERT_TYPE,
                            severity,
                            message,
                            now.toInstant(ZoneOffset.UTC),
                            (double) daysUntilExpiry,
                            "days",
                            null // scheduler source — no external source event ID
                    );
                    if (alert != null) {
                        firedMilestones.add(milestone);
                        jdbcTemplate.update(
                                "UPDATE expiry_detection_state SET milestones_fired = ?::jsonb, " +
                                        "alert_open = TRUE, alert_id = ?, last_evaluated_at = ? " +
                                        "WHERE id = ?",
                                milestonesToJson(firedMilestones), alert.getId(), now, stateId
                        );
                        log.info("Fired SIM_EXPIRY milestone {}d for device={} machine={}",
                                milestone, deviceId, machineId);
                    }
                }
                break; // Only fire one milestone per evaluation cycle
            }
        }

        // Update last evaluated time
        jdbcTemplate.update(
                "UPDATE expiry_detection_state SET last_evaluated_at = ? WHERE id = ?",
                now, stateId
        );
    }

    @SuppressWarnings("unchecked")
    private List<Integer> parseMilestones(String json) {
        try {
            List<Integer> list = objectMapper.readValue(json, ArrayList.class);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String milestonesToJson(List<Integer> milestones) {
        try {
            return objectMapper.writeValueAsString(milestones);
        } catch (Exception e) {
            return "[]";
        }
    }
}
