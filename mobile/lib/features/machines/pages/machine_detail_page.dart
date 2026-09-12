import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_section_header.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/alerts/providers/alerts_provider.dart';
import 'package:yantrago/features/alerts/widgets/alert_card.dart';
import 'package:yantrago/features/commands/widgets/command_status_widget.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_telemetry_provider.dart';
import 'package:yantrago/features/machines/widgets/location_card.dart';
import 'package:yantrago/features/machines/widgets/machine_info_card.dart';
import 'package:yantrago/features/machines/widgets/machine_telemetry_grid.dart';
import 'package:yantrago/features/machines/widgets/on_off_button.dart';
import 'package:go_router/go_router.dart';

/// Machine detail page — shows machine info, location, telemetry, and
/// ON/OFF controls.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is
/// received. The ON/OFF buttons request actions; confirmation comes from
/// the device.
///
/// Layout priority: the Turn ON / Turn OFF controls must be visible in
/// the initial viewport without scrolling. Telemetry uses a 2-column grid,
/// the info card uses a compact 2-column metadata layout, and inter-section
/// spacing is minimized. Command Status may require scrolling.
class MachineDetailPage extends ConsumerWidget {
  final String machineId;

  const MachineDetailPage({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final machine = ref.watch(machineDetailProvider(machineId));

    return Scaffold(
      appBar: AppBar(
        title: machine.maybeWhen(
          data: (m) => Text(m.name),
          orElse: () => const Text('Machine Details'),
        ),
      ),
      body: machine.when(
        loading: () => AppStatePanel.loading(message: 'Loading machine…'),
        error: (_, __) => AppStatePanel.error(
          message: 'Unable to load this machine. Please try again.',
        ),
        data: (m) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(machineDetailProvider(machineId));
            ref.invalidate(machineTelemetryProvider(machineId));
            ref.invalidate(machineAlertsProvider(machineId));
            await Future.wait([
              ref.read(machineDetailProvider(machineId).future),
              ref.read(machineTelemetryProvider(machineId).future),
              ref.read(machineAlertsProvider(machineId).future),
            ]);
          },
          child: AppPageBody(
            scrollable: true,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            safeArea: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                MachineInfoCard(machine: m),
                const SizedBox(height: 8),
                LocationCard(machineId: m.id),
                const AppSectionHeader(title: 'Telemetry', compact: true),
                MachineTelemetryGrid(machine: m),
                const AppSectionHeader(title: 'Controls', compact: true),
                OnOffButton(machineId: m.id, imei: m.imei ?? ''),
                const SizedBox(height: 12),
                const CommandStatusWidget(),
                const SizedBox(height: 8),
                Center(
                  child: Text(
                    'Machine state changes only after device confirmation.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
                const SizedBox(height: 16),
                const AppSectionHeader(title: 'Faults', compact: true),
                _FaultsSection(machineId: m.id),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Faults section — shows alerts for this machine.
///
/// Fetches alerts via [machineAlertsProvider] (filtered by machineId).
/// Shows a compact list of AlertCards, or an empty state when there are
/// no active faults.
class _FaultsSection extends ConsumerWidget {
  final String machineId;

  const _FaultsSection({required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final alerts = ref.watch(machineAlertsProvider(machineId));

    return alerts.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(
          child: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ),
      error: (_, __) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(
          'Unable to load faults. Pull down to refresh.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ),
      data: (list) {
        if (list.isEmpty) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: <Widget>[
                Icon(
                  Icons.check_circle_outline,
                  size: 18,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                Flexible(
                  child: Text(
                    'No active faults',
                    style: Theme.of(context).textTheme.bodySmall,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            ...list.map((a) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: AlertCard(
                    alert: a,
                    onTap: () => context.push('/app/alerts/${a.id}'),
                  ),
                )),
          ],
        );
      },
    );
  }
}
