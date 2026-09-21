import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/command.dart';

/// A single command history row: status icon, command type, optional
/// machine name (for cross-machine lists), relative timestamp, error,
/// and status badge.
class CommandRow extends StatelessWidget {
  final Command command;
  final String? machineName;

  const CommandRow({super.key, required this.command, this.machineName});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;

    return Row(
      children: <Widget>[
        Icon(_icon(command), size: 20, color: _toneColor(context, command)),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              if (machineName != null)
                Text(
                  machineName!,
                  style: text.bodySmall?.copyWith(
                    color: colors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              Text(
                _typeLabel(l10n, command.commandType),
                style: text.bodyMedium?.copyWith(
                  fontWeight: FontWeight.w500,
                ),
              ),
              if (command.lastError != null)
                Text(
                  command.lastError!,
                  style: text.bodySmall?.copyWith(color: colors.error),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              Text(
                relativeTime(l10n, command.createdAt),
                style: text.bodySmall?.copyWith(
                  color: colors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        AppStatusBadge(
          label: _statusLabel(l10n, command),
          tone: _tone(command),
          dot: true,
        ),
      ],
    );
  }

  IconData _icon(Command c) {
    if (c.commandType == 'ON' || c.commandType == 'FENCING_ON') {
      return Icons.power_settings_new;
    }
    return Icons.power_off;
  }

  String _typeLabel(AppLocalizations l10n, String type) {
    switch (type) {
      case 'ON':
      case 'FENCING_ON':
        return l10n.commandTurnOn;
      case 'OFF':
      case 'FENCING_OFF':
        return l10n.commandTurnOff;
      default:
        return type; // always-en: raw command type code
    }
  }

  String _statusLabel(AppLocalizations l10n, Command c) {
    if (c.isPending) return l10n.statusPending;
    if (c.isAcked) return l10n.statusAck;
    if (c.isDone) return l10n.statusDone;
    if (c.isFailed) return l10n.statusFailed;
    if (c.isTimeout) return l10n.statusTimedOut;
    return c.status; // always-en: raw command status code
  }

  StatusTone _tone(Command c) {
    if (c.isPending) return StatusTone.warning;
    if (c.isAcked) return StatusTone.info;
    if (c.isDone) return StatusTone.success;
    if (c.isFailed || c.isTimeout) return StatusTone.danger;
    return StatusTone.neutral;
  }

  Color _toneColor(BuildContext context, Command c) {
    final semantic = Theme.of(context).extension<AppSemanticColors>();
    return semantic?.tone(_tone(c)) ?? Theme.of(context).colorScheme.primary;
  }
}
