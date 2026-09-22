import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_action_button.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// ON/OFF button — sends fencing ON/OFF commands to a machine.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
/// Per AGENTS.md rule 6: all commands must be auditable.
///
/// The ON/OFF colors describe the action being requested, not the resulting
/// machine state. The snackbar confirms the command was sent and is awaiting
/// acknowledgement; it never claims success.
class OnOffButton extends ConsumerStatefulWidget {
  final String machineId;
  final String imei;

  const OnOffButton({
    super.key,
    required this.machineId,
    required this.imei,
  });

  @override
  ConsumerState<OnOffButton> createState() => _OnOffButtonState();
}

class _OnOffButtonState extends ConsumerState<OnOffButton> {
  bool _sending = false;

  Future<void> _sendCommand(String commandType) async {
    setState(() => _sending = true);
    try {
      await ref.read(commandProvider.notifier).sendCommand(
            machineId: widget.machineId,
            imei: widget.imei,
            commandType: commandType,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.commandSentAwaitingAck),
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        final ColorScheme colors = Theme.of(context).colorScheme;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.commandSendFailed),
            backgroundColor: colors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Row(
      children: <Widget>[
        Expanded(
          child: AppActionButton(
            label: l10n.actionTurnOn,
            style: AppActionButtonStyle.on,
            icon: Icons.power_settings_new,
            busy: _sending,
            onPressed: _sending ? null : () => _sendCommand('FENCING_ON'),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: AppActionButton(
            label: l10n.actionTurnOff,
            style: AppActionButtonStyle.off,
            icon: Icons.power_off,
            busy: _sending,
            onPressed: _sending ? null : () => _sendCommand('FENCING_OFF'),
          ),
        ),
      ],
    );
  }
}
