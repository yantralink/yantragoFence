import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_metric_card.dart';

/// Voltage widget — displays current voltage reading as a shared metric card.
///
/// Composes [AppMetricCard]. Null voltage is shown as unavailable, not
/// zero.
class VoltageWidget extends StatelessWidget {
  final double? voltage;

  const VoltageWidget({super.key, this.voltage});

  @override
  Widget build(BuildContext context) {
    if (voltage == null) {
      return const AppMetricCard(
        label: 'Voltage',
        icon: Icons.bolt,
        state: AppMetricState.unavailable,
        statusText: 'No report received',
      );
    }
    return AppMetricCard(
      label: 'Voltage',
      icon: Icons.bolt,
      state: AppMetricState.available,
      value: voltage!.toStringAsFixed(2),
      unit: 'V',
      statusText: 'Latest sample',
    );
  }
}
