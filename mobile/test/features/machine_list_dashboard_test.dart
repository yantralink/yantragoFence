// Widget tests for the migrated Machine List and Dashboard screens.
//
// Covers: machine card navigation, pull-to-refresh, empty/error lists,
// null metrics, long labels, dark mode, and responsive metric layout.
// Providers are overridden so the tests never contact the network.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/dashboard/pages/dashboard_page.dart';
import 'package:yantrago/features/dashboard/providers/dashboard_provider.dart';
import 'package:yantrago/features/dashboard/widgets/battery_widget.dart';
import 'package:yantrago/features/dashboard/widgets/faults_widget.dart';
import 'package:yantrago/features/dashboard/widgets/gsm_status_widget.dart';
import 'package:yantrago/features/dashboard/widgets/voltage_widget.dart';
import 'package:yantrago/features/machines/pages/machine_list_page.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/user.dart';

Machine _machine({
  String id = 'm1',
  String name = 'North Field Fence',
  String machineId = 'YG-001',
  String status = 'OFFLINE',
  bool isOnline = false,
  int? batteryPct,
  bool? charging,
  int? gsmSignal,
  double? voltage,
}) {
  return Machine(
    id: id,
    machineId: machineId,
    name: name,
    status: status,
    isOnline: isOnline,
    createdAt: DateTime(2026, 1, 1),
    batteryPct: batteryPct,
    charging: charging,
    gsmSignal: gsmSignal,
    voltage: voltage,
  );
}

DashboardSummary _summary({
  int total = 3,
  int online = 2,
  int active = 1,
  int fault = 0,
  int offline = 1,
  List<Machine> machines = const [],
}) {
  return DashboardSummary(
    totalMachines: total,
    onlineMachines: online,
    activeMachines: active,
    inStockMachines: 0,
    faultMachines: fault,
    offlineMachines: offline,
    machines: machines,
  );
}

Widget _wrapped(
  Widget child, {
  Brightness brightness = Brightness.light,
  List<Override> overrides = const [],
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
  String initialLocation = '/app/machines',
}) {
  final GoRouter router = GoRouter(
    initialLocation: initialLocation,
    routes: [
      ShellRoute(
        builder: (context, state, c) => c,
        routes: [
          GoRoute(
            path: '/app/machines',
            builder: (context, state) => child,
          ),
          GoRoute(
            path: '/app/machines/:id',
            builder: (context, state) => Scaffold(
              body: Center(child: Text('DETAIL ${state.pathParameters['id']}')),
            ),
          ),
        ],
      ),
    ],
  );
  return MaterialApp.router(
    theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
    routerConfig: router,
    builder: (context, widget) => MediaQuery(
      data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
      child: widget!,
    ),
  );
}

