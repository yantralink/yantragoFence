import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/models/command.dart';

/// Command history section — shows recent ON/OFF commands for a machine.
///
/// Displays the last 10 commands with type, status badge, timestamp, and
/// error (if any). Follows the same visual pattern as the Faults section.
///
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
class CommandHistorySection extends ConsumerWidget {
  final String machineId;

  const CommandHistorySection({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final commands = ref.watch(machineCommandsProvider(machineId));

    return commands.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(
          child: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ),
      error: (_, __) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(
          'Unable to load command history. Pull down to refresh.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ),
      data: (list) {
        if (list.isEmpty) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: <Widget>[
                Icon(
                  Icons.history,
                  size: 18,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                Flexible(
                  child: Text(
                    'No command history yet',
                    style: Theme.of(context).textTheme.bodySmall,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            AppSurfaceCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  ...list.map((c) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: _CommandRow(command: c),
                      )),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

class _CommandRow extends StatelessWidget {
  final Command command;

  const _CommandRow({required this.command});

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
    return c.status;
  }

  StatusTone _tone(Command c) {
    if (c.isPending) return StatusTone.warning;
    if (c.isAcked) return StatusTone.info;
    if (c.isDone) return StatusTone.success;
    if (c.isFailed) return StatusTone.danger;
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
