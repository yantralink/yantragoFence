import 'package:flutter/material.dart';

/// Recharge status widget — displays charging status.
class RechargeStatusWidget extends StatelessWidget {
  final bool? charging;

  const RechargeStatusWidget({super.key, this.charging});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(
              charging == true ? Icons.battery_charging_full : Icons.battery_alert,
              size: 32,
              color: charging == true ? Colors.green : Colors.grey,
            ),
            const SizedBox(height: 8),
            Text('Recharge', style: Theme.of(context).textTheme.bodySmall),
            Text(
              charging == true ? 'Charging' : charging == false ? 'Not Charging' : '--',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ],
        ),
      ),
    );
  }
}
