import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';

/// Ignition widget — displays engine on/off status (device ACC) as a
/// shared metric card.
///
/// Composes [AppMetricCard]. Null ignition is shown as unavailable, not
/// off — the device may simply not have reported ACC yet (legacy devices
/// or no ACC-capable packet received).
class IgnitionWidget extends StatelessWidget {
  final bool? ignitionOn;

  const IgnitionWidget({super.key, this.ignitionOn});

  @override
  Widget build(BuildContext context) {
    if (ignitionOn == null) {
      return const AppMetricCard(
        label: 'Ignition',
        icon: Icons.key,
        state: AppMetricState.unavailable,
        statusText: 'No ACC report',
      );
    }
    return AppMetricCard(
      label: 'Ignition',
      icon: Icons.key,
      state: AppMetricState.available,
      statusTone: ignitionOn! ? StatusTone.success : StatusTone.neutral,
      value: ignitionOn! ? 'ON' : 'OFF',
      statusText: ignitionOn! ? 'Engine running' : 'Engine off',
    );
  }
}
