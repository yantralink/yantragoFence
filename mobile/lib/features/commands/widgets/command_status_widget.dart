import 'package:flutter/material.dart';

/// Command status widget — shows the status of the last sent command.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
class CommandStatusWidget extends StatelessWidget {
  const CommandStatusWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Command Status', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 16),
            const Center(
              child: Text('No recent commands', style: TextStyle(color: Colors.grey)),
            ),
            // TODO: Show last command status with real-time WebSocket updates
          ],
        ),
      ),
    );
  }
}
