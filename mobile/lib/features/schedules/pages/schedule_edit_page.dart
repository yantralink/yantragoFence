import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/schedules/models/machine_schedule.dart';
import 'package:yantrago/features/schedules/providers/schedule_provider.dart';
import 'package:yantrago/features/schedules/widgets/day_of_week_selector.dart';
import 'package:yantrago/features/schedules/widgets/schedule_run_history.dart';
import 'package:yantrago/features/schedules/widgets/schedule_time_field.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Schedule editor — /app/machines/:id/schedule.
///
/// Configure the automatic ON and OFF times plus the days the schedule
/// repeats on (days select the ON slot; the paired OFF fires the next
/// morning when it crosses midnight — the "+1 day" chip).
///
/// Safety note shown on the page: scheduled commands run on the server and
/// only count as executed once the device confirms (AGENTS.md rule 5).
class ScheduleEditPage extends ConsumerStatefulWidget {
  final String machineId;

  const ScheduleEditPage({super.key, required this.machineId});

  @override
  ConsumerState<ScheduleEditPage> createState() => _ScheduleEditPageState();
}

class _ScheduleEditPageState extends ConsumerState<ScheduleEditPage> {
  static const _defaultOn = TimeOfDay(hour: 18, minute: 0);
  static const _defaultOff = TimeOfDay(hour: 7, minute: 0);
  static const _defaultTz = 'Asia/Kolkata';

  TimeOfDay _onTime = _defaultOn;
  TimeOfDay _offTime = _defaultOff;
  int _daysMask = 127;
  String _timezone = _defaultTz;
  bool _initialized = false;
  bool _saving = false;

  bool get _crossesMidnight =>
      (_offTime.hour * 60 + _offTime.minute) <=
      (_onTime.hour * 60 + _onTime.minute);

  /// Hydrates local form state once the provider resolves. A null schedule
  /// means "not configured" — defaults stay; a non-null schedule populates
  /// the form exactly once so later edits aren't clobbered.
  void _initFrom(AsyncValue<MachineSchedule?> async) {
    if (_initialized || !async.hasValue) return;
    final existing = async.value;
    if (existing != null) {
      _onTime = existing.onTime;
      _offTime = existing.offTime;
      _daysMask = existing.daysOfWeek;
      _timezone = existing.timezone;
    }
    _initialized = true;
  }

  Future<void> _pickTime(bool isOn) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: isOn ? _onTime : _offTime,
    );
    if (picked == null) return;
    setState(() => isOn ? _onTime = picked : _offTime = picked);
  }

  Future<void> _save(MachineSchedule? existing) async {
    final l10n = context.l10n;
    setState(() => _saving = true);
    try {
      final draft = (existing ??
              MachineSchedule(
                id: '',
                machineId: widget.machineId,
                onTime: _onTime,
                offTime: _offTime,
                daysOfWeek: _daysMask,
                timezone: _timezone,
                enabled: true,
              ))
          .copyWith(
        onTime: _onTime,
        offTime: _offTime,
        daysOfWeek: _daysMask,
        timezone: _timezone,
      );
      await ref
          .read(scheduleActionsProvider)
          .upsert(widget.machineId, draft);
      ref.invalidate(machineScheduleProvider(widget.machineId));
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(l10n.scheduleSaved)));
        context.pop();
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(l10n.scheduleSaveFailed)));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _delete() async {
    final l10n = context.l10n;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.scheduleDelete),
        content: Text(l10n.scheduleDeleteConfirm),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text(MaterialLocalizations.of(ctx).cancelButtonLabel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text(l10n.scheduleDelete),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(scheduleActionsProvider).delete(widget.machineId);
      ref.invalidate(machineScheduleProvider(widget.machineId));
      if (mounted) context.pop();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(l10n.scheduleDeleteFailed)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final scheduleAsync =
        ref.watch(machineScheduleProvider(widget.machineId));
    _initFrom(scheduleAsync);
    final existing = scheduleAsync.valueOrNull;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.scheduleAutoTitle)),
      body: scheduleAsync.isLoading
          ? AppStatePanel.loading(message: l10n.scheduleAutoTitle)
          : _form(context, l10n, existing),
    );
  }

  Widget _form(BuildContext context, AppLocalizations l10n,
      MachineSchedule? existing) {
    final text = Theme.of(context).textTheme;
    final colors = Theme.of(context).colorScheme;

    return AppPageBody(
      scrollable: true,
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          ScheduleTimeField(
            label: l10n.scheduleTurnOnAt,
            time: _onTime,
            onTap: () => _pickTime(true),
          ),
          const SizedBox(height: 12),
          ScheduleTimeField(
            label: l10n.scheduleTurnOffAt,
            time: _offTime,
            onTap: () => _pickTime(false),
            showNextDayChip: _crossesMidnight,
            nextDayLabel: l10n.schedulePlusOneDay,
          ),
          const SizedBox(height: 20),
          Text(l10n.scheduleRepeatDays,
              style: text.titleSmall?.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          DayOfWeekSelector(
            daysMask: _daysMask,
            onChanged: (mask) => setState(() => _daysMask = mask),
          ),
          const SizedBox(height: 16),
          Text(
            l10n.scheduleTimezoneLabel(_timezone),
            style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
          ),
          const SizedBox(height: 12),
          AppSurfaceCard(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Icon(Icons.info_outline, size: 18, color: colors.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(l10n.scheduleInfoNote, style: text.bodySmall),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _saving ? null : () => _save(existing),
            child: _saving
                ? const SizedBox(
                    height: 18,
                    width: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(l10n.scheduleSave),
          ),
          if (existing != null) ...[
            const SizedBox(height: 8),
            TextButton.icon(
              icon: Icon(Icons.delete_outline, color: colors.error),
              label: Text(l10n.scheduleDelete,
                  style: TextStyle(color: colors.error)),
              onPressed: _delete,
            ),
            const SizedBox(height: 16),
            ScheduleRunHistory(machineId: widget.machineId),
          ],
        ],
      ),
    );
  }
}
