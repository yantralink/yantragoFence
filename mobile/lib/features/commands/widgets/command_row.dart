import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
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
                _typeLabel(command.commandType),
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
                _relative(command.createdAt),
                style: text.bodySmall?.copyWith(
                  color: colors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        AppStatusBadge(
          label: _statusLabel(command),
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

  String _typeLabel(String type) {
    switch (type) {
      case 'ON':
      case 'FENCING_ON':
        return 'Turn On';
      case 'OFF':
      case 'FENCING_OFF':
        return 'Turn Off';
      default:
        return type;
    }
  }

  String _statusLabel(Command c) {
    if (c.isPending) return 'Pending';
    if (c.isAcked) return 'Ack';
    if (c.isDone) return 'Done';
    if (c.isFailed) return 'Failed';
    if (c.isTimeout) return 'Timed Out';
    return c.status;
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

  String _relative(DateTime t) {
    final Duration d = DateTime.now().difference(t);
    if (d.inMinutes < 1) return 'just now';
    if (d.inHours < 1) return '${d.inMinutes} min ago';
    if (d.inDays < 1) return '${d.inHours} h ago';
    return '${d.inDays} d ago';
  }
}
