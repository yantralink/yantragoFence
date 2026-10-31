import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/widgets/machine_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Machine list page — shows all machines assigned to the current user.
///
/// Per AGENTS.md: organization_id comes from JWT, never from request body.
/// The provider handles tenant isolation; this page only renders results.
class MachineListPage extends ConsumerWidget {
  const MachineListPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final machines = ref.watch(machineListProvider);
    final user = ref.watch(currentUserProvider);
    final l10n = context.l10n;

    return Scaffold(
      // always-en: organization display name / brand fallback
      appBar: AppBar(title: Text(user?.organizationName ?? 'YantraGO')),
      body: machines.when(
        loading: () => AppStatePanel.loading(message: l10n.loadingMachines),
        error: (_, __) => AppStatePanel.error(
          message: l10n.machinesLoadFailed,
          onRetry: () => ref.refresh(machineListProvider.future),
        ),
        data: (list) {
          if (list.isEmpty) {
            return AppStatePanel.empty(
              title: l10n.machinesEmptyTitle,
              message: l10n.machinesEmptyMessage,
              icon: Icons.precision_manufacturing_outlined,
              onRetry: () => ref.refresh(machineListProvider.future),
            );
          }
          return RefreshIndicator(
            onRefresh: () async {
              // Address providers depend on location, so both refresh together.
              ref.invalidate(machineLocationProvider);
              ref.invalidate(machineListProvider);
              await ref.read(machineListProvider.future);
            },
            // Build only nearby cards: adding addresses must not geocode the
            // entire fetched fleet when the first screenful is displayed.
            child: ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.lg,
                AppSpacing.sm,
                AppSpacing.lg,
                AppSpacing.xxl,
              ),
              itemCount: list.length + 1,
              itemBuilder: (context, index) {
                if (index == 0) {
                  return AppSectionHeader(
                    title: l10n.machinesTitle,
                    aside: l10n.machinesAssignedCount(list.length),
                  );
                }
                final machine = list[index - 1];
                return Padding(
                  key: ValueKey(machine.id),
                  padding: const EdgeInsets.only(bottom: AppSpacing.md),
                  child: MachineCard(
                    machine: machine,
                    onTap: () => context.push('/app/machines/${machine.id}'),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
