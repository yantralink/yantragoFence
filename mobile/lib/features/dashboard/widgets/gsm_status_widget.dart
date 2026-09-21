import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metric_card.dart';
import 'package:yantrago/l10n/l10n.dart';

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
    final l10n = context.l10n;
    if (signal == null) {
      return AppMetricCard(
        label: l10n.metricLabelDeviceSignal,
        icon: Icons.signal_cellular_alt,
        state: AppMetricState.unavailable,
        statusText: l10n.metricNoReport,
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
        ? l10n.signalStrong
        : level >= 2
            ? l10n.signalWeak
            : level >= 1
                ? l10n.signalPoor
                : l10n.signalNone;
    return AppMetricCard(
      label: l10n.metricLabelDeviceSignal,
      icon: Icons.signal_cellular_alt,
      state: AppMetricState.available,
      value: '$level', // always-en: numeric value, Latin digits
      unit: '/4', // always-en: unit
      statusText: status,
      statusTone: tone,
    );
  }
}
