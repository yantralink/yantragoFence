import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Charging Status widget — displays an electrical-services indicator
/// based on internal battery.
///
/// Composes [AppMetricCard]. The icon glows GREEN when the internal
/// battery is exactly 10%. Otherwise it is grey. No battery percentage
/// or charging status text is shown — only the icon.
///
/// - battery == 10  → charging (green, Icons.electrical_services)
/// - battery != 10  → not charging (grey, Icons.electrical_services)
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
        icon: Icons.electrical_services,
        state: AppMetricState.unavailable,
      );
    }

    final bool isOn = battery == 10;
    const IconData icon = Icons.electrical_services;
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
