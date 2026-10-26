import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/schedules/models/machine_schedule.dart';
import 'package:yantrago/features/schedules/providers/schedule_provider.dart';
import 'package:yantrago/features/schedules/widgets/schedule_run_status.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Compact schedule summary on the machine detail page.
///
/// Three states: no schedule (setup CTA), configured (times + days + toggle +
/// next/last run + Edit). The switch is the only configuration changed here —
/// time/day editing lives on the dedicated edit page.
///
/// The displayed times and next-run instants come from the backend; the app
/// never computes schedule execution itself (server is authoritative).
class ScheduleCard extends ConsumerWidget {
  final String machineId;

  const ScheduleCard({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final schedule = ref.watch(machineScheduleProvider(machineId));
    final l10n = context.l10n;

    return schedule.when(
      loading: () => const AppSurfaceCard(
        child: SizedBox(
          height: 48,
          child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
        ),
      ),
      // 403 = user lacks schedule:read (e.g. a JWT issued before the
      // permission was seeded — fixed by re-login/refresh). Hide rather
      // than tease a control they cannot use. Transient errors get a
      // small retry row so a network blip doesn't hide the feature.
      error: (err, _) => _isForbidden(err)
          ? const SizedBox.shrink()
          : _ErrorCard(onRetry: () =>
              ref.invalidate(machineScheduleProvider(machineId))),
      data: (s) => s == null
          ? _EmptyCard(machineId: machineId)
          : _ConfiguredCard(machineId: machineId, schedule: s, l10n: l10n),
    );
  }

  bool _isForbidden(Object err) =>
      err is DioException && err.response?.statusCode == 403;
}

/// Compact retry row shown when the schedule fetch fails transiently —
/// network/5xx errors shouldn't silently hide the feature card.
class _ErrorCard extends StatelessWidget {
  final VoidCallback onRetry;
  const _ErrorCard({required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return AppSurfaceCard(
      child: Row(
        children: <Widget>[
          Icon(Icons.schedule, size: 20,
              color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              context.l10n.scheduleAutoTitle,
              style: Theme.of(context)
                  .textTheme
                  .titleSmall
                  ?.copyWith(fontWeight: FontWeight.w600),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.refresh, size: 20),
            onPressed: onRetry,
          ),
        ],
      ),
    );
  }
}

class _EmptyCard extends StatelessWidget {
  final String machineId;
  const _EmptyCard({required this.machineId});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final text = Theme.of(context).textTheme;

    return AppSurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Icon(Icons.schedule, size: 20,
                  color: Theme.of(context).colorScheme.primary),
              const SizedBox(width: 8),
              Text(l10n.scheduleAutoTitle,
                  style: text.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
            ],
          ),
          const SizedBox(height: 8),
          Text(l10n.scheduleNone, style: text.bodySmall),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: FilledButton.tonalIcon(
              icon: const Icon(Icons.add, size: 18),
              label: Text(l10n.scheduleSetup),
              onPressed: () =>
                  context.push('/app/machines/$machineId/schedule'),
            ),
          ),
        ],
      ),
    );
  }
}

class _ConfiguredCard extends ConsumerWidget {
  final String machineId;
  final MachineSchedule schedule;
  final AppLocalizations l10n;
  const _ConfiguredCard({
    required this.machineId,
    required this.schedule,
    required this.l10n,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final text = Theme.of(context).textTheme;
    final colors = Theme.of(context).colorScheme;
    final localizations = MaterialLocalizations.of(context);

    return AppSurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Icon(Icons.schedule, size: 20, color: colors.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(l10n.scheduleAutoTitle,
                    style: text.titleSmall
                        ?.copyWith(fontWeight: FontWeight.w600)),
              ),
              Switch(
                value: schedule.enabled,
                onChanged: (v) => _toggle(context, ref, v),
              ),
            ],
          ),
          Text(
            '${l10n.commandTurnOn} ${localizations.formatTimeOfDay(schedule.onTime)}'
            '  →  '
            '${l10n.commandTurnOff} ${localizations.formatTimeOfDay(schedule.offTime)}'
            '${schedule.crossesMidnight ? ' ${l10n.schedulePlusOneDay}' : ''}',
            style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 4),
          Text(_daysSummary(), style: text.bodySmall),
          const Divider(height: 16),
          if (schedule.nextOnAt != null)
            Text(l10n.scheduleNextOnAt(_fmtInstant(schedule.nextOnAt!)),
                style: text.bodySmall),
          if (schedule.nextOffAt != null)
            Text(l10n.scheduleNextOffAt(_fmtInstant(schedule.nextOffAt!)),
                style: text.bodySmall),
          if (schedule.lastRun != null) ...[
            const SizedBox(height: 6),
            Row(
              children: <Widget>[
                Icon(scheduleRunIcon(schedule.lastRun!),
                    size: 16,
                    color: scheduleRunColor(context, schedule.lastRun!)),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    l10n.scheduleLastRun(
                        scheduleRunLabel(l10n, schedule.lastRun!)),
                    style: text.bodySmall?.copyWith(
                      color: scheduleRunColor(context, schedule.lastRun!),
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              icon: const Icon(Icons.edit_outlined, size: 18),
              label: Text(l10n.scheduleEdit),
              onPressed: () =>
                  context.push('/app/machines/$machineId/schedule'),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _toggle(BuildContext context, WidgetRef ref, bool value) async {
    try {
      await ref.read(scheduleActionsProvider).setEnabled(machineId, value);
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.scheduleToggleFailed)),
        );
      }
    } finally {
      ref.invalidate(machineScheduleProvider(machineId));
    }
  }

  String _fmtInstant(DateTime instant) =>
      DateFormat.yMMMd().add_jm().format(instant);

  String _daysSummary() {
    if (schedule.isDaily) return l10n.scheduleDaily;
    final formatter = DateFormat.E();
    final days = <String>[
      for (var w = 1; w <= 7; w++)
        if (schedule.isDaySelected(w)) formatter.format(DateTime(2024, 1, w)),
    ];
    return days.join(' ');
  }

}
