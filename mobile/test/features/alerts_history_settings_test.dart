// Widget tests for the Notifications page (formerly the Alerts page
// Active/Inbox tabs) and the Settings screen. Providers are overridden
// so the tests never contact the network.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/alerts/pages/alerts_page.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/features/settings/pages/settings_page.dart';
import 'package:yantrago/models/notification_inbox.dart';

NotificationInbox _notification({
  String id = 'n1',
  String alertType = 'DEVICE_OFFLINE',
  String severity = 'WARNING',
  String title = 'Device Offline',
  String body = 'Device went offline unexpectedly',
  bool isRead = false,
}) {
  return NotificationInbox(
    id: id,
    organizationId: 'org1',
    userId: 'u1',
    alertId: 'a1',
    eventId: 'e1',
    alertType: alertType,
    severity: severity,
    incidentState: 'OPEN',
    title: title,
    body: body,
    machineId: 'm1',
    observedValue: null,
    observedUnit: null,
    locale: 'en',
    templateVersion: 1,
    isRead: isRead,
    isAcknowledged: false,
    createdAt: DateTime(2026, 1, 1, 10, 30),
    updatedAt: DateTime(2026, 1, 1, 10, 30),
  );
}

NotificationInboxPage _page(List<NotificationInbox> items) {
  return NotificationInboxPage(
    items: items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
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
  group('AlertsPage (Notifications)', () {
    testWidgets('shows Notifications title and inbox items', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          notificationInboxProvider.overrideWith((ref) async => _page([
                _notification(id: 'n1', title: 'Device Offline'),
                _notification(id: 'n2', title: 'Low Battery'),
              ])),
        ],
      );
      expect(find.text('Notifications'), findsOneWidget);
      expect(find.text('Device Offline'), findsWidgets);
      expect(find.text('Low Battery'), findsWidgets);
    });

    testWidgets('has no Active Alerts tab or tab bar', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          notificationInboxProvider
              .overrideWith((ref) async => _page([_notification()])),
        ],
      );
      expect(find.byType(TabBar), findsNothing);
      expect(find.text('Active Alerts'), findsNothing);
      expect(find.text('Inbox'), findsNothing);
    });

    testWidgets('shows mark-all-read and filter actions', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          notificationInboxProvider
              .overrideWith((ref) async => _page([_notification()])),
        ],
      );
      expect(find.byIcon(Icons.done_all), findsOneWidget);
      expect(find.byIcon(Icons.tune), findsOneWidget);
    });

    testWidgets('empty inbox shows empty state', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          notificationInboxProvider.overrideWith((ref) async => _page([])),
        ],
      );
      expect(find.text('No notifications'), findsOneWidget);
    });

    testWidgets('error state shows friendly message', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          notificationInboxProvider.overrideWith(
            (ref) async => throw Exception('SocketException'),
          ),
        ],
      );
      expect(find.textContaining('SocketException'), findsNothing);
    });

    testWidgets('loading state shows spinner', (tester) async {
      final completer = Completer<NotificationInboxPage>();
      await _pump(
        tester,
        const AlertsPage(),
        overrides: [
          notificationInboxProvider
              .overrideWith((ref) => completer.future),
        ],
      );
      expect(find.byType(CircularProgressIndicator), findsWidgets);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        brightness: Brightness.dark,
        overrides: [
          notificationInboxProvider
              .overrideWith((ref) async => _page([_notification()])),
        ],
      );
      expect(find.text('Device Offline'), findsWidgets);
    });

    testWidgets('renders without overflow at 200% text scale', (tester) async {
      await _pump(
        tester,
        const AlertsPage(),
        textScale: 2.0,
        surface: const Size(390, 2400),
        overrides: [
          notificationInboxProvider
              .overrideWith((ref) async => _page([_notification()])),
        ],
      );
      expect(tester.takeException(), isNull);
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
