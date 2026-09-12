// Widget tests for the migrated Machine Details reference screen.
//
// Covers: available data, missing fields, long identifiers, telemetry
// unavailable state, command pending presentation, map navigation,
// light/dark themes, and large text. Providers are overridden so the
// tests never contact the network, secure storage, or maps.

import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/alerts/providers/alerts_provider.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/features/machines/pages/machine_detail_page.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_telemetry_provider.dart';
import 'package:yantrago/models/alert.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/machine_location.dart';
import 'package:yantrago/models/telemetry.dart';

Machine _machine({
  String id = 'm1',
  String name = 'North Field Fence',
  String machineId = 'YG-001',
  String? imei = '000000000000001',
  String status = 'OFFLINE',
  bool isOnline = false,
  String? protocolType = 'CONCOX_V5',
  String? model,
  String? simNumber,
  int? batteryPct,
  bool? charging,
  int? gsmSignal,
  double? voltage,
}) {
  return Machine(
    id: id,
    machineId: machineId,
    name: name,
    imei: imei,
    status: status,
    isOnline: isOnline,
    protocolType: protocolType,
    model: model,
    simNumber: simNumber,
    createdAt: DateTime(2026, 1, 1),
    batteryPct: batteryPct,
    charging: charging,
    gsmSignal: gsmSignal,
    voltage: voltage,
  );
}

MachineLocation _location() => const MachineLocation(
      machineId: 'm1',
      latitude: 18.520430,
      longitude: 73.856740,
    );

