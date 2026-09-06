import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';

/// ON/OFF button — sends fencing ON/OFF commands to a machine.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
/// Per AGENTS.md rule 6: all commands must be auditable.
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
          SnackBar(content: Text('$commandType command sent. Waiting for ACK...')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to send command: $e'),
              backgroundColor: Theme.of(context).colorScheme.error),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: FilledButton.icon(
            onPressed: _sending ? null : () => _sendCommand('FENCING_ON'),
            icon: const Icon(Icons.power_settings_new),
            label: const Text('Turn ON'),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: FilledButton.icon(
            onPressed: _sending ? null : () => _sendCommand('FENCING_OFF'),
            icon: const Icon(Icons.power_off),
            label: const Text('Turn OFF'),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
          ),
        ),
      ],
    );
  }
}
