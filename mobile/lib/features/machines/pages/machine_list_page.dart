import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/widgets/machine_card.dart';

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

    return Scaffold(
      appBar: AppBar(title: Text(user?.organizationName ?? 'YantraGO')),
      body: machines.when(
        loading: () => AppStatePanel.loading(message: 'Loading machines…'),
        error: (_, __) => AppStatePanel.error(
          message: 'Unable to load machines. Please try again.',
          onRetry: () => ref.refresh(machineListProvider.future),
        ),
        data: (list) {
          if (list.isEmpty) {
            return AppStatePanel.empty(
              title: 'No machines assigned yet',
              message: 'Your machines will appear here once your organization assigns them.',
              icon: Icons.precision_manufacturing_outlined,
              onRetry: () => ref.refresh(machineListProvider.future),
            );
          }
          return RefreshIndicator(
            onRefresh: () => ref.refresh(machineListProvider.future),
            child: AppPageBody(
              scrollable: true,
              safeArea: false,
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  AppSectionHeader(
                    title: 'Machines',
                    aside: '${list.length} assigned',
                  ),
                  ...list.map((m) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: MachineCard(
                          machine: m,
                          onTap: () => context.push('/app/machines/${m.id}'),
                        ),
                      )),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
