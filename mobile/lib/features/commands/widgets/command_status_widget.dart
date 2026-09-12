import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/models/command.dart';

/// Command status widget — shows the status of the last sent command.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is
/// received. This widget reflects the command lifecycle states
/// (pending, acknowledged, done, failed) without ever claiming success
/// before acknowledgement.
class CommandStatusWidget extends ConsumerWidget {
  const CommandStatusWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(commandProvider);

    if (state.lastCommand == null && !state.pending) {
      return AppSurfaceCard(
        child: AppStatePanel.empty(
          title: 'No recent commands',
          message: 'Commands sent to this machine will appear here.',
          icon: Icons.history,
        ),
      );
    }

    final cmd = state.lastCommand;
    if (cmd == null) {
      return AppSurfaceCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const AppSectionHeader(title: 'Command Status'),
            const SizedBox(height: 8),
            _PendingStatus(),
          ],
        ),
      );
    }

    return AppSurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          const AppSectionHeader(title: 'Command Status'),
          const SizedBox(height: 8),
          AppMetadataRow(label: 'Command', value: cmd.commandType),
          AppMetadataRow(label: 'Status', value: _statusLabel(cmd)),
          AppMetadataRow(
            label: 'Sent',
            value: _relative(cmd.createdAt),
          ),
          if (cmd.lastError != null)
            AppMetadataRow(label: 'Error', value: cmd.lastError),
          const SizedBox(height: 8),
          AppStatusBadge(
            label: _statusLabel(cmd),
            tone: _tone(cmd),
            dot: true,
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

  String _relative(DateTime t) {
    final Duration d = DateTime.now().difference(t);
    if (d.inMinutes < 1) return 'just now';
    if (d.inHours < 1) return '${d.inMinutes} min ago';
    if (d.inDays < 1) return '${d.inHours} h ago';
    return '${d.inDays} d ago';
  }
}

class _PendingStatus extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Row(
      children: <Widget>[
        const SizedBox(
          width: 16,
          height: 16,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        const SizedBox(width: 8),
        Text(
          'Awaiting device acknowledgement…',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ],
    );
  }
}