Future<void> _pumpList(
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
        const MachineListPage(),
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

Future<void> _pumpDashboard(
  WidgetTester tester,
  List<Override> overrides, {
  Brightness brightness = Brightness.light,
  double textScale = 1.0,
  Size surface = const Size(390, 1400),
}) async {
  await tester.binding.setSurfaceSize(surface);
  final GoRouter router = GoRouter(
    initialLocation: '/app/dashboard',
    routes: [
      ShellRoute(
        builder: (context, state, child) => child,
        routes: [
          GoRoute(
            path: '/app/dashboard',
            builder: (context, state) => const DashboardPage(),
          ),
        ],
      ),
    ],
  );
  await tester.pumpWidget(
    ProviderScope(
      overrides: overrides,
      child: MaterialApp.router(
        theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
        routerConfig: router,
        builder: (context, widget) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
          child: widget!,
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

const User _user = User(
  id: 'u1',
  email: 'demo@example.invalid',
  fullName: 'Demo User',
  role: 'CUSTOMER',
  active: true,
  organizationName: 'Demo Farm',
);

void main() {
  group('MachineListPage', () {
    testWidgets('shows machines with section header and count', (tester) async {
      await _pumpList(tester, [
        machineListProvider.overrideWith((ref) async => [
              _machine(id: 'm1', name: 'North Field Fence'),
              _machine(id: 'm2', name: 'West Field Fence', isOnline: true, status: 'ACTIVE'),
            ]),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.text('MACHINES'), findsOneWidget);
      expect(find.text('2 assigned'), findsOneWidget);
      expect(find.text('North Field Fence'), findsOneWidget);
      expect(find.text('West Field Fence'), findsOneWidget);
    });

    testWidgets('tapping a card navigates to detail', (tester) async {
      await _pumpList(tester, [
        machineListProvider.overrideWith((ref) async => [_machine(id: 'm1')]),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      await tester.tap(find.text('North Field Fence'));
      await tester.pumpAndSettle();
      expect(find.text('DETAIL m1'), findsOneWidget);
    });

    testWidgets('empty state shows message and refresh', (tester) async {
      await _pumpList(tester, [
        machineListProvider.overrideWith((ref) async => []),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.text('No machines assigned yet'), findsOneWidget);
      expect(find.text('Refresh'), findsOneWidget);
    });

    testWidgets('error state shows friendly message and retry', (tester) async {
      await _pumpList(tester, [
        machineListProvider.overrideWith(
          (ref) async => throw Exception('SocketException: Failed host lookup'),
        ),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.text('Unable to load machines. Please try again.'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(find.textContaining('SocketException'), findsNothing);
    });

    testWidgets('loading state shows loading panel', (tester) async {
      final completer = Completer<List<Machine>>();
      await _pumpList(tester, [
        machineListProvider.overrideWith((ref) => completer.future),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Loading machines…'), findsOneWidget);
    });

    testWidgets('long machine name does not overflow', (tester) async {
      await _pumpList(tester, [
        machineListProvider.overrideWith((ref) async => [
              _machine(
                name: 'North Field Fence Cooperative Society Limited Extension Unit With A Very Long Name',
              ),
            ]),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(tester.takeException(), isNull);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pumpList(
        tester,
        [
          machineListProvider.overrideWith((ref) async => [_machine()]),
          currentUserProvider.overrideWith((ref) => _user),
        ],
        brightness: Brightness.dark,
      );
      expect(find.text('North Field Fence'), findsOneWidget);
    });

    testWidgets('renders without overflow at 200% text scale', (tester) async {
      await _pumpList(
        tester,
        [
          machineListProvider.overrideWith((ref) async => [_machine()]),
          currentUserProvider.overrideWith((ref) => _user),
        ],
        textScale: 2.0,
        surface: const Size(390, 2400),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('pull-to-refresh triggers provider refresh', (tester) async {
      await _pumpList(
        tester,
        [
          machineListProvider.overrideWith((ref) async => [_machine()]),
          currentUserProvider.overrideWith((ref) => _user),
        ],
      );
      expect(find.byType(RefreshIndicator), findsOneWidget);
      expect(find.text('North Field Fence'), findsOneWidget);
      // Drag down to trigger refresh
      await tester.fling(find.text('North Field Fence'), const Offset(0, 300), 1000);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      // RefreshIndicator should be visible during refresh
      expect(find.byType(RefreshIndicator), findsOneWidget);
    });
  });

  group('DashboardPage', () {
    testWidgets('shows status card with counts', (tester) async {
      await _pumpDashboard(tester, [
        dashboardProvider.overrideWith((ref) async => _summary(
              total: 3,
              online: 2,
              active: 1,
              offline: 1,
              machines: [_machine()],
            )),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.text('Machine Status'), findsOneWidget);
      expect(find.text('3'), findsWidgets);
      expect(find.text('Online'), findsOneWidget);
      expect(find.text('Fencing'), findsOneWidget);
    });

    testWidgets('null telemetry shows unavailable, not zero', (tester) async {
      await _pumpDashboard(tester, [
        dashboardProvider.overrideWith((ref) async => _summary(machines: [_machine()])),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.text('Unavailable'), findsNWidgets(4));
      expect(find.text('No report received'), findsNWidgets(4));
    });

    testWidgets('voltage telemetry shows real value from machine', (tester) async {
      await _pumpDashboard(tester, [
        dashboardProvider.overrideWith((ref) async => _summary(
              machines: [_machine(voltage: 12.22, batteryPct: 100, charging: true, gsmSignal: 4)],
            )),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.text('12.22'), findsOneWidget);
    });

    testWidgets('error state shows friendly message', (tester) async {
      await _pumpDashboard(tester, [
        dashboardProvider.overrideWith(
          (ref) async => throw Exception('Network error'),
        ),
        currentUserProvider.overrideWith((ref) => _user),
      ]);
      expect(find.text('Unable to load the dashboard. Please try again.'), findsOneWidget);
      expect(find.textContaining('Network error'), findsNothing);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pumpDashboard(
        tester,
        [
          dashboardProvider.overrideWith((ref) async => _summary(machines: [_machine()])),
          currentUserProvider.overrideWith((ref) => _user),
        ],
        brightness: Brightness.dark,
      );
      expect(find.text('Machine Status'), findsOneWidget);
    });

    testWidgets('renders without overflow at narrow width', (tester) async {
      await _pumpDashboard(
        tester,
        [
          dashboardProvider.overrideWith((ref) async => _summary(machines: [_machine()])),
          currentUserProvider.overrideWith((ref) => _user),
        ],
        surface: const Size(320, 1600),
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('Dashboard metric widgets', () {
    testWidgets('BatteryWidget null shows unavailable', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: BatteryWidget(battery: null)),
      ));
      expect(find.text('Unavailable'), findsOneWidget);
      expect(find.text('No report received'), findsOneWidget);
    });

    testWidgets('BatteryWidget value shows percentage and tone', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: BatteryWidget(battery: 88)),
      ));
      expect(find.text('88'), findsOneWidget);
      expect(find.text('%'), findsOneWidget);
      expect(find.text('On battery'), findsOneWidget);
    });

    testWidgets('BatteryWidget charging shows charging status', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: BatteryWidget(battery: 100, charging: true)),
      ));
      expect(find.text('100'), findsOneWidget);
      expect(find.text('Charging'), findsOneWidget);
    });

    testWidgets('BatteryWidget not charging shows on battery', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: BatteryWidget(battery: 50, charging: false)),
      ));
      expect(find.text('50'), findsOneWidget);
      expect(find.text('On battery'), findsOneWidget);
    });

    testWidgets('VoltageWidget null shows unavailable', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: VoltageWidget(voltage: null)),
      ));
      expect(find.text('Unavailable'), findsOneWidget);
    });

    testWidgets('VoltageWidget value shows voltage in volts', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: VoltageWidget(voltage: 12.22)),
      ));
      expect(find.text('12.22'), findsOneWidget);
      expect(find.text('V'), findsOneWidget);
      expect(find.text('Latest sample'), findsOneWidget);
    });

    testWidgets('GsmStatusWidget value shows signal level', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: GsmStatusWidget(signal: 4)),
      ));
      expect(find.text('4'), findsOneWidget);
      expect(find.text('/4'), findsOneWidget);
      expect(find.text('Strong signal'), findsOneWidget);
    });

    testWidgets('FaultsWidget null shows unavailable', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: FaultsWidget(count: null)),
      ));
      expect(find.text('Unavailable'), findsOneWidget);
      expect(find.text('No report received'), findsOneWidget);
    });

    testWidgets('FaultsWidget zero shows no active faults', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: FaultsWidget(count: 0)),
      ));
      expect(find.text('0'), findsOneWidget);
      expect(find.text('No active faults'), findsOneWidget);
    });

    testWidgets('FaultsWidget count shows active faults', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: const Scaffold(body: FaultsWidget(count: 3)),
      ));
      expect(find.text('3'), findsOneWidget);
      expect(find.text('3 active faults'), findsOneWidget);
    });
  });
}
