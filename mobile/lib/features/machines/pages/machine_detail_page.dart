import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/widgets/on_off_button.dart';
import 'package:yantrago/features/machines/widgets/location_card.dart';
import 'package:yantrago/features/commands/widgets/command_status_widget.dart';

/// Machine detail page — shows machine info and ON/OFF controls.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
class MachineDetailPage extends ConsumerWidget {
  final String machineId;

  const MachineDetailPage({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final machine = ref.watch(machineDetailProvider(machineId));

    return Scaffold(
      appBar: AppBar(title: const Text('Machine Details')),
      body: machine.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error: $err')),
        data: (m) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(m.name, style: Theme.of(context).textTheme.headlineSmall),
                    const SizedBox(height: 8),
                    if (m.machineId.isNotEmpty)
                      _InfoRow(label: 'Machine ID', value: m.machineId),
                    if (m.imei != null) _InfoRow(label: 'IMEI', value: m.imei!),
                    if (m.protocolType != null) _InfoRow(label: 'Protocol', value: m.protocolType!),
                    _InfoRow(label: 'Status', value: m.status),
                    if (m.model != null) _InfoRow(label: 'Model', value: m.model!),
                    if (m.simNumber != null) _InfoRow(label: 'SIM', value: m.simNumber!),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            LocationCard(machineId: m.id),
            const SizedBox(height: 24),
            OnOffButton(machineId: m.id, imei: m.imei ?? ''),
            const SizedBox(height: 24),
            const CommandStatusWidget(),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              )),
          Text(value, style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w500,
              )),
        ],
      ),
    );
  }
}
