import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Machine status widget — displays engine on/off status (device ACC)
/// as a shared metric card.
///
/// Composes [AppMetricCard]. Null ignition is shown as unavailable, not
/// off — the device may simply not have reported ACC yet (legacy devices
/// or no ACC-capable packet received).
class IgnitionWidget extends StatelessWidget {
  final bool? ignitionOn;

  const IgnitionWidget({super.key, this.ignitionOn});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (ignitionOn == null) {
      return AppMetricCard(
        label: l10n.metricLabelMachineStatus,
        icon: Icons.key,
        state: AppMetricState.unavailable,
        statusText: l10n.metricNoAccReport,
      );
    }
    return AppMetricCard(
      label: l10n.metricLabelMachineStatus,
      icon: Icons.key,
      state: AppMetricState.available,
      statusTone: ignitionOn! ? StatusTone.success : StatusTone.neutral,
      value: ignitionOn! ? 'ON' : 'OFF', // always-en: raw state code
      statusText:
          ignitionOn! ? l10n.machineRunning : l10n.machineStopped,
    );
  }
}
