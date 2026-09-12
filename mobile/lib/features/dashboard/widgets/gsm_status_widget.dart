import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';

/// GSM status widget — displays GSM signal strength as a shared metric card.
///
/// Composes [AppMetricCard]. Null signal is shown as unavailable, not
/// zero. The status tone reflects the domain threshold chosen here.
///
/// BR05 protocol provides a coarse 0–4 GSM signal level (not dBm).
/// 0 = no signal, 4 = strongest. Thresholds are tuned for this scale.
class GsmStatusWidget extends StatelessWidget {
  final int? signal;

  const GsmStatusWidget({super.key, this.signal});

  @override
  Widget build(BuildContext context) {
    if (signal == null) {
      return const AppMetricCard(
        label: 'GSM Signal',
        icon: Icons.signal_cellular_alt,
        state: AppMetricState.unavailable,
        statusText: 'No report received',
      );
    }
    final int level = signal!;
    // BR05 GSM signal level is 0–4 (coarse enum, not dBm).
    final StatusTone tone = level >= 3
        ? StatusTone.success
        : level >= 2
            ? StatusTone.warning
            : StatusTone.danger;
    final String status = level >= 3
        ? 'Strong signal'
        : level >= 2
            ? 'Weak signal'
            : level >= 1
                ? 'Poor signal'
                : 'No signal';
    return AppMetricCard(
      label: 'GSM Signal',
      icon: Icons.signal_cellular_alt,
      state: AppMetricState.available,
      value: '$level',
      unit: '/4',
      statusText: status,
      statusTone: tone,
    );
  }
}
