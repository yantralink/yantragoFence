import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/features/commands/widgets/command_row.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Command History page — all ON/OFF commands across every machine in
/// the tenant, newest first, paged with Load more. Reachable from the
/// Profile settings menu (moved off the machine details screen).
class CommandHistoryPage extends ConsumerWidget {
  const CommandHistoryPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final history = ref.watch(commandHistoryProvider);
    // Machine names for row labels — degrade gracefully if the list
    // fails to load (rows just omit the name line).
    final machines = ref.watch(machineListProvider).valueOrNull;
    final names = <String, String>{
      for (final m in machines ?? const []) m.id: m.name,
    };
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.commandHistory)),
      body: history.when(
        loading: () => AppStatePanel.loading(message: l10n.loadingCommands),
        error: (_, __) => AppStatePanel.error(
          message: l10n.commandHistoryLoadFailed,
          onRetry: () =>
              ref.read(commandHistoryProvider.notifier).load(),
        ),
        data: (state) {
          if (state.commands.isEmpty) {
            return AppStatePanel.empty(
              title: l10n.noCommands,
              message: l10n.commandsEmptyMessageAllMachines,
            );
          }
          return RefreshIndicator(
            onRefresh: () =>
                ref.read(commandHistoryProvider.notifier).load(),
            child: AppPageBody(
              scrollable: true,
              safeArea: false,
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  AppSurfaceCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        ...state.commands.map(
                          (c) => Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: CommandRow(
                              command: c,
                              machineName: names[c.machineId],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (state.hasMore) ...<Widget>[
                    const SizedBox(height: 12),
                    Center(
                      child: OutlinedButton(
                        onPressed: state.loadingMore
                            ? null
                            : () => _loadMore(context, ref),
                        child: state.loadingMore
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : Text(l10n.loadMore),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _loadMore(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(commandHistoryProvider.notifier).loadMore();
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(context.l10n.loadMoreFailed)),
        );
      }
    }
  }
}
