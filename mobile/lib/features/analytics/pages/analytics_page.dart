import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/analytics/providers/analytics_provider.dart';
import 'package:yantrago/features/analytics/widgets/battery_health_card.dart';
import 'package:yantrago/features/analytics/widgets/machine_selector.dart';
import 'package:yantrago/features/analytics/widgets/range_chips.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Analytics tab — historical graphs for the selected machine.
///
/// Phase 2 scope: External Battery Health card. Fence Fault and
/// Machine Activity cards land in Phase 3.
class AnalyticsPage extends ConsumerWidget {
  const AnalyticsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final machines = ref.watch(machineListProvider);
    final machineId = ref.watch(effectiveAnalyticsMachineProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.analyticsTitle)),
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
            onRefresh: () => ref.refresh(telemetrySeriesProvider.future),
            child: AppPageBody(
              scrollable: true,
              safeArea: false,
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const AnalyticsMachineSelector(),
                  const SizedBox(height: 12),
                  const AnalyticsRangeChips(),
                  const SizedBox(height: 16),
                  if (machineId == null)
                    AppStatePanel.empty(
                      title: l10n.selectMachine,
                      message: l10n.selectMachineMessage,
                      icon: Icons.precision_manufacturing_outlined,
                    )
                  else
                    const _BatterySection(),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _BatterySection extends ConsumerWidget {
  const _BatterySection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final series = ref.watch(telemetrySeriesProvider);

    return series.when(
      loading: () => const Card(
        child: SizedBox(
          height: 200,
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      error: (_, __) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: AppStatePanel.error(
            message: l10n.errorSomethingWentWrong,
            onRetry: () => ref.refresh(telemetrySeriesProvider.future),
          ),
        ),
      ),
      data: (data) => BatteryHealthCard(voltage: data.voltage),
    );
  }
}
