import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';

/// Recharge status widget — displays charging status as a shared metric card.
///
/// Composes [AppMetricCard]. Null charging state is shown as unavailable.
/// This reflects the machine's charging status, not SIM validity.
class RechargeStatusWidget extends StatelessWidget {
  final bool? charging;

  const RechargeStatusWidget({super.key, this.charging});

  @override
  Widget build(BuildContext context) {
    if (charging == null) {
      return const AppMetricCard(
        label: 'Recharge',
        icon: Icons.battery_charging_full,
        state: AppMetricState.unavailable,
        statusText: 'No report received',
      );
    }
    final bool isCharging = charging!;
    return AppMetricCard(
      label: 'Recharge',
      icon: isCharging ? Icons.battery_charging_full : Icons.battery_alert,
      state: AppMetricState.available,
      value: isCharging ? 'Charging' : 'Not charging',
      statusText: isCharging ? 'Power connected' : 'On battery',
      statusTone: isCharging ? StatusTone.success : StatusTone.neutral,
    );
  }
}
