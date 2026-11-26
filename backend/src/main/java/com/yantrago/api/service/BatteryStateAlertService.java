package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Battery-state transition detector for the Machine Details bulb semantics.
 *
 * The internal battery percentage encodes the machine states, matching the
 * app's Charging Status / Fence Fault tiles:
 *   {0, 20, 40, 60} = FAULT    -> FENCE_FAULT incident, WARNING
 *   other values    = NORMAL   -> resolves the fault incident silently
 *   null            -> ignored entirely (GPS frames carry no battery;
 *                      devices.battery_pct keeps the last real value
 *                      as previous state)
 *
 * Notifications fire only on state transitions, never on repeated values:
 * the caller passes the previous stored battery_pct; if it maps to the same
 * state as the current reading, nothing happens. A null previous reading is
 * the first-ever observation — it establishes the baseline silently.
 *
 * MACHINE_CHARGING is no longer raised by this service (the charging bulb
 * has no notification), but stale charging incidents are still resolved
 * silently on every transition so none linger open forever.
 *
 * Incident resolution is always silent (resolveIncidentSilently): resolves
 * are internal bookkeeping so a later re-entry into the same state produces
 * a fresh incident — users only ever see the FENCE_FAULT OPEN notification.
 *
 * Per AGENTS.md rule 4: invoked from the RabbitMQ telemetry path.
 * Per AGENTS.md rule 7: orgId is resolved from the device record upstream,
 * never from the message payload.
 */
@Service
public class BatteryStateAlertService {

    private static final Logger log = LoggerFactory.getLogger(BatteryStateAlertService.class);

    public static final String ALERT_TYPE_CHARGING = "MACHINE_CHARGING";
    public static final String ALERT_TYPE_FAULT = "FENCE_FAULT";

    /** Internal battery percentages that mean fence fault (red bulb ON). */
    private static final Set<Double> FAULT_PCTS = Set.of(0.0, 20.0, 40.0, 60.0);
    private static final String UNIT_PERCENT = "PERCENT";

    enum BatteryState { FAULT, NORMAL }

    private final CanonicalAlertService canonicalAlertService;

    public BatteryStateAlertService(CanonicalAlertService canonicalAlertService) {
        this.canonicalAlertService = canonicalAlertService;
    }

    /**
     * Evaluates a battery reading against the device's previous stored value.
     *
     * @param orgId               organization resolved from the device record
     * @param machineId           machine the device is bound to
     * @param previousBatteryPct  devices.battery_pct before this update (null = first reading)
     * @param currentBatteryPct   battery percentage from the telemetry message
     * @param observedAt          packet measurement time (null = now)
     */
    public void evaluate(UUID orgId, UUID machineId, Double previousBatteryPct,
                         double currentBatteryPct, Instant observedAt) {
        if (orgId == null || machineId == null) {
            log.debug("Skipping battery state evaluation — device not bound to a machine (orgId={}, machineId={})",
                    orgId, machineId);
            return;
        }
        if (previousBatteryPct == null) {
            // First-ever reading establishes the baseline — no notification.
            log.debug("Battery baseline established for machine={} at {}%", machineId, currentBatteryPct);
            return;
        }

        BatteryState previous = toState(previousBatteryPct);
        BatteryState current = toState(currentBatteryPct);
        if (previous == current) {
            return; // repeated heartbeat at the same state — dedup
        }

        log.info("Battery state transition for machine={}: {} -> {} ({}% -> {}%)",
                machineId, previous, current, previousBatteryPct, currentBatteryPct);

        switch (current) {
            case FAULT -> {
                // Stale charging incidents (raised by older versions) are cleaned up.
                canonicalAlertService.resolveIncidentSilently(
                        orgId, machineId, ALERT_TYPE_CHARGING, "Charging stopped — fault detected");
                canonicalAlertService.processAlertEvent(
                        machineId, ALERT_TYPE_FAULT, "WARNING",
                        "Fence fault detected",
                        observedAt, currentBatteryPct, UNIT_PERCENT, null);
            }
            case NORMAL -> {
                canonicalAlertService.resolveIncidentSilently(
                        orgId, machineId, ALERT_TYPE_FAULT, "Fault cleared");
                canonicalAlertService.resolveIncidentSilently(
                        orgId, machineId, ALERT_TYPE_CHARGING, "Charging stopped");
            }
        }
    }

    private static BatteryState toState(double batteryPct) {
        return FAULT_PCTS.contains(batteryPct) ? BatteryState.FAULT : BatteryState.NORMAL;
    }
}
