import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/features/history/providers/history_provider.dart';
import 'package:yantrago/models/command.dart';

/// Activity history page — shows command history.
class ActivityHistoryPage extends ConsumerWidget {
  final String? machineId;

  const ActivityHistoryPage({super.key, this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: const Text('Activity History')),
      body: machineId == null
          ? const Center(child: Text('Select a machine to view history'))
          : Consumer(
              builder: (context, ref, _) {
                final history = ref.watch(historyProvider(machineId!));
                return history.when(
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (err, _) => Center(child: Text('Error: $err')),
                  data: (list) => list.isEmpty
                      ? const Center(child: Text('No activity history'))
                      : ListView.builder(
                          itemCount: list.length,
                          itemBuilder: (context, i) {
                            final cmd = list[i] as Command;
                            return ListTile(
                              leading: Icon(
                                cmd.commandType == 'FENCING_ON'
                                    ? Icons.power_settings_new
                                    : Icons.power_off,
                                color: cmd.isFailed ? Colors.red : Colors.green,
                              ),
                              title: Text(cmd.commandType),
                              subtitle:
                                  Text(AppDateUtils.formatDateTime(cmd.createdAt)),
                              trailing: Text(cmd.status),
                            );
                          },
                        ),
                );
              },
            ),
    );
  }
}
