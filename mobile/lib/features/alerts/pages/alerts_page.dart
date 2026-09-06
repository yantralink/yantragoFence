import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/features/alerts/providers/alerts_provider.dart';

/// Alerts page — shows all alerts for the organization.
class AlertsPage extends ConsumerWidget {
  const AlertsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final alerts = ref.watch(alertsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Alerts')),
      body: alerts.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error: $err')),
        data: (list) => RefreshIndicator(
          onRefresh: () => ref.refresh(alertsProvider.future),
          child: list.isEmpty
              ? const Center(child: Text('No alerts'))
              : ListView.builder(
                  itemCount: list.length,
                  itemBuilder: (context, i) {
                    final alert = list[i];
                    return ListTile(
                      leading: Icon(
                        alert.isCritical ? Icons.error : Icons.warning,
                        color: alert.isCritical ? Colors.red : Colors.orange,
                      ),
                      title: Text(alert.message),
                      subtitle: Text(alert.alertType),
                      trailing: Text(alert.severity),
                    );
                  },
                ),
        ),
      ),
    );
  }
}
