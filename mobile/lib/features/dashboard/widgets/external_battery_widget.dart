import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';

import 'external_battery_mapper.dart';

/// External battery widget — displays the machine's main (external)
/// battery as a 0–100% charge estimate, derived from the 0x94 voltage
/// report.
///
/// Composes [AppMetricCard]. Null or non-positive voltage is shown as
/// unavailable ("No voltage report"), never fabricated as 0% — consistent
/// with the app's "do not fabricate readings" design rule. The status
/// line shows "Charging" (green) when external power is connected,
/// otherwise the zone label ("Critical Low Battery" / "Low Battery" /
/// "Battery level" / "Battery Full").
///
/// This is a *different* battery from [BatteryWidget]: that tile shows
/// the tracker's internal backup battery (a coarse 7-level enum); this
/// tile derives a real percentage from the machine's actual battery
/// voltage. Both tiles coexist.
class ExternalBatteryWidget extends StatelessWidget {
  final double? voltage;
  final bool? charging;

  const ExternalBatteryWidget({super.key, this.voltage, this.charging});

  @override
  Widget build(BuildContext context) {
    final int? pct = voltageToExternalBatteryPct(voltage);
    if (pct == null) {
      return const AppMetricCard(
        label: 'Battery',
        icon: Icons.battery_std,
        state: AppMetricState.unavailable,
        statusText: 'No voltage report',
      );
    }

    final bool isCharging = charging == true;
    final BatteryZone zone = externalBatteryZone(pct);

    // Below 15% always shows "Battery Low" and at 100% "Battery Full" —
    // both take precedence over the charging indicator.
    final String statusText;
    final StatusTone statusTone;
    final IconData icon;

    if (pct < 15) {
      statusText = 'Battery Low';
      statusTone = StatusTone.danger;
      icon = Icons.battery_alert;
    } else if (pct == 100) {
      statusText = 'Battery Full';
      statusTone = StatusTone.success;
      icon = Icons.battery_full;
    } else if (isCharging) {
      statusText = 'Charging';
      statusTone = StatusTone.success;
      icon = Icons.battery_charging_full;
    } else {
      switch (zone) {
        case BatteryZone.critical:
          statusText = 'Critical Low Battery';
          statusTone = StatusTone.danger;
          icon = Icons.battery_alert;
        case BatteryZone.low:
          statusText = 'Low Battery';
          statusTone = StatusTone.warning;
          icon = Icons.battery_std;
        case BatteryZone.normal:
          statusText = 'Battery level';
          statusTone = StatusTone.neutral;
          icon = Icons.battery_std;
        case BatteryZone.full:
          statusText = 'Battery Full';
          statusTone = StatusTone.success;
          icon = Icons.battery_std;
      }
    }

    return AppMetricCard(
      label: 'Battery',
      icon: icon,
      state: AppMetricState.available,
      value: '$pct',
      unit: '%',
      statusText: statusText,
      statusTone: statusTone,
    );
  }
}
