import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Battery widget — displays battery percentage and charging status.
///
/// Composes [AppMetricCard]. Null battery is shown as unavailable, not
/// zero. The status line shows "Charging" (green) when external power is
/// connected, otherwise "On battery" (neutral). The status tone reflects
/// the battery level threshold.
class BatteryWidget extends StatelessWidget {
  final int? battery;
  final bool? charging;

  const BatteryWidget({super.key, this.battery, this.charging});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (battery == null) {
      return AppMetricCard(
        label: l10n.metricLabelChargingStatus,
        icon: Icons.battery_5_bar,
        state: AppMetricState.unavailable,
        statusText: l10n.metricNoReport,
      );
    }
    final int level = battery!;
    final bool isCharging = charging == true;

    // Battery level tone (for low/critical indication)
    final StatusTone levelTone = level > 60
        ? StatusTone.success
        : level > 20
            ? StatusTone.warning
            : StatusTone.danger;

    // Charging takes precedence for the status line — it's more actionable
    // than "Healthy/Low/Critical" which the user can infer from the %.
    final String statusText = isCharging ? l10n.batteryCharging : l10n.batteryOnBattery;
    final StatusTone statusTone = isCharging ? StatusTone.success : levelTone;
    final IconData icon = isCharging
        ? Icons.battery_charging_full
        : Icons.battery_5_bar;

    return AppMetricCard(
      label: l10n.metricLabelChargingStatus,
      icon: icon,
      state: AppMetricState.available,
      value: '$level', // always-en: numeric value, Latin digits
      unit: '%', // always-en: unit
      statusText: statusText,
      statusTone: statusTone,
    );
  }
}
