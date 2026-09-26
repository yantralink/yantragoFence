package com.yantrago.api.service;

import com.yantrago.api.dto.analytics.BatteryHealthDto;
import com.yantrago.api.model.Machine;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.repository.TelemetryRepository;
import com.yantrago.api.security.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule-based battery health analysis over voltage_readings.
 *
 * This is deliberately heuristic, not ML: the score blends a standard
 * lead-acid SOC table, reading variance, time below 12 V, and a
 * least-squares declining trend. "Estimated days until low" is shown
 * only when the decline is consistent (R² above threshold).
 */
@Service
public class BatteryHealthService {

    /** Healthy voltage band — confirmed by user for the chart. */
    static final double BAND_MIN = 11.8;
    static final double BAND_MAX = 14.0;
    /** Below this the battery is effectively discharged. */
    static final double CRITICAL_V = 11.0;
    /** Minimum readings needed for any analysis. */
    static final int MIN_POINTS = 5;
    /** Minimum span needed for a meaningful trend. */
    private static final Duration MIN_TREND_SPAN = Duration.ofHours(20);
    /** Steady decline threshold — ~50 mV/day. */
    private static final double DECLINE_V_PER_DAY = 0.05;
    /** R² below this means the line is noise, not a trend. */
    private static final double MIN_R2 = 0.25;
    /** Projection horizon. */
    private static final int PROJECTION_DAYS = 7;

    private final MachineRepository machineRepository;
    private final TelemetryRepository telemetryRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;

    public BatteryHealthService(MachineRepository machineRepository,
                                TelemetryRepository telemetryRepository,
                                OwnerContextService ownerContextService,
                                TenantGuard tenantGuard) {
        this.machineRepository = machineRepository;
        this.telemetryRepository = telemetryRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
    }

