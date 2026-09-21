import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Voltage widget — displays current voltage reading as a shared metric card.
///
/// Composes [AppMetricCard]. Null voltage is shown as unavailable, not
/// zero.
class VoltageWidget extends StatelessWidget {
  final double? voltage;

  const VoltageWidget({super.key, this.voltage});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (voltage == null) {
      return AppMetricCard(
        label: l10n.metricLabelVoltage,
        icon: Icons.bolt,
        state: AppMetricState.unavailable,
        statusText: l10n.metricNoReport,
      );
    }
    return AppMetricCard(
      label: l10n.metricLabelVoltage,
      icon: Icons.bolt,
      state: AppMetricState.available,
      value: voltage!.toStringAsFixed(2),
      unit: 'V', // always-en: unit
      statusText: l10n.statusLatestSample,
    );
  }
}
