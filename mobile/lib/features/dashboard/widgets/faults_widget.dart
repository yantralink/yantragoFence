import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

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
    final l10n = context.l10n;
    if (count == null) {
      return AppMetricCard(
        label: l10n.metricLabelFenceFault,
        icon: Icons.error_outline,
        state: AppMetricState.unavailable,
        statusText: l10n.metricNoReport,
      );
    }
    final int n = count!;
    if (n == 0) {
      return AppMetricCard(
        label: l10n.metricLabelFenceFault,
        icon: Icons.check_circle_outline,
        state: AppMetricState.available,
        value: '0', // always-en: numeric value, Latin digits
        statusText: l10n.faultsNone,
        statusTone: StatusTone.success,
      );
    }
    return AppMetricCard(
      label: l10n.metricLabelFenceFault,
      icon: Icons.error_outline,
      state: AppMetricState.available,
      value: '$n', // always-en: numeric value, Latin digits
      statusText: l10n.faultsCount(n),
      statusTone: n >= 3 ? StatusTone.danger : StatusTone.warning,
    );
  }
}
