import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Fence Fault widget — displays a bulb indicator based on internal battery.
///
/// Composes [AppMetricCard]. The bulb glows RED when the internal battery
/// is exactly 100%, indicating a fence fault. Otherwise the bulb is off
/// (grey outline). No value or status text is shown — only the bulb icon.
///
/// - battery == 100  → bulb ON (red, Icons.lightbulb)
/// - battery != 100  → bulb OFF (grey, Icons.lightbulb_outline)
/// - battery == null → unavailable
class FaultsWidget extends StatelessWidget {
  final int? batteryPct;

  const FaultsWidget({super.key, this.batteryPct});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (batteryPct == null) {
      return AppMetricCard(
        label: l10n.metricLabelFenceFault,
        icon: Icons.lightbulb_outline,
        state: AppMetricState.unavailable,
      );
    }

    final bool isFault = batteryPct == 100;
    final IconData icon = isFault ? Icons.lightbulb : Icons.lightbulb_outline;
    final Color iconColor = isFault ? Colors.red : Colors.grey;

    return AppMetricCard(
      label: l10n.metricLabelFenceFault,
      icon: icon,
      iconColor: iconColor,
      bodyIcon: icon,
      bodyIconColor: iconColor,
      bodyIconSize: 48,
      state: AppMetricState.available,
    );
  }
}
