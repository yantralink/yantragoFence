// Widget tests for the migrated map pages, location card, and machine
// marker. Providers are overridden so the tests never contact the
// network, maps API, or reverse geocoding service.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/features/machines/pages/machine_location_map_page.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/widgets/location_card.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/machine_location.dart';

Machine _machine({String name = 'North Field Fence'}) {
  return Machine(
    id: 'm1',
    machineId: 'YG-001',
    name: name,
    status: 'OFFLINE',
    isOnline: false,
    createdAt: DateTime(2026, 1, 1),
  );
}

MachineLocation _location() => const MachineLocation(
      machineId: 'm1',
      latitude: 18.520430,
      longitude: 73.856740,
    );

/// Fixed-consent controllers for tests — they never touch
/// FlutterSecureStorage (which has no plugin implementation in tests).
class _GrantedConsent extends MapConsentController {
  @override
  Future<MapConsent> build() async => MapConsent.granted;
}

class _UndecidedConsent extends MapConsentController {
  @override
  Future<MapConsent> build() async => MapConsent.undecided;
}

class _DeclinedConsent extends MapConsentController {
  @override
  Future<MapConsent> build() async => MapConsent.declined;
}

Widget _wrapped(
  Widget child, {
  Brightness brightness = Brightness.light,
  List<Override> overrides = const [],
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
}) {
  final GoRouter router = GoRouter(
    initialLocation: '/test',
    routes: [
      ShellRoute(
        builder: (context, state, c) => c,
        routes: [
          GoRoute(path: '/test', builder: (context, state) => child),
          GoRoute(
            path: '/app/machines/:id/location',
            builder: (context, state) =>
                const Scaffold(body: Center(child: Text('MAP PAGE'))),
          ),
        ],
      ),
    ],
  );
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp.router(
      theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
      routerConfig: router,
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
  // FlutterSecureStorage channel calls never complete inside testWidgets —
  // the platform message goes nowhere and the future hangs. Answer them so
  // consent grant() reaches its state update (persistence still no-ops).
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (call) async => null,
    );
  });

  group('MachineLocationMapPage', () {
    testWidgets('shows loading state when consent granted', (tester) async {
      final completer = Completer<MachineLocation?>();
      await _pump(
        tester,
        const MachineLocationMapPage(machineId: 'm1'),
        overrides: [
          mapConsentProvider.overrideWith(() => _GrantedConsent()),
          machineDetailProvider('m1').overrideWith((ref) async => _machine()),
          machineLocationProvider('m1').overrideWith((ref) => completer.future),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Loading location…'), findsOneWidget);
    });

    testWidgets('shows empty state when no location', (tester) async {
      await _pump(
        tester,
        const MachineLocationMapPage(machineId: 'm1'),
        overrides: [
          mapConsentProvider.overrideWith(() => _GrantedConsent()),
          machineDetailProvider('m1').overrideWith((ref) async => _machine()),
          machineLocationProvider('m1').overrideWith((ref) async => null),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('No location data available'), findsOneWidget);
    });

    testWidgets('shows empty state on location error', (tester) async {
      await _pump(
        tester,
        const MachineLocationMapPage(machineId: 'm1'),
        overrides: [
          mapConsentProvider.overrideWith(() => _GrantedConsent()),
          machineDetailProvider('m1').overrideWith((ref) async => _machine()),
          machineLocationProvider('m1').overrideWith(
            (ref) async => throw Exception('Network error'),
          ),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('No location data available'), findsOneWidget);
    });

    testWidgets('shows consent gate when undecided — no map, no geocoding',
        (tester) async {
      await _pump(
        tester,
        const MachineLocationMapPage(machineId: 'm1'),
        overrides: [
          mapConsentProvider.overrideWith(() => _UndecidedConsent()),
          machineDetailProvider('m1').overrideWith((ref) async => _machine()),
          machineLocationProvider('m1').overrideWith((ref) async => _location()),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('Enable map services'), findsWidgets);
      expect(find.text('Allow'), findsOneWidget);
      expect(find.text('Not now'), findsOneWidget);
    });

    testWidgets('shows declined view with coordinates only', (tester) async {
      await _pump(
        tester,
        const MachineLocationMapPage(machineId: 'm1'),
        overrides: [
          mapConsentProvider.overrideWith(() => _DeclinedConsent()),
          machineDetailProvider('m1').overrideWith((ref) async => _machine()),
          machineLocationProvider('m1').overrideWith((ref) async => _location()),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('Map services are off'), findsOneWidget);
      expect(find.textContaining('18.520430'), findsOneWidget);
    });

    testWidgets('granting consent from the gate transitions to map flow',
        (tester) async {
      await _pump(
        tester,
        const MachineLocationMapPage(machineId: 'm1'),
        overrides: [
          mapConsentProvider.overrideWith(() => _UndecidedConsent()),
          machineDetailProvider('m1').overrideWith((ref) async => _machine()),
          machineLocationProvider('m1').overrideWith(
              (ref) => Completer<MachineLocation?>().future),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('Allow'), findsOneWidget);

      // Tap Allow — grant() writes to SecureStorage; in tests the plugin
      // call fails but the controller still applies the session choice.
      await tester.tap(find.text('Allow'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // Consent gate gone — the page moved into the location flow
      // (which stays on the loading panel while the future pends).
      expect(find.text('Not now'), findsNothing);
      expect(find.text('Loading location…'), findsOneWidget);
    });
    // Note: Data-state tests (with WebView) are skipped because
    // WebViewPlatform.instance is not available in widget tests.
  });

  group('LocationCard', () {
    testWidgets('shows coordinates when location available', (tester) async {
      await _pump(
        tester,
        const LocationCard(machineId: 'm1'),
        overrides: [
          machineLocationProvider('m1').overrideWith((ref) async => _location()),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('Current Location'), findsOneWidget);
      expect(find.textContaining('18.520430'), findsOneWidget);
    });

    testWidgets('shows no-location state when missing', (tester) async {
      await _pump(
        tester,
        const LocationCard(machineId: 'm1'),
        overrides: [
          machineLocationProvider('m1').overrideWith((ref) async => null),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('No location data available'), findsOneWidget);
    });

    testWidgets('shows loading indicator while fetching', (tester) async {
      final completer = Completer<MachineLocation?>();
      await _pump(
        tester,
        const LocationCard(machineId: 'm1'),
        overrides: [
          machineLocationProvider('m1').overrideWith((ref) => completer.future),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('tapping navigates to map page', (tester) async {
      await _pump(
        tester,
        const LocationCard(machineId: 'm1'),
        overrides: [
          machineLocationProvider('m1').overrideWith((ref) async => _location()),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      await tester.tap(find.text('Current Location'));
      await tester.pumpAndSettle();
      expect(find.text('MAP PAGE'), findsOneWidget);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const LocationCard(machineId: 'm1'),
        brightness: Brightness.dark,
        overrides: [
          machineLocationProvider('m1').overrideWith((ref) async => _location()),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(find.text('Current Location'), findsOneWidget);
    });

    testWidgets('renders without overflow at 200% text scale', (tester) async {
      await _pump(
        tester,
        const LocationCard(machineId: 'm1'),
        textScale: 2.0,
        surface: const Size(390, 800),
        overrides: [
          machineLocationProvider('m1').overrideWith((ref) async => _location()),
          machineAddressProvider('m1').overrideWith((ref) async => null),
        ],
      );
      expect(tester.takeException(), isNull);
    });
  });
}
