// Widget tests for the migrated Alerts, Command Status, History, and
// Settings screens. Providers are overridden so the tests never contact
// the network.

import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/alerts/pages/alerts_page.dart';
import 'package:yantrago/features/alerts/providers/alerts_provider.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/features/commands/widgets/command_status_widget.dart';
import 'package:yantrago/features/history/pages/activity_history_page.dart';
import 'package:yantrago/features/history/providers/history_provider.dart';
import 'package:yantrago/features/settings/pages/settings_page.dart';
import 'package:yantrago/models/alert.dart';
import 'package:yantrago/models/command.dart';

Alert _alert({
  String id = 'a1',
  String alertType = 'DEVICE_OFFLINE',
  String severity = 'WARNING',
  String message = 'Device went offline unexpectedly',
  bool acknowledged = false,
}) {
  return Alert(
    id: id,
    organizationId: 'org1',
    deviceId: 'd1',
    imei: '000000000000001',
    alertType: alertType,
    severity: severity,
    message: message,
    acknowledged: acknowledged,
    createdAt: DateTime(2026, 1, 1, 10, 30),
  );
}

Command _command({
  String id = 'c1',
  String commandType = 'FENCING_ON',
  String status = 'PENDING',
  String? lastError,
}) {
  return Command(
    id: id,
    machineId: 'm1',
    commandType: commandType,
    status: status,
    lastError: lastError,
    createdAt: DateTime(2026, 1, 1, 10, 30),
  );
}

