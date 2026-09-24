import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/dashboard/widgets/battery_widget.dart';
import 'package:yantrago/features/dashboard/widgets/external_battery_widget.dart';
import 'package:yantrago/features/dashboard/widgets/faults_widget.dart';
import 'package:yantrago/features/dashboard/widgets/gsm_status_widget.dart';
import 'package:yantrago/features/dashboard/widgets/ignition_widget.dart';
import 'package:yantrago/features/dashboard/widgets/voltage_widget.dart';
import 'package:yantrago/features/machines/providers/machine_telemetry_provider.dart';
import 'package:yantrago/features/machines/providers/telemetry_socket_provider.dart';
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

    // Live socket updates override the REST snapshot once a frame arrives.
    // Watching this provider also keeps the WebSocket connected while the
    // details screen is mounted.
    final liveTelemetry = ref.watch(telemetrySocketProvider(machine.id));

    // REST snapshot — the base data loaded once per screen visit.
    final Telemetry restTelemetry = telemetryAsync.when(
      data: (t) => t,
      loading: () => Telemetry(
        battery: machine.batteryPct,
        charging: machine.charging,
        gsmSignal: machine.gsmSignal,
        voltage: machine.voltage,
        ignitionOn: machine.ignitionOn,
      ),
      error: (_, __) => Telemetry(
        battery: machine.batteryPct,
        charging: machine.charging,
        gsmSignal: machine.gsmSignal,
        voltage: machine.voltage,
        ignitionOn: machine.ignitionOn,
      ),
    );

    // Merge live socket data with the REST snapshot. GPS packets arrive
    // with null for battery/voltage/gsm/charging/ignition (those fields
    // only come from heartbeat/alarm packets), so keep the REST value
    // for any field the socket frame leaves null.
    //
    // Freshness guard: if the last live frame is older than 5 minutes
    // (dead socket that never delivered a close event, or frames simply
    // stopped arriving), drop it entirely and show the REST snapshot.
    final Telemetry? freshLive = _isFresh(liveTelemetry) ? liveTelemetry : null;
    final Telemetry telemetry = freshLive != null
        ? Telemetry(
            id: freshLive.id ?? restTelemetry.id,
            deviceId: freshLive.deviceId ?? restTelemetry.deviceId,
            imei: freshLive.imei ?? restTelemetry.imei,
            voltage: freshLive.voltage ?? restTelemetry.voltage,
            battery: freshLive.battery ?? restTelemetry.battery,
            gsmSignal: freshLive.gsmSignal ?? restTelemetry.gsmSignal,
            charging: freshLive.charging ?? restTelemetry.charging,
            ignitionOn: freshLive.ignitionOn ?? restTelemetry.ignitionOn,
            timestamp: freshLive.timestamp ?? restTelemetry.timestamp,
            receivedAt: freshLive.receivedAt,
          )
        : restTelemetry;

    return LayoutBuilder(
      builder: (context, constraints) {
        // Stack to one column when narrow or when text scaling is large.
        final bool narrow = constraints.maxWidth < 320 ||
            MediaQuery.textScalerOf(context).scale(14) > 18;
        final crossAxisCount = narrow ? 1 : 2;
        final tiles = _buildTiles(telemetry);
        return _Grid(
          crossAxisCount: crossAxisCount,
          spacing: 8,
          max_width: constraints.maxWidth,
          children: tiles,
        );
      },
    );
  }

  /// A live frame is fresh if it was received within the last 5 minutes.
  /// [Telemetry.receivedAt] is set client-side on each socket frame —
  /// REST snapshots have it null and are never considered "live".
  static bool _isFresh(Telemetry? live) {
    final receivedAt = live?.receivedAt;
    if (live == null || receivedAt == null) return false;
    return DateTime.now().difference(receivedAt).inMinutes < 5;
  }

  List<Widget> _buildTiles(Telemetry telemetry) {
    // Use the reusable dashboard widgets which already handle null states
    // (showing "No report received" when data is unavailable).
    return <Widget>[
      IgnitionWidget(ignitionOn: telemetry.ignitionOn),
      ExternalBatteryWidget(
        voltage: telemetry.voltage,
        charging: telemetry.charging,
      ),
      BatteryWidget(battery: telemetry.battery, charging: telemetry.charging),
      FaultsWidget(batteryPct: telemetry.battery),
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
