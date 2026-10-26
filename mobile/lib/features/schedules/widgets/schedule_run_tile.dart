import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/features/schedules/models/machine_schedule.dart';
import 'package:yantrago/features/schedules/widgets/schedule_run_status.dart';
import 'package:yantrago/l10n/l10n.dart';

/// One row in the schedule run history — slot time, direction, status and
/// error. Mirrors CommandRow's layout.
class ScheduleRunTile extends StatelessWidget {
  final ScheduleRun run;

  const ScheduleRunTile({super.key, required this.run});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final text = Theme.of(context).textTheme;
    final colors = Theme.of(context).colorScheme;

    return Row(
      children: <Widget>[
        Icon(
          run.commandType == 'ON' ? Icons.power_settings_new : Icons.power_off,
          size: 20,
          color: colors.onSurfaceVariant,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                run.commandType == 'ON'
                    ? l10n.commandTurnOn
                    : l10n.commandTurnOff,
                style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
              ),
              if (run.error != null)
                Text(
                  run.error!,
                  style: text.bodySmall?.copyWith(color: colors.error),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              Text(
                DateFormat.yMMMd().add_jm().format(run.scheduledSlot),
                style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
              ),
            ],
          ),
        ),
        AppStatusBadge(
          label: scheduleRunLabel(l10n, run),
          tone: scheduleRunTone(run),
          dot: true,
        ),
      ],
    );
  }
}