    @Transactional(readOnly = true)
    public BatteryHealthDto getBatteryHealth(UUID machineId, LocalDateTime from, LocalDateTime to) {
        UUID orgId = ownerContextService.getOrganizationId();
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId));
        tenantGuard.validateTenantAccess(machine.getOrganizationId());

        List<Map<String, Object>> rows =
                telemetryRepository.findVoltageByMachineId(orgId, machineId, from, to);
        return analyze(rows);
    }

    /**
     * Pure analysis over ordered (recorded_at, voltage) rows — kept
     * static so unit tests can feed synthetic series directly.
     */
    static BatteryHealthDto analyze(List<Map<String, Object>> rows) {
        List<double[]> pts = new ArrayList<>(); // [hours since first, volts]
        LocalDateTime t0 = null;
        LocalDateTime lastAt = null;
        for (Map<String, Object> r : rows) {
            LocalDateTime at = toLocalDateTime(r.get("recorded_at"));
            Object v = r.get("voltage");
            if (at == null || !(v instanceof Number n)) continue;
            if (t0 == null) t0 = at;
            pts.add(new double[]{Duration.between(t0, at).toMinutes() / 60.0, n.doubleValue()});
            lastAt = at;
        }

        if (pts.size() < MIN_POINTS) {
            return new BatteryHealthDto(-1, "INSUFFICIENT_DATA", "INSUFFICIENT_DATA",
                    null, null, null, List.of());
        }

        double latest = pts.get(pts.size() - 1)[1];
        double[] regression = linregress(pts); // [slope V/hr, intercept, r2]
        Double slopeVPerDay = null;
        if (Duration.between(t0, lastAt).compareTo(MIN_TREND_SPAN) >= 0) {
            slopeVPerDay = regression[0] * 24.0;
        }

        boolean declining = slopeVPerDay != null
                && slopeVPerDay < -DECLINE_V_PER_DAY
                && regression[2] >= MIN_R2;

        Integer daysUntilLow = declining
                ? (int) Math.round((latest - BAND_MIN) / -slopeVPerDay)
                : null;

        List<BatteryHealthDto.ProjectionPoint> projection = List.of();
        if (declining && daysUntilLow != null && daysUntilLow > 0) {
            projection = project(lastAt, latest, slopeVPerDay, daysUntilLow);
        }

        return new BatteryHealthDto(
                score(pts, declining),
                status(latest, declining),
                insight(latest, declining),
                latest,
                slopeVPerDay == null ? null : slopeVPerDay * 1000.0,
                daysUntilLow,
                projection);
    }

    // ---------- scoring ----------

    private static int score(List<double[]> pts, boolean declining) {
        double resting = median(pts.subList(Math.max(0, pts.size() - 20), pts.size()));
        int score = socScore(resting);

        score -= (int) Math.round(Math.min(15, stddev(pts) * 30));

        long below12 = pts.stream().filter(p -> p[1] < 12.0).count();
        score -= (int) Math.round(Math.min(20, below12 * 40.0 / pts.size()));

        if (declining) score -= 15;

        return Math.max(0, Math.min(100, score));
    }

    /** Piecewise lead-acid SOC table on resting voltage. */
    private static int socScore(double v) {
        double[][] table = {{10.5, 0}, {11.8, 40}, {12.0, 55}, {12.2, 65},
                {12.4, 75}, {12.5, 85}, {12.7, 100}};
        if (v >= 12.7) return 100;
        if (v <= 10.5) return 0;
        for (int i = 1; i < table.length; i++) {
            if (v <= table[i][0]) {
                double frac = (v - table[i - 1][0]) / (table[i][0] - table[i - 1][0]);
                return (int) Math.round(table[i - 1][1] + frac * (table[i][1] - table[i - 1][1]));
            }
        }
        return 0;
    }

    private static String status(double latest, boolean declining) {
        if (latest < CRITICAL_V) return "CRITICAL";
        if (latest < BAND_MIN) return "LOW";
        if (latest > BAND_MAX) return "HIGH";
        return declining ? "DECLINING" : "HEALTHY";
    }

    private static String insight(double latest, boolean declining) {
        if (latest < CRITICAL_V) return "CRITICALLY_LOW";
        return declining ? "DECLINING" : "STABLE";
    }

    // ---------- math helpers ----------

    /** Ordinary least squares → [slope, intercept, r2]. */
    private static double[] linregress(List<double[]> pts) {
        int n = pts.size();
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        for (double[] p : pts) {
            sx += p[0]; sy += p[1];
            sxx += p[0] * p[0]; sxy += p[0] * p[1]; syy += p[1] * p[1];
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) return new double[]{0, 0, 0};
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        double rDenom = Math.sqrt((n * sxx - sx * sx) * (n * syy - sy * sy));
        double r = rDenom == 0 ? 0 : (n * sxy - sx * sy) / rDenom;
        return new double[]{slope, intercept, r * r};
    }

    private static double median(List<double[]> pts) {
        double[] v = pts.stream().mapToDouble(p -> p[1]).sorted().toArray();
        int n = v.length;
        return n % 2 == 1 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2;
    }

    private static double stddev(List<double[]> pts) {
        double mean = pts.stream().mapToDouble(p -> p[1]).average().orElse(0);
        double var = pts.stream().mapToDouble(p -> (p[1] - mean) * (p[1] - mean))
                .average().orElse(0);
        return Math.sqrt(var);
    }

    private static List<BatteryHealthDto.ProjectionPoint> project(
            LocalDateTime lastAt, double latest, double slopeVPerDay, int daysUntilLow) {
        List<BatteryHealthDto.ProjectionPoint> out = new ArrayList<>();
        int days = Math.min(daysUntilLow, PROJECTION_DAYS);
        for (int d = 0; d <= days; d++) {
            double v = Math.max(BAND_MIN, latest + slopeVPerDay * d);
            out.add(new BatteryHealthDto.ProjectionPoint(lastAt.plusDays(d), v));
        }
        return out;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime t) return t;
        return null;
    }
}
