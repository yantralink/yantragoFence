import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/features/dashboard/providers/dashboard_provider.dart';
import 'package:yantrago/features/dashboard/widgets/machine_status_card.dart';
import 'package:yantrago/features/dashboard/widgets/battery_widget.dart';
import 'package:yantrago/features/dashboard/widgets/voltage_widget.dart';
import 'package:yantrago/features/dashboard/widgets/gsm_status_widget.dart';
import 'package:yantrago/features/dashboard/widgets/recharge_status_widget.dart';

/// Dashboard page — main overview of all machines.
///
/// Scaffold with placeholder UI. Full implementation will show:
/// - Machine status summary cards
/// - Battery, voltage, GSM, recharge widgets
/// - Real-time updates via WebSocket
class DashboardPage extends ConsumerWidget {
  const DashboardPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summary = ref.watch(dashboardProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Dashboard')),
      body: summary.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error: $err')),
        data: (data) => RefreshIndicator(
          onRefresh: () => ref.refresh(dashboardProvider.future),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // Summary cards
              MachineStatusCard(
                total: data.totalMachines,
                online: data.onlineMachines,
                fencingOn: data.activeMachines,
                fault: data.faultMachines,
                offline: data.offlineMachines,
              ),
              const SizedBox(height: 16),

              // Telemetry widgets (placeholder)
              Row(
                children: [
                  Expanded(child: BatteryWidget(battery: null)),
                  const SizedBox(width: 8),
                  Expanded(child: VoltageWidget(voltage: null)),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(child: GsmStatusWidget(signal: null)),
                  const SizedBox(width: 8),
                  Expanded(child: RechargeStatusWidget(charging: null)),
                ],
              ),
              const SizedBox(height: 24),

              // Machine list preview
              Text('Machines', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 8),
              ...data.machines.take(5).map((m) => ListTile(
                    leading: Icon(
                      m.isOnline ? Icons.circle : Icons.circle_outlined,
                      color: m.isOnline ? Colors.green : Colors.grey,
                      size: 12,
                    ),
                    title: Text(m.name),
                    subtitle: Text(m.machineId.isNotEmpty ? m.machineId : (m.imei ?? '')),
                    trailing: Text(m.status),
                  )),
            ],
          ),
        ),
      ),
    );
  }
}
