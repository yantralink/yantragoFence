import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/features/schedules/models/machine_schedule.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';

/// Shared mapping from schedule-run status to localized label, semantic
/// tone, color and icon — used by both the schedule card's last-run line
/// and the run-history tile (DRY).
String scheduleRunLabel(AppLocalizations l10n, ScheduleRun run) {
  if (run.isSuccess) return l10n.statusDone;
  if (run.isFailed) return l10n.statusFailed;
  if (run.isMissed) return l10n.scheduleRunMissed;
  return l10n.scheduleRunDispatched;
}

StatusTone scheduleRunTone(ScheduleRun run) {
  if (run.isSuccess) return StatusTone.success;
  if (run.isFailed) return StatusTone.danger;
  if (run.isMissed) return StatusTone.warning;
  return StatusTone.info;
}

Color scheduleRunColor(BuildContext context, ScheduleRun run) {
  final semantic = Theme.of(context).extension<AppSemanticColors>();
  return semantic?.tone(scheduleRunTone(run)) ??
      Theme.of(context).colorScheme.primary;
}

IconData scheduleRunIcon(ScheduleRun run) {
  if (run.isSuccess) return Icons.check_circle_outline;
  if (run.isFailed) return Icons.error_outline;
  if (run.isMissed) return Icons.warning_amber_outlined;
  return Icons.schedule;
}
