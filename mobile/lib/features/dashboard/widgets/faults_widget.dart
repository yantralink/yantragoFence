import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Fence Fault widget — displays an electric-fence indicator based on
/// internal battery.
///
/// Composes [AppMetricCard]. The fence icon glows RED when the internal
/// battery is exactly 100%, indicating a fence fault. Otherwise it is
/// grey. No value or status text is shown — only the icon.
///
/// - battery == 100  → fault (red, Icons.fence)
/// - battery != 100  → normal (grey, Icons.fence)
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
    const IconData icon = Icons.fence;
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
