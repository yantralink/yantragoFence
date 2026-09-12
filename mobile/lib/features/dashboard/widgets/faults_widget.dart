import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';

/// Faults widget — displays the count of active faults as a metric card.
///
/// Composes [AppMetricCard]. Shows "No faults" (success) when count is 0,
/// otherwise "N fault(s)" with a danger/warning tone based on count.
/// Null count is shown as unavailable.
class FaultsWidget extends StatelessWidget {
  final int? count;

  const FaultsWidget({super.key, this.count});

  @override
  Widget build(BuildContext context) {
    if (count == null) {
      return const AppMetricCard(
        label: 'Faults',
        icon: Icons.error_outline,
        state: AppMetricState.unavailable,
        statusText: 'No report received',
      );
    }
    final int n = count!;
    if (n == 0) {
      return const AppMetricCard(
        label: 'Faults',
        icon: Icons.check_circle_outline,
        state: AppMetricState.available,
        value: '0',
        statusText: 'No active faults',
        statusTone: StatusTone.success,
      );
    }
    return AppMetricCard(
      label: 'Faults',
      icon: Icons.error_outline,
      state: AppMetricState.available,
      value: '$n',
      statusText: n == 1 ? '1 active fault' : '$n active faults',
      statusTone: n >= 3 ? StatusTone.danger : StatusTone.warning,
    );
  }
}
