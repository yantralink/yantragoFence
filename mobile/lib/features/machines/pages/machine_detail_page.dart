import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_telemetry_provider.dart';
import 'package:yantrago/features/machines/providers/telemetry_socket_provider.dart';
import 'package:yantrago/features/machines/widgets/location_card.dart';
import 'package:yantrago/features/machines/widgets/machine_telemetry_grid.dart';
import 'package:yantrago/features/machines/widgets/on_off_button.dart';
import 'package:yantrago/features/schedules/providers/schedule_provider.dart';
import 'package:yantrago/features/schedules/widgets/schedule_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Machine detail page — shows machine info, location, telemetry, and
/// ON/OFF controls.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is
/// received. The ON/OFF buttons request actions; confirmation comes from
/// the device.
///
/// Layout priority: the ON/OFF controls must be visible in
/// the initial viewport without scrolling. Telemetry uses a 2-column grid,
/// the info card uses a compact 2-column metadata layout, and inter-section
/// spacing is minimized.
class MachineDetailPage extends ConsumerWidget {
  final String machineId;

  const MachineDetailPage({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final machine = ref.watch(machineDetailProvider(machineId));
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(
        title: machine.maybeWhen(
          data: (m) => Text(m.name),
          orElse: () => Text(l10n.machineDetailsTitle),
        ),
      ),
      body: machine.when(
        loading: () => AppStatePanel.loading(message: l10n.loadingMachine),
        error: (_, __) => AppStatePanel.error(
          message: l10n.machineLoadFailed,
        ),
        data: (m) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(machineDetailProvider(machineId));
            ref.invalidate(machineTelemetryProvider(machineId));
            ref.invalidate(machineLocationProvider(machineId));
            ref.invalidate(machineAddressProvider(machineId));
            ref.invalidate(machineScheduleProvider(machineId));
            await Future.wait([
              ref.read(machineDetailProvider(machineId).future),
              ref.read(machineTelemetryProvider(machineId).future),
              ref.read(machineLocationProvider(machineId).future),
            ]);
          },
          child: AppPageBody(
            scrollable: true,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            safeArea: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                // Small chip with the machine's confirmed running state.
                // fencingState is authoritative (Turn ON/OFF drives it);
                // ACC ignition is only a fallback for legacy trackers.
                _MachineRunningCard(
                  fencingState: m.fencingState,
                  ignitionOn:
                      ref.watch(telemetrySocketProvider(m.id))?.ignitionOn ??
                          m.ignitionOn,
                ),
                const SizedBox(height: 8),
                MachineTelemetryGrid(machine: m),
                const SizedBox(height: 16),
                OnOffButton(machineId: m.id, imei: m.imei ?? ''),
                const SizedBox(height: 8),
                ScheduleCard(machineId: m.id),
                const SizedBox(height: 8),
                LocationCard(machineId: m.id),
                const SizedBox(height: 12),
                Center(
                  child: Text(
                    l10n.machineStateConfirmationNote,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Small card with a single chip showing the machine's confirmed
/// running state (ACC). Hidden until the device has reported ignition.
class _MachineRunningCard extends StatelessWidget {
  final String? fencingState;
  final bool? ignitionOn;

  const _MachineRunningCard({this.fencingState, this.ignitionOn});

  @override
  Widget build(BuildContext context) {
    // Fence state wins; ACC only matters when fencingState is unknown.
    final on = fencingState == 'ON'
        ? true
        : fencingState == 'OFF'
            ? false
            : ignitionOn == true
                ? true
                : null;
    if (on == null) return const SizedBox.shrink();
    final l10n = context.l10n;
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 2),
      child: Align(
        alignment: Alignment.centerLeft,
        child: AppStatusBadge(
          label: on ? l10n.machineRunning : l10n.machineStopped,
          tone: on ? StatusTone.success : StatusTone.danger,
          dot: on,
        ),
      ),
    );
  }
}
