import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/analytics/providers/analytics_provider.dart';
import 'package:yantrago/features/analytics/widgets/analytics_format.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine.dart';

/// At-a-glance strip over the analytics cards: total ON time, fault
/// count, and latest battery voltage — all from the providers the page
/// already fetches. Values render '—' while their provider is loading
/// or failed, so one bad call never blocks the row.
class AnalyticsSummaryHeader extends ConsumerWidget {
  const AnalyticsSummaryHeader({super.key});

  /// The machine currently selected in the analytics dropdown.
  static Machine? selectedMachine(WidgetRef ref) {
    final id = ref.watch(effectiveAnalyticsMachineProvider);
    if (id == null) return null;
    final machines = ref.watch(machineListProvider).valueOrNull;
    if (machines == null) return null;
    for (final m in machines) {
      if (m.id == id) return m;
    }
    return null;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final activity = ref.watch(machineActivityProvider).valueOrNull;
    final faults = ref.watch(faultTimelineProvider).valueOrNull;
    final series = ref.watch(telemetrySeriesProvider).valueOrNull;

    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        child: Row(
          children: [
            _Stat(
              icon: Icons.power_settings_new,
              color: Colors.green,
              value: activity == null
                  ? '—'
                  : formatMinutes(l10n, activity.totalOnMinutes),
              label: l10n.analyticsLabelOn,
            ),
            _Stat(
              icon: Icons.error_outline,
              color: Colors.red,
              value: faults == null ? '—' : '${faults.length}',
              label: l10n.analyticsLabelFaults,
            ),
            _Stat(
              icon: Icons.battery_charging_full,
              color: Colors.blue,
              value: (series == null || series.voltage.isEmpty)
                  ? '—'
                  : l10n.batteryVolts(
                      series.voltage.last.value.toStringAsFixed(1)),
              label: l10n.analyticsLabelBattery,
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String value;
  final String label;

  const _Stat({
    required this.icon,
    required this.color,
    required this.value,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Expanded(
      child: Column(
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(height: 4),
          Text(
            value,
            style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          Text(label,
              style: text.labelSmall,
              maxLines: 1,
              overflow: TextOverflow.ellipsis),
        ],
      ),
    );
  }
}
