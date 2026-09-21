import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Recharge status widget — displays charging status as a shared metric card.
///
/// Composes [AppMetricCard]. Null charging state is shown as unavailable.
/// This reflects the machine's charging status, not SIM validity.
class RechargeStatusWidget extends StatelessWidget {
  final bool? charging;

  const RechargeStatusWidget({super.key, this.charging});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (charging == null) {
      return AppMetricCard(
        label: l10n.metricLabelRecharge,
        icon: Icons.battery_charging_full,
        state: AppMetricState.unavailable,
        statusText: l10n.metricNoReport,
      );
    }
    final bool isCharging = charging!;
    return AppMetricCard(
      label: l10n.metricLabelRecharge,
      icon: isCharging ? Icons.battery_charging_full : Icons.battery_alert,
      state: AppMetricState.available,
      value: isCharging ? l10n.batteryCharging : l10n.batteryNotCharging,
      statusText: isCharging ? l10n.powerConnected : l10n.batteryOnBattery,
      statusTone: isCharging ? StatusTone.success : StatusTone.neutral,
    );
  }
}
