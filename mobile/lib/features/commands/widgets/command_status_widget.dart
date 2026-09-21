import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/command.dart';

/// Command status widget — shows the status of the last sent command.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is
/// received. This widget reflects the command lifecycle states
/// (pending, acknowledged, done, failed) without ever claiming success
/// before acknowledgement.
class CommandStatusWidget extends ConsumerWidget {
  final String machineId;

  const CommandStatusWidget({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final state = ref.watch(commandProvider);

    // Treat a command from another machine as no command at all.
    final lastCommand = state.lastCommand;
    final cmd = (lastCommand != null && lastCommand.machineId == machineId)
        ? lastCommand
        : null;

    if (cmd == null) {
      return AppSurfaceCard(
        child: AppStatePanel.empty(
          title: l10n.noRecentCommands,
          message: l10n.commandsEmptyMessage,
          icon: Icons.history,
        ),
      );
    }

    return AppSurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          AppSectionHeader(title: l10n.commandStatusSection),
          const SizedBox(height: 8),
          AppMetadataRow(
              label: l10n.detailCommand,
              value: cmd.commandType), // always-en: raw command type code
          AppMetadataRow(label: l10n.detailStatus, value: _statusLabel(l10n, cmd)),
          AppMetadataRow(
            label: l10n.detailSent,
            value: relativeTime(l10n, cmd.createdAt),
          ),
          if (cmd.lastError != null)
            AppMetadataRow(label: l10n.detailError, value: cmd.lastError),
          const SizedBox(height: 8),
          AppStatusBadge(
            label: _statusLabel(l10n, cmd),
            tone: _tone(cmd),
            dot: true,
          ),
        ],
      ),
    );
  }

  String _statusLabel(AppLocalizations l10n, Command c) {
    if (c.isPending) return l10n.statusAwaitingAck;
    if (c.isAcked) return l10n.statusAcknowledged;
    if (c.isDone) return l10n.statusDone;
    if (c.isFailed) return l10n.statusFailed;
    if (c.isTimeout) return l10n.statusTimedOut;
    return c.status; // always-en: raw command status code
  }

  StatusTone _tone(Command c) {
    if (c.isPending) return StatusTone.warning;
    if (c.isAcked) return StatusTone.info;
    if (c.isDone) return StatusTone.success;
    if (c.isFailed) return StatusTone.danger;
    if (c.isTimeout) return StatusTone.danger;
    return StatusTone.neutral;
  }
}
