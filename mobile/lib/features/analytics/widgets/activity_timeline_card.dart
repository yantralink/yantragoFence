import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/analytics/widgets/analytics_format.dart';
import 'package:yantrago/features/analytics/widgets/timeline_strip.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine_activity.dart';

/// Machine Activity card — ignition-ON sessions as green bars on a
/// shared time axis, manual commands as ▼ ticks, and a detail row per
/// session ("when it start and stop").
class ActivityTimelineCard extends StatelessWidget {
  final MachineActivity activity;
  final DateTime rangeStart;
  final DateTime rangeEnd;

  /// Detail rows are capped so a busy month can't blow up the card.
  static const int maxRows = 5;

  const ActivityTimelineCard({
    super.key,
    required this.activity,
    required this.rangeStart,
    required this.rangeEnd,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final colors = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final sessions = activity.sessions;

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
                  child: Text(l10n.analyticsActivityTitle,
                      style: text.titleMedium),
                ),
                if (sessions.isNotEmpty)
                  _summaryChip(
                    '${l10n.analyticsOnTimeSummary(formatMinutes(l10n, activity.totalOnMinutes))} · '
                    '${l10n.analyticsSessionCount(sessions.length)}',
                  ),
              ],
            ),
            const SizedBox(height: 12),
            if (sessions.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: AppStatePanel.empty(
                  title: l10n.analyticsActivityEmpty,
                  icon: Icons.history_toggle_off_outlined,
                ),
              )
            else ...[
              TimelineStrip(
                rangeStart: rangeStart,
                rangeEnd: rangeEnd,
                intervals: [
                  for (final s in sessions)
                    TimelineInterval(
                      start: s.startAt,
                      end: s.endAt,
                      color: s.ongoing
                          ? Colors.green.withValues(alpha: 0.55)
                          : Colors.green,
                    ),
                ],
                ticks: [
                  for (final m in activity.commandMarkers)
                    TimelineTick(
                      at: m.issuedAt,
                      color: _markerColor(m.status),
                      tooltip: _markerTooltip(context, m),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              _legend(context),
              const SizedBox(height: 4),
              for (final s in sessions.take(maxRows))
                _sessionRow(context, s),
              if (sessions.length > maxRows)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    l10n.analyticsMoreRows(sessions.length - maxRows),
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

  Widget _summaryChip(String label) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.green.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: const TextStyle(
            color: Colors.green,
            fontWeight: FontWeight.w600,
            fontSize: 12),
      ),
    );
  }

  Widget _legend(BuildContext context) {
    final l10n = context.l10n;
    final text = Theme.of(context).textTheme;
    return Row(
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(
            color: Colors.green,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
        const SizedBox(width: 4),
        Text(l10n.analyticsLegendOn, style: text.labelSmall),
        const SizedBox(width: 16),
        Icon(Icons.arrow_drop_down, size: 14, color: Colors.grey.shade600),
        const SizedBox(width: 2),
        Text(l10n.analyticsLegendCommand, style: text.labelSmall),
      ],
    );
  }

  Widget _sessionRow(BuildContext context, MachineSession s) {
    final l10n = context.l10n;
    final text = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          Icon(Icons.power_settings_new,
              size: 14,
              color: s.ongoing ? Colors.green : Colors.grey.shade600),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              formatTimeRange(s.startAt, s.endAt),
              style: text.bodySmall,
            ),
          ),
          if (s.ongoing)
            _ongoingChip(l10n.analyticsOngoing)
          else
            Text(
              formatMinutes(l10n, s.durationMinutes),
              style:
                  text.bodySmall?.copyWith(fontWeight: FontWeight.w600),
            ),
        ],
      ),
    );
  }

  Widget _ongoingChip(String label) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.green.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: const TextStyle(
            color: Colors.green,
            fontWeight: FontWeight.w600,
            fontSize: 11),
      ),
    );
  }

  Color _markerColor(String status) {
    switch (status) {
      case 'DONE':
        return Colors.green;
      case 'FAILED':
      case 'TIMEOUT':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  String _markerTooltip(BuildContext context, CommandMarker m) {
    final l10n = context.l10n;
    final type = (m.commandType == 'ON' || m.commandType == 'FENCING_ON')
        ? l10n.commandTurnOn
        : (m.commandType == 'OFF' || m.commandType == 'FENCING_OFF')
            ? l10n.commandTurnOff
            : m.commandType;
    final time = DateFormat('dd/MM HH:mm').format(m.issuedAt);
    return '$type · ${m.status} · $time';
  }
}
