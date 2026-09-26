import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Fence Fault widget — bulb indicator based on internal battery.
///
/// Composes [AppMetricCard]. The body bulb glows RED when the internal
/// battery is exactly 100%, indicating a fence fault. Otherwise the bulb
/// is off (grey outline). The small header icon is an electric fence.
/// No value or status text is shown — only the bulb.
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
        icon: Icons.fence,
        state: AppMetricState.unavailable,
      );
    }

    final bool isFault = batteryPct == 100;
    final IconData bodyIcon =
        isFault ? Icons.lightbulb : Icons.lightbulb_outline;
    final Color bodyColor = isFault ? Colors.red : Colors.grey;

    return AppMetricCard(
      label: l10n.metricLabelFenceFault,
      icon: Icons.fence,
      bodyIcon: bodyIcon,
      bodyIconColor: bodyColor,
      bodyIconSize: 48,
      state: AppMetricState.available,
    );
  }
}
