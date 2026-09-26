import 'package:flutter/material.dart';

import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/analytics/widgets/analytics_format.dart';
import 'package:yantrago/features/analytics/widgets/timeline_strip.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/fault_interval.dart';

/// Fence Fault card — Gantt strip of FENCE_FAULT incidents inside the
/// selected range plus a detail row per fault (time span + duration).
/// Only FENCE_FAULT is shown per product decision; other alert types
/// are intentionally excluded.
class FaultTimelineCard extends StatelessWidget {
  final List<FaultInterval> faults;
  final DateTime rangeStart;
  final DateTime rangeEnd;

  /// Detail rows are capped so a bad week can't blow up the card.
  static const int maxRows = 5;

  const FaultTimelineCard({
    super.key,
    required this.faults,
    required this.rangeStart,
    required this.rangeEnd,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final colors = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(l10n.analyticsFaultTitle,
                      style: text.titleMedium),
                ),
                if (faults.isNotEmpty)
                  _countChip(l10n.analyticsFaultCount(faults.length)),
              ],
            ),
            const SizedBox(height: 12),
            if (faults.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: AppStatePanel.empty(
                  title: l10n.analyticsFaultEmpty,
                  icon: Icons.verified_outlined,
                ),
              )
            else ...[
              TimelineStrip(
                rangeStart: rangeStart,
                rangeEnd: rangeEnd,
                intervals: [
                  for (final f in faults)
                    TimelineInterval(
                      start: f.triggeredAt,
                      end: f.resolvedAt,
                      color: f.ongoing ? Colors.orange : Colors.red,
                    ),
                ],
              ),
              const SizedBox(height: 8),
              for (final f in faults.take(maxRows))
                _faultRow(context, f),
              if (faults.length > maxRows)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    l10n.analyticsMoreRows(faults.length - maxRows),
                    style: text.labelSmall
                        ?.copyWith(color: colors.onSurfaceVariant),
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _countChip(String label) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.red.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: const TextStyle(
            color: Colors.red, fontWeight: FontWeight.w600, fontSize: 12),
      ),
    );
  }

  Widget _faultRow(BuildContext context, FaultInterval f) {
    final l10n = context.l10n;
    final text = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          Icon(Icons.error_outline,
              size: 14, color: f.ongoing ? Colors.orange : Colors.red),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              formatTimeRange(f.triggeredAt, f.resolvedAt),
              style: text.bodySmall,
            ),
          ),
          if (f.ongoing)
            _ongoingChip(l10n.analyticsOngoing)
          else
            Text(
              formatMinutes(l10n, f.durationMinutes),
              style: text.bodySmall
                  ?.copyWith(fontWeight: FontWeight.w600),
            ),
        ],
      ),
    );
  }

  Widget _ongoingChip(String label) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.orange.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: const TextStyle(
            color: Colors.orange,
            fontWeight: FontWeight.w600,
            fontSize: 11),
      ),
    );
  }
}
