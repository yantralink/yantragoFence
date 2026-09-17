/// External battery mapper — derives a 0–100% charge estimate from the
/// machine's external (main) battery voltage, as reported by the BR05
/// 0x94 information packet.
///
/// Formula (12 V lead-acid window):
///   pct = (voltage - 10.5) / (13.5 - 10.5) * 100, clamped to 0–100,
/// floored to an int (never overstate charge).
///
/// Mobile-only derivation (plan Option A). The function is pure and
/// isolated so it can be lifted to the backend later if reports need it.
library;

/// Voltage window for a 12 V lead-acid battery bank.
const double kExternalBatteryMinVoltage = 10.5; // fully discharged
const double kExternalBatteryMaxVoltage = 13.5; // fully charged

/// Zone thresholds (percent). Display labels only — the % stays continuous.
const int kExternalBatteryCriticalPct = 10;
const int kExternalBatteryLowPct = 30;
const int kExternalBatteryFullPct = 95;

/// Display zone for an external battery percentage.
enum BatteryZone { critical, low, normal, full }

/// Converts external voltage (volts) to a battery percentage.
///
/// Returns null when [voltage] is null or <= 0 (no report / bad sensor
/// frame — the caller must show "unavailable", never fabricate 0%).
/// Floors to int so charge is never overstated.
int? voltageToExternalBatteryPct(double? voltage) {
  if (voltage == null || voltage <= 0) return null;
  final double window =
      kExternalBatteryMaxVoltage - kExternalBatteryMinVoltage; // 3.0 V
  final double pct =
      (voltage - kExternalBatteryMinVoltage) / window * 100.0;
  if (pct <= 0) return 0;
  if (pct >= 100) return 100;
  return pct.floor();
}

/// Maps a battery percentage to its display zone.
BatteryZone externalBatteryZone(int pct) {
  if (pct < kExternalBatteryCriticalPct) return BatteryZone.critical;
  if (pct < kExternalBatteryLowPct) return BatteryZone.low;
  if (pct < kExternalBatteryFullPct) return BatteryZone.normal;
  return BatteryZone.full;
}
