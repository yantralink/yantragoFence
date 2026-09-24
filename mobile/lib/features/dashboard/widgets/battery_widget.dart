import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Charging Status widget — displays a bulb indicator based on internal battery.
///
/// Composes [AppMetricCard]. The bulb glows GREEN when the internal battery
/// is exactly 10%. Otherwise the bulb is off (grey outline). No battery
/// percentage or charging status text is shown — only the bulb icon.
///
/// - battery == 10  → bulb ON (green, Icons.lightbulb)
/// - battery != 10  → bulb OFF (grey, Icons.lightbulb_outline)
/// - battery == null → unavailable
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
        icon: Icons.lightbulb_outline,
        state: AppMetricState.unavailable,
      );
    }

    final bool isOn = battery == 10;
    final IconData icon = isOn ? Icons.lightbulb : Icons.lightbulb_outline;
    final Color iconColor = isOn ? Colors.green : Colors.grey;

    return AppMetricCard(
      label: l10n.metricLabelChargingStatus,
      icon: icon,
      iconColor: iconColor,
      bodyIcon: icon,
      bodyIconColor: iconColor,
      bodyIconSize: 48,
      state: AppMetricState.available,
    );
  }
}
