import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/history/providers/history_provider.dart';
import 'package:yantrago/models/command.dart';

/// Activity history page — shows command history for a machine.
///
/// Per AGENTS.md rule 6: all commands must be auditable. This page
/// renders the audit trail without fabricating data.
class ActivityHistoryPage extends ConsumerWidget {
  final String? machineId;

  const ActivityHistoryPage({super.key, this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: const Text('Activity History')),
      body: machineId == null
          ? AppStatePanel.empty(
              title: 'Select a machine',
              message: 'Choose a machine to view its command history.',
              icon: Icons.history,
            )
          : _HistoryBody(machineId: machineId!),
    );
  }
}

class _HistoryBody extends ConsumerWidget {
  final String machineId;

  const _HistoryBody({required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final history = ref.watch(historyProvider(machineId));

    return history.when(
      loading: () => AppStatePanel.loading(message: 'Loading history…'),
      error: (_, __) => AppStatePanel.error(
        message: 'Unable to load history. Please try again.',
        onRetry: () => ref.refresh(historyProvider(machineId).future),
      ),
      data: (list) {
        if (list.isEmpty) {
          return AppStatePanel.empty(
            title: 'No activity history',
            message: 'Commands sent to this machine will appear here.',
            icon: Icons.history,
            onRetry: () => ref.refresh(historyProvider(machineId).future),
          );
        }
        return AppPageBody(
          scrollable: true,
          safeArea: false,
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              AppSectionHeader(
                title: 'Command History',
                aside: '${list.length} total',
              ),
              ...list.map((c) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: _CommandHistoryItem(cmd: c),
                  )),
            ],
          ),
        );
      },
    );
  }
}

class _CommandHistoryItem extends StatelessWidget {
  final Command cmd;

  const _CommandHistoryItem({required this.cmd});

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;
    final StatusTone tone = _tone(cmd);
    final IconData icon = cmd.commandType == 'FENCING_ON'
        ? Icons.power_settings_new
        : Icons.power_off;

    return AppSurfaceCard(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _IconBox(tone: tone, icon: icon),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        cmd.commandType,
                        style: text.titleSmall,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 8),
                    AppStatusBadge(
                      label: _statusLabel(cmd),
                      tone: tone,
                      dot: true,
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  AppDateUtils.formatDateTime(cmd.createdAt),
                  style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
                ),
                if (cmd.lastError != null) ...<Widget>[
                  const SizedBox(height: 4),
                  Text(
                    cmd.lastError!,
                    style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _statusLabel(Command c) {
    if (c.isPending) return 'Awaiting ACK';
    if (c.isAcked) return 'Acknowledged';
    if (c.isDone) return 'Done';
    if (c.isFailed) return 'Failed';
    return c.status;
  }

  StatusTone _tone(Command c) {
    if (c.isPending) return StatusTone.warning;
    if (c.isAcked) return StatusTone.info;
    if (c.isDone) return StatusTone.success;
    if (c.isFailed) return StatusTone.danger;
    return StatusTone.neutral;
  }
}

class _IconBox extends StatelessWidget {
  final StatusTone tone;
  final IconData icon;
  const _IconBox({required this.tone, required this.icon});

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final Color foreground = semantic?.tone(tone) ?? Theme.of(context).colorScheme.primary;
    final Color background = semantic?.toneSoft(tone) ?? foreground.withValues(alpha: 0.12);
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Icon(icon, size: 22, color: foreground),
    );
  }
}
