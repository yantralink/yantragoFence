import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/widgets/machine_card.dart';

/// Machine list page — shows all machines in the organization.
class MachineListPage extends ConsumerWidget {
  const MachineListPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final machines = ref.watch(machineListProvider);
    final user = ref.watch(currentUserProvider);

    return Scaffold(
      appBar: AppBar(title: Text(user?.organizationName ?? 'YantraGO')),
      body: machines.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.red),
              const SizedBox(height: 16),
              Text('Failed to load machines', textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () => ref.refresh(machineListProvider.future),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (list) {
          if (list.isEmpty) {
            return const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.precision_manufacturing_outlined,
                      size: 64, color: Colors.grey),
                  SizedBox(height: 16),
                  Text('No machines assigned yet',
                      style: TextStyle(color: Colors.grey)),
                ],
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref.refresh(machineListProvider.future),
            child: ListView.builder(
              itemCount: list.length,
              itemBuilder: (context, i) {
                final m = list[i];
                return MachineCard(
                  machine: m,
                  onTap: () => context.push('/app/machines/${m.id}'),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
