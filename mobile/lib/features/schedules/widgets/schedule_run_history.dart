import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/schedules/providers/schedule_provider.dart';
import 'package:yantrago/features/schedules/widgets/schedule_run_tile.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Last few schedule execution records — shows the user what the schedule
/// actually did (ACKED/FAILED/MISSED per AGENTS.md rule 5 auditability).
/// Renders nothing while empty or on error — the section is informational.
class ScheduleRunHistory extends ConsumerWidget {
  final String machineId;

  const ScheduleRunHistory({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final runs = ref.watch(scheduleRunsProvider(machineId));

    return runs.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(8),
        child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
      ),
      error: (_, __) => const SizedBox.shrink(),
      data: (list) {
        if (list.isEmpty) return const SizedBox.shrink();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(l10n.scheduleRecentRuns,
                style: Theme.of(context)
                    .textTheme
                    .titleSmall
                    ?.copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            ...list.take(8).map((r) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: ScheduleRunTile(run: r),
                )),
          ],
        );
      },
    );
  }
}
