import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/analytics/providers/analytics_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Today / 7 Days / 30 Days range selector for the Analytics page.
class AnalyticsRangeChips extends ConsumerWidget {
  const AnalyticsRangeChips({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selected = ref.watch(analyticsRangeProvider);
    final l10n = context.l10n;

    final options = <(AnalyticsRange, String)>[
      (AnalyticsRange.today, l10n.analyticsRangeToday),
      (AnalyticsRange.days7, l10n.analyticsRange7d),
      (AnalyticsRange.days30, l10n.analyticsRange30d),
    ];

    return Row(
      children: [
        for (final (range, label) in options) ...[
          ChoiceChip(
            label: Text(label),
            selected: selected == range,
            onSelected: (_) =>
                ref.read(analyticsRangeProvider.notifier).state = range,
          ),
          if (range != AnalyticsRange.days30) const SizedBox(width: 8),
        ],
      ],
    );
  }
}
