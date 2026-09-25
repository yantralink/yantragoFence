import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/dashboard/providers/dashboard_provider.dart';
import 'package:yantrago/features/dashboard/widgets/battery_widget.dart';
import 'package:yantrago/features/dashboard/widgets/faults_widget.dart';
import 'package:yantrago/features/dashboard/widgets/gsm_status_widget.dart';
import 'package:yantrago/features/dashboard/widgets/machine_status_card.dart';
import 'package:yantrago/features/dashboard/widgets/voltage_widget.dart';
import 'package:yantrago/features/machines/widgets/machine_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Dashboard page — main overview of all machines.
///
/// Composes shared layout patterns and the refactored metric widgets.
/// Per AGENTS.md: no placeholder production data; null telemetry is shown
/// as unavailable, not zero.
class DashboardPage extends ConsumerWidget {
  const DashboardPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summary = ref.watch(dashboardProvider);
    final user = ref.watch(currentUserProvider);
    final l10n = context.l10n;

    return Scaffold(
      // always-en: organization display name / brand fallback
      appBar: AppBar(title: Text(user?.organizationName ?? 'YantraGO')),
      body: summary.when(
        loading: () => AppStatePanel.loading(message: l10n.loadingDashboard),
        error: (_, __) => AppStatePanel.error(
          message: l10n.dashboardLoadFailed,
          onRetry: () => ref.refresh(dashboardProvider.future),
        ),
        data: (data) => RefreshIndicator(
          onRefresh: () => ref.refresh(dashboardProvider.future),
          child: AppPageBody(
            scrollable: true,
            safeArea: false,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                AppSectionHeader(
                    title: l10n.farmOverview,
                    aside: l10n.statusLatestSample),
                MachineStatusCard(
                  total: data.totalMachines,
                  online: data.onlineMachines,
                  fencingOn: data.activeMachines,
                  fault: data.faultMachines,
                  offline: data.offlineMachines,
                ),
                const SizedBox(height: 16),
                AppSectionHeader(
                  title: l10n.telemetrySection,
                  icon: Icons.monitor_heart,
                ),
                _MetricGrid(
                  children: <Widget>[
                    BatteryWidget(
                      battery: data.machines.isNotEmpty
                          ? data.machines.first.batteryPct
                          : null,
                      charging: data.machines.isNotEmpty
                          ? data.machines.first.charging
                          : null,
                    ),
                    VoltageWidget(
                      voltage: data.machines.isNotEmpty
                          ? data.machines.first.voltage
                          : null,
                    ),
                    GsmStatusWidget(
                      signal: data.machines.isNotEmpty
                          ? data.machines.first.gsmSignal
                          : null,
                    ),
                    FaultsWidget(
                        batteryPct: data.machines.isNotEmpty
                            ? data.machines.first.batteryPct
                            : null),
                  ],
                ),
                AppSectionHeader(title: l10n.yourMachines),
                ...data.machines.take(5).map((m) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: MachineCard(
                        machine: m,
                        onTap: () => context.push('/app/machines/${m.id}'),
                      ),
                    )),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Responsive metric grid — two columns, stacks to one on narrow widths.
class _MetricGrid extends StatelessWidget {
  final List<Widget> children;
  const _MetricGrid({required this.children});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final bool narrow = constraints.maxWidth < 320 ||
            MediaQuery.textScalerOf(context).scale(14) > 18;
        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: children
              .map((c) => SizedBox(
                    width: narrow
                        ? double.infinity
                        : (constraints.maxWidth - 12) / 2,
                    child: c,
                  ))
              .toList(),
        );
      },
    );
  }
}
