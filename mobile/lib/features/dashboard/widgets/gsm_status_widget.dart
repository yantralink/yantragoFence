import 'package:flutter/material.dart';

/// GSM status widget — displays GSM signal strength.
class GsmStatusWidget extends StatelessWidget {
  final int? signal;

  const GsmStatusWidget({super.key, this.signal});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.signal_cellular_alt, size: 32, color: _signalColor(signal)),
            const SizedBox(height: 8),
            Text('GSM Signal', style: Theme.of(context).textTheme.bodySmall),
            Text(
              signal != null ? '$signal dBm' : '--',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ],
        ),
      ),
    );
  }

  Color _signalColor(int? level) {
    if (level == null) return Colors.grey;
    if (level > 20) return Colors.green;
    if (level > 10) return Colors.orange;
    return Colors.red;
  }
}
