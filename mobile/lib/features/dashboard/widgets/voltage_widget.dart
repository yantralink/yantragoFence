import 'package:flutter/material.dart';

/// Voltage widget — displays current voltage reading.
class VoltageWidget extends StatelessWidget {
  final double? voltage;

  const VoltageWidget({super.key, this.voltage});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.electrical_services, size: 32, color: Colors.blue),
            const SizedBox(height: 8),
            Text('Voltage', style: Theme.of(context).textTheme.bodySmall),
            Text(
              voltage != null ? '${voltage!.toStringAsFixed(2)}V' : '--',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ],
        ),
      ),
    );
  }
}