Widget _wrapped(
  Widget child, {
  Brightness brightness = Brightness.light,
  List<Override> overrides = const [],
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
      home: child,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
        child: widget!,
      ),
    ),
  );
}

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Brightness brightness = Brightness.light,
  List<Override> overrides = const [],
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
}) async {
  await tester.binding.setSurfaceSize(surface);
  await tester.pumpWidget(_wrapped(
    child,
    brightness: brightness,
    overrides: overrides,
    textScale: textScale,
    surface: surface,
  ));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

void main() {
  group('AlertsPage', () {
    testWidgets('shows alerts with section header and count', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          alertsProvider.overrideWith((ref) async => [
                _alert(id: 'a1', alertType: 'DEVICE_OFFLINE'),
                _alert(id: 'a2', alertType: 'LOW_BATTERY', severity: 'CRITICAL'),
              ]),
        ],
      );
      expect(find.text('ACTIVE ALERTS'), findsOneWidget);
      expect(find.text('2 total'), findsOneWidget);
      expect(find.text('DEVICE_OFFLINE'), findsOneWidget);
      expect(find.text('LOW_BATTERY'), findsOneWidget);
    });

    testWidgets('empty state shows message and refresh', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [alertsProvider.overrideWith((ref) async => [])],
      );
      expect(find.text('No active alerts'), findsOneWidget);
      expect(find.text('Refresh'), findsOneWidget);
    });

    testWidgets('error state shows friendly message', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          alertsProvider.overrideWith(
            (ref) async => throw Exception('SocketException'),
          ),
        ],
      );
      expect(find.text('Unable to load alerts. Please try again.'), findsOneWidget);
      expect(find.textContaining('SocketException'), findsNothing);
    });

    testWidgets('loading state shows loading panel', (tester) async {
      final completer = Completer<List<Alert>>();
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [alertsProvider.overrideWith((ref) => completer.future)],
      );
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Loading alerts…'), findsOneWidget);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        brightness: Brightness.dark,
        overrides: [
          alertsProvider.overrideWith((ref) async => [_alert()]),
        ],
      );
      expect(find.text('DEVICE_OFFLINE'), findsOneWidget);
    });

    testWidgets('renders without overflow at 200% text scale', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        textScale: 2.0,
        surface: const Size(390, 2400),
        overrides: [
          alertsProvider.overrideWith((ref) async => [_alert()]),
        ],
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('unknown severity renders as info tone', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          alertsProvider.overrideWith((ref) async => [
                _alert(alertType: 'CUSTOM_ALERT', severity: 'UNKNOWN'),
              ]),
        ],
      );
      expect(find.text('CUSTOM_ALERT'), findsOneWidget);
      expect(find.text('UNKNOWN'), findsOneWidget);
    });

    testWidgets('long alert message does not overflow', (tester) async {
      final longMessage =
          'Device reported a critical fault in the fencing circuit after a power surge '
          'from the main supply line, causing the unit to shut down automatically as a '
          'safety precaution. Please inspect the power conditioning module immediately.';
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          alertsProvider.overrideWith(
            (ref) async => [_alert(message: longMessage)],
          ),
        ],
      );
      expect(find.textContaining('Device reported a critical fault'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('acknowledged alert shows acknowledged label', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          alertsProvider.overrideWith(
            (ref) async => [_alert(acknowledged: true)],
          ),
        ],
      );
      expect(find.text('Acknowledged'), findsOneWidget);
    });
  });

  group('CommandStatusWidget', () {
    testWidgets('shows empty state when no commands', (tester) async {
      await _pump(
        tester,
        const CommandStatusWidget(),
        overrides: [
          commandProvider.overrideWith((ref) => _FakeCommandNotifier()),
        ],
      );
      expect(find.text('No recent commands'), findsOneWidget);
    });

    testWidgets('shows pending status as Awaiting ACK', (tester) async {
      await _pump(
        tester,
        const CommandStatusWidget(),
        overrides: [
          commandProvider.overrideWith(
            (ref) => _FakeCommandNotifier(
              command: _command(status: 'PENDING'),
              pending: true,
            ),
          ),
        ],
      );
      expect(find.text('Awaiting ACK'), findsWidgets);
      expect(find.text('FENCING_ON'), findsOneWidget);
    });

    testWidgets('shows done status', (tester) async {
      await _pump(
        tester,
        const CommandStatusWidget(),
        overrides: [
          commandProvider.overrideWith(
            (ref) => _FakeCommandNotifier(
              command: _command(status: 'DONE'),
            ),
          ),
        ],
      );
      expect(find.text('Done'), findsWidgets);
    });

    testWidgets('shows failed status with error', (tester) async {
      await _pump(
        tester,
        const CommandStatusWidget(),
        overrides: [
          commandProvider.overrideWith(
            (ref) => _FakeCommandNotifier(
              command: _command(status: 'FAILED', lastError: 'Device timeout'),
            ),
          ),
        ],
      );
      expect(find.text('Failed'), findsWidgets);
      expect(find.text('Device timeout'), findsOneWidget);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const CommandStatusWidget(),
        brightness: Brightness.dark,
        overrides: [
          commandProvider.overrideWith(
            (ref) => _FakeCommandNotifier(
              command: _command(status: 'ACK'),
            ),
          ),
        ],
      );
      expect(find.text('Acknowledged'), findsWidgets);
    });
  });

  group('ActivityHistoryPage', () {
    testWidgets('shows empty state when no machineId', (tester) async {
      await _pump(tester, const ActivityHistoryPage());
      expect(find.text('Select a machine'), findsOneWidget);
    });

    testWidgets('shows history with count', (tester) async {
      await _pump(
        tester,
        const ActivityHistoryPage(machineId: 'm1'),
        overrides: [
          historyProvider('m1').overrideWith((ref) async => [
                _command(id: 'c1', commandType: 'FENCING_ON', status: 'DONE'),
                _command(id: 'c2', commandType: 'FENCING_OFF', status: 'FAILED'),
              ]),
        ],
      );
      expect(find.text('COMMAND HISTORY'), findsOneWidget);
      expect(find.text('2 total'), findsOneWidget);
      expect(find.text('FENCING_ON'), findsOneWidget);
      expect(find.text('FENCING_OFF'), findsOneWidget);
    });

    testWidgets('empty history shows message', (tester) async {
      await _pump(
        tester,
        const ActivityHistoryPage(machineId: 'm1'),
        overrides: [historyProvider('m1').overrideWith((ref) async => [])],
      );
      expect(find.text('No activity history'), findsOneWidget);
    });

    testWidgets('error state shows friendly message', (tester) async {
      await _pump(
        tester,
        const ActivityHistoryPage(machineId: 'm1'),
        overrides: [
          historyProvider('m1').overrideWith(
            (ref) async => throw Exception('Network error'),
          ),
        ],
      );
      expect(find.text('Unable to load history. Please try again.'), findsOneWidget);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const ActivityHistoryPage(machineId: 'm1'),
        brightness: Brightness.dark,
        overrides: [
          historyProvider('m1').overrideWith((ref) async => [_command()]),
        ],
      );
      expect(find.text('FENCING_ON'), findsOneWidget);
    });
  });

  group('SettingsPage', () {
    testWidgets('shows settings sections', (tester) async {
      await _pump(tester, const SettingsPage());
      expect(find.text('CONNECTION'), findsOneWidget);
      expect(find.text('NOTIFICATIONS'), findsOneWidget);
      expect(find.text('ABOUT'), findsOneWidget);
      expect(find.text('Push notifications'), findsOneWidget);
      expect(find.text('YantraGO'), findsWidgets);
    });

    testWidgets('toggling notifications updates state', (tester) async {
      await _pump(tester, const SettingsPage());
      expect(find.byType(Switch), findsOneWidget);
      final Switch sw = tester.widget(find.byType(Switch));
      expect(sw.value, isTrue);
      await tester.tap(find.byType(Switch));
      await tester.pump();
      final Switch sw2 = tester.widget(find.byType(Switch));
      expect(sw2.value, isFalse);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const SettingsPage(),
        brightness: Brightness.dark,
      );
      expect(find.text('Push notifications'), findsOneWidget);
    });
  });
}

class _FakeCommandNotifier extends CommandNotifier {
  _FakeCommandNotifier({Command? command, bool pending = false})
      : super(Dio()) {
    state = CommandState(lastCommand: command, pending: pending);
  }

  @override
  Future<void> sendCommand({
    required String machineId,
    required String imei,
    required String commandType,
  }) async {}
}
