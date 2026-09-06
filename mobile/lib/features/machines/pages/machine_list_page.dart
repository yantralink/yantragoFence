import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/widgets/machine_card.dart';

/// Machine list page — shows all machines in the organization.
class MachineListPage extends ConsumerWidget {
  const MachineListPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final machines = ref.watch(machineListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Machines')),
      body: machines.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error: $err')),
        data: (list) => RefreshIndicator(
          onRefresh: () => ref.refresh(machineListProvider.future),
          child: ListView.builder(
            itemCount: list.length,
            itemBuilder: (context, i) {
              final m = list[i];
              return MachineCard(
                machine: m,
                onTap: () => context.go('/app/machines/${m.id}'),
              );
            },
          ),
        ),
      ),
    );
  }
}