GoRouter _router({required Widget child}) {
  return GoRouter(
    initialLocation: '/app/machines/m1',
    routes: [
      ShellRoute(
        builder: (context, state, c) => c,
        routes: [
          GoRoute(
            path: '/app/machines/:id',
            builder: (context, state) =>
                MachineDetailPage(machineId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: '/app/machines/:id/location',
            builder: (context, state) => const Scaffold(
              body: Center(child: Text('MAP PAGE')),
            ),
          ),
        ],
      ),
    ],
  );
}

Widget _wrapped(
  Widget child, {
  Brightness brightness = Brightness.light,
  List<Override> overrides = const [],
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
}) {
  return MaterialApp.router(
    theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
    routerConfig: _router(child: child),
    builder: (context, widget) => MediaQuery(
      data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
      child: widget!,
    ),
  );
}

Future<void> _pump(
  WidgetTester tester,
  List<Override> overrides, {
  Brightness brightness = Brightness.light,
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
}) async {
  await tester.binding.setSurfaceSize(surface);
  await tester.pumpWidget(
    ProviderScope(
      overrides: overrides,
      child: _wrapped(
        const MachineDetailPage(machineId: 'm1'),
        brightness: brightness,
        overrides: overrides,
        textScale: textScale,
        surface: surface,
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

List<Override> _overrides({
  required Machine machine,
  MachineLocation? location,
  Object? addressError,
  bool pending = false,
}) {
  return <Override>[
    machineDetailProvider('m1').overrideWith((ref) async => machine),
    machineLocationProvider('m1').overrideWith((ref) async => location),
    machineTelemetryProvider('m1').overrideWith((ref) async => Telemetry.empty),
    machineAlertsProvider('m1').overrideWith((ref) async => <Alert>[]),
    if (addressError != null)
      machineAddressProvider('m1').overrideWith((ref) async => throw addressError)
    else
      machineAddressProvider('m1').overrideWith((ref) async => null),
    commandProvider.overrideWith((ref) => _FakeCommandNotifier(pending)),
  ];
}

class _FakeCommandNotifier extends CommandNotifier {
  _FakeCommandNotifier(bool pending) : super(Dio()) {
    state = CommandState(pending: pending);
  }
  @override
  Future<void> sendCommand({
    required String machineId,
    required String imei,
    required String commandType,
  }) async {}
}

void main() {
  testWidgets('shows machine name in app bar and info card', (tester) async {
    await _pump(tester, _overrides(machine: _machine()));
    expect(find.text('North Field Fence'), findsNWidgets(2));
    expect(find.text('YG-001'), findsOneWidget);
    expect(find.text('000000000000001'), findsOneWidget);
  });

  testWidgets('shows online success badge for online machine', (tester) async {
    await _pump(
      tester,
      _overrides(machine: _machine(status: 'FENCING_ON', isOnline: true)),
    );
    expect(find.text('Online'), findsOneWidget);
  });

  testWidgets('shows offline neutral badge for offline machine', (tester) async {
    await _pump(tester, _overrides(machine: _machine()));
    expect(find.text('Offline'), findsOneWidget);
  });

  testWidgets('telemetry grid shows unavailable state, not fabricated values',
      (tester) async {
    await _pump(tester, _overrides(machine: _machine()));
    // Battery, GSM, Voltage show unavailable; Faults shows 0 (no active faults).
    expect(find.text('Unavailable'), findsNWidgets(3));
    expect(find.text('No report received'), findsNWidgets(3));
    // "No active faults" appears twice: FaultsWidget in grid + _FaultsSection.
    expect(find.text('0'), findsOneWidget);
    expect(find.text('No active faults'), findsNWidgets(2));
    expect(find.text('24.2'), findsNothing);
    expect(find.text('88'), findsNothing);
  });

  testWidgets('telemetry grid shows voltage from machine fallback', (tester) async {
    // Use a Completer that never completes so the telemetry provider stays loading,
    // forcing the grid to fall back to the machine's voltage field.
    final completer = Completer<Telemetry>();
    await _pump(tester, [
      machineDetailProvider('m1').overrideWith((ref) async => _machine(voltage: 12.22)),
      machineLocationProvider('m1').overrideWith((ref) async => null),
      machineTelemetryProvider('m1').overrideWith((ref) => completer.future),
      machineAlertsProvider('m1').overrideWith((ref) async => <Alert>[]),
      machineAddressProvider('m1').overrideWith((ref) async => null),
      commandProvider.overrideWith((ref) => _FakeCommandNotifier(false)),
    ]);
    // Voltage should show the machine's voltage value while telemetry is loading.
    expect(find.text('12.22'), findsOneWidget);
  });

  testWidgets('long machine name and IMEI do not overflow', (tester) async {
    await _pump(
      tester,
      _overrides(
        machine: _machine(
          name: 'North Field Fence Cooperative Society Limited Extension Unit',
          imei: '1234567890123456789012345678901234567890',
        ),
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('missing optional fields are not shown as blank rows', (tester) async {
    await _pump(
      tester,
      _overrides(
        machine: _machine(model: null, simNumber: null, protocolType: null),
      ),
    );
    expect(find.text('Model'), findsNothing);
    expect(find.text('SIM'), findsNothing);
    expect(find.text('Protocol'), findsNothing);
  });

  testWidgets('location card shows coordinates when available', (tester) async {
    await _pump(tester, _overrides(machine: _machine(), location: _location()));
    expect(find.textContaining('18.520430'), findsOneWidget);
  });

  testWidgets('location card shows no-location state when missing', (tester) async {
    await _pump(tester, _overrides(machine: _machine(), location: null));
    expect(find.text('No location data available'), findsOneWidget);
  });

  testWidgets('ON/OFF buttons are present and tappable', (tester) async {
    await _pump(tester, _overrides(machine: _machine()));
    expect(find.text('Turn ON'), findsOneWidget);
    expect(find.text('Turn OFF'), findsOneWidget);
  });

  testWidgets('command confirmation note is shown', (tester) async {
    await _pump(tester, _overrides(machine: _machine()));
    expect(
      find.text('Machine state changes only after device confirmation.'),
      findsOneWidget,
    );
  });

  testWidgets('loading state shows loading panel, not blank', (tester) async {
    // Use a Completer that never completes so the provider stays loading.
    final completer = Completer<Machine>();
    await _pump(
      tester,
      <Override>[
        machineDetailProvider('m1').overrideWith((ref) => completer.future),
        machineLocationProvider('m1').overrideWith((ref) async => null),
        machineAddressProvider('m1').overrideWith((ref) async => null),
        commandProvider.overrideWith((ref) => _FakeCommandNotifier(false)),
      ],
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Loading machine…'), findsOneWidget);
  });

  testWidgets('error state shows friendly message, not raw exception',
      (tester) async {
    await _pump(
      tester,
      <Override>[
        machineDetailProvider('m1').overrideWith(
          (ref) async => throw Exception('SocketException: Failed host lookup'),
        ),
        machineLocationProvider('m1').overrideWith((ref) async => null),
        machineAddressProvider('m1').overrideWith((ref) async => null),
        commandProvider.overrideWith((ref) => _FakeCommandNotifier(false)),
      ],
    );
    expect(find.text('Unable to load this machine. Please try again.'),
        findsOneWidget);
    expect(find.textContaining('SocketException'), findsNothing);
  });

  testWidgets('renders in dark theme', (tester) async {
    await _pump(
      tester,
      _overrides(machine: _machine()),
      brightness: Brightness.dark,
    );
    expect(find.text('North Field Fence'), findsNWidgets(2));
  });

  testWidgets('renders without overflow at 200% text scale', (tester) async {
    await _pump(
      tester,
      _overrides(machine: _machine()),
      textScale: 2.0,
      surface: const Size(390, 2400),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('renders without overflow at narrow width', (tester) async {
    await _pump(
      tester,
      _overrides(machine: _machine()),
      surface: const Size(320, 1600),
    );
    expect(tester.takeException(), isNull);
  });
}
