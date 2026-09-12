import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/alerts/providers/alerts_provider.dart';
import 'package:yantrago/features/dashboard/widgets/battery_widget.dart';
import 'package:yantrago/features/dashboard/widgets/faults_widget.dart';
import 'package:yantrago/features/dashboard/widgets/gsm_status_widget.dart';
import 'package:yantrago/features/dashboard/widgets/voltage_widget.dart';
import 'package:yantrago/features/machines/providers/machine_telemetry_provider.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/telemetry.dart';

/// Machine telemetry grid — responsive metric tiles for a machine.
///
/// Feature widget composing shared dashboard metric widgets. Shows a
/// two-column grid that stacks to one column on narrow widths or large text
/// scaling. Displays telemetry from the dedicated telemetry endpoint
/// (GET /api/v1/machines/{id}/telemetry/latest), falling back to the
/// [Machine] model's latest device state (V33 columns) while loading.
///
/// Per the design plan: do not fabricate battery, voltage, faults, or SIM
/// expiry readings that the backend does not provide. Missing telemetry is
/// shown as unavailable, not zero.
class MachineTelemetryGrid extends ConsumerWidget {
  final Machine machine;

  const MachineTelemetryGrid({super.key, required this.machine});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Watch the dedicated telemetry provider for fresh data.
    // Fall back to the Machine model's fields (from the machine detail
    // endpoint) while the telemetry endpoint is loading or on error.
    final telemetryAsync = ref.watch(machineTelemetryProvider(machine.id));
    final alertsAsync = ref.watch(machineAlertsProvider(machine.id));

    final Telemetry telemetry = telemetryAsync.when(
      data: (t) => t,
      loading: () => Telemetry(
        battery: machine.batteryPct,
        charging: machine.charging,
        gsmSignal: machine.gsmSignal,
        voltage: machine.voltage,
      ),
      error: (_, __) => Telemetry(
        battery: machine.batteryPct,
        charging: machine.charging,
        gsmSignal: machine.gsmSignal,
        voltage: machine.voltage,
      ),
    );

    final int? faultsCount = alertsAsync.maybeWhen(
      data: (list) => list.length,
      orElse: () => null,
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        // Stack to one column when narrow or when text scaling is large.
        final bool narrow = constraints.maxWidth < 320 ||
            MediaQuery.textScalerOf(context).scale(14) > 18;
        final crossAxisCount = narrow ? 1 : 2;
        final tiles = _buildTiles(telemetry, faultsCount);
        return _Grid(
          crossAxisCount: crossAxisCount,
          spacing: 8,
          max_width: constraints.maxWidth,
          children: tiles,
        );
      },
    );
  }

  List<Widget> _buildTiles(Telemetry telemetry, int? faultsCount) {
    // Use the reusable dashboard widgets which already handle null states
    // (showing "No report received" when data is unavailable).
    return <Widget>[
      BatteryWidget(battery: telemetry.battery, charging: telemetry.charging),
      FaultsWidget(count: faultsCount),
      GsmStatusWidget(signal: telemetry.gsmSignal),
      VoltageWidget(voltage: telemetry.voltage),
    ];
  }
}

class _Grid extends StatelessWidget {
  final int crossAxisCount;
  final double spacing;
  final double max_width;
  final List<Widget> children;

  const _Grid({
    required this.crossAxisCount,
    required this.spacing,
    required this.max_width,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    final double itemWidth =
        (max_width - (crossAxisCount - 1) * spacing) / crossAxisCount;
    return Wrap(
      spacing: spacing,
      runSpacing: spacing,
      children: children
          .map((c) => SizedBox(width: itemWidth, child: c))
          .toList(),
    );
  }
}
