import 'package:flutter/material.dart';

/// Battery widget — displays battery percentage.
class BatteryWidget extends StatelessWidget {
  final int? battery;

  const BatteryWidget({super.key, this.battery});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.battery_std, size: 32, color: _batteryColor(battery)),
            const SizedBox(height: 8),
            Text('Battery', style: Theme.of(context).textTheme.bodySmall),
            Text(
              battery != null ? '$battery%' : '--',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ],
        ),
      ),
    );
  }

  Color _batteryColor(int? level) {
    if (level == null) return Colors.grey;
    if (level > 60) return Colors.green;
    if (level > 20) return Colors.orange;
    return Colors.red;
  }
}
