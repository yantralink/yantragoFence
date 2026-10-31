import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/core/services/reverse_geocoding_service.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/widgets/machine_card.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/machine_location.dart';

const _location = MachineLocation(
  machineId: 'm1',
  latitude: 12.97,
  longitude: 77.59,
);
const _address = ResolvedAddress(
  displayName: 'Mahatma Gandhi Road, Ashokanagar, Bengaluru',
  road: 'Mahatma Gandhi Road',
  suburb: 'Ashokanagar',
  city: 'Bengaluru',
);

Machine _machine({
  bool online = true,
  String status = 'FENCING_OFF',
  String name = 'Accenture Machine 2',
}) => Machine(
  id: 'm1',
  machineId: 'YG000009',
  name: name,
  status: status,
  isOnline: online,
  createdAt: DateTime(2026),
  lastSeenAt: DateTime.now(),
);

Future<void> _pump(
  WidgetTester tester, {
  Machine? machine,
  MachineLocation? location = _location,
  ResolvedAddress? address = _address,
  double width = 390,
  double scale = 1,
  Locale locale = const Locale('en'),
  bool dark = false,
  VoidCallback? onTap,
  List<Override> overrides = const [],
}) async {
  await tester.binding.setSurfaceSize(Size(width, 1100));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        machineLocationProvider.overrideWith((ref, id) async => location),
        machineAddressProvider.overrideWith((ref, id) async => address),
        ...overrides,
      ],
      child: MaterialApp(
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        theme: dark ? AppTheme.darkTheme : AppTheme.lightTheme,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(scale)),
          child: child!,
        ),
        home: Scaffold(
          body: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: RepaintBoundary(
                key: const ValueKey('card-preview'),
                child: MachineCard(
                  machine: machine ?? _machine(),
                  onTap: onTap,
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('shows real data and location tap preserves whole-card action', (
    tester,
  ) async {
    var taps = 0;
    await _pump(tester, onTap: () => taps++);
    expect(find.text('Accenture Machine 2'), findsOneWidget);
    expect(find.text('YG000009'), findsOneWidget);
    expect(find.text('Online'), findsOneWidget);
    expect(find.text('Fence OFF · confirmed'), findsOneWidget);
    expect(find.byIcon(Icons.access_time), findsOneWidget);
    await tester.tap(find.text(_address.displayName));
    expect(taps, 1);
  });

  testWidgets('online alone never confirms OFF; explicit ON remains separate', (
    tester,
  ) async {
    await _pump(tester, machine: _machine(status: 'ACTIVE'));
    expect(find.text('Online'), findsOneWidget);
    expect(find.textContaining('confirmed'), findsNothing);
    await _pump(tester, machine: _machine(status: 'FENCING_ON'));
    expect(find.text('Online'), findsOneWidget);
    expect(find.text('Fence ON · confirmed'), findsOneWidget);
  });

  testWidgets('offline retains and labels last-known location', (tester) async {
    await _pump(tester, machine: _machine(online: false));
    expect(find.text('Offline'), findsOneWidget);
    expect(
      find.text('Last known location: ${_address.displayName}'),
      findsOneWidget,
    );
  });

  testWidgets('coordinates remain when address resolution is unavailable', (
    tester,
  ) async {
    await _pump(tester, address: null);
    expect(find.text('12.970000, 77.590000'), findsOneWidget);
    expect(find.byIcon(Icons.location_on_outlined), findsOneWidget);
  });

  testWidgets('no GPS still renders the required location row', (tester) async {
    await _pump(tester, location: null);
    expect(find.text('No location data available'), findsOneWidget);
    expect(find.text(_address.displayName), findsNothing);
  });

  testWidgets('address refresh hides stale text until fresh data arrives', (
    tester,
  ) async {
    final pending = Completer<ResolvedAddress?>();
    var reads = 0;
    await _pump(
      tester,
      overrides: [
        machineAddressProvider('m1').overrideWith((ref) {
          reads++;
          return reads == 1 ? Future.value(_address) : pending.future;
        }),
      ],
    );
    final container = ProviderScope.containerOf(
      tester.element(find.byType(MachineCard)),
    );
    container.invalidate(machineAddressProvider('m1'));
    await tester.pumpAndSettle();
    expect(find.text(_address.displayName), findsNothing);
    expect(find.text('12.970000, 77.590000'), findsOneWidget);
    pending.completeError(StateError('geocoder unavailable'));
    await tester.pumpAndSettle();
    expect(find.text(_address.displayName), findsNothing);
    expect(find.text('12.970000, 77.590000'), findsOneWidget);
  });

  testWidgets('location loading and errors never hide the card', (
    tester,
  ) async {
    final pending = Completer<MachineLocation?>();
    await _pump(
      tester,
      overrides: [
        machineLocationProvider('m1').overrideWith((ref) => pending.future),
      ],
    );
    expect(find.text('Loading location…'), findsOneWidget);
    pending.completeError(StateError('private error'));
    await tester.pumpAndSettle();
    expect(find.text('No location data available'), findsOneWidget);
    expect(find.text('Accenture Machine 2'), findsOneWidget);
    expect(find.textContaining('private error'), findsNothing);
  });

  for (final lang in ['en', 'hi', 'mr']) {
    for (final dark in [false, true]) {
      testWidgets('$lang dark=$dark: long content at 320px and 200% wraps', (
        tester,
      ) async {
        await _pump(
          tester,
          width: 320,
          scale: 2,
          dark: dark,
          locale: Locale(lang),
          machine: _machine(
            online: false,
            name: 'North Field Cooperative Society Machine With A Long Name',
          ),
          address: const ResolvedAddress(
            displayName:
                'Very Long Farm Address, Farmland Extension, Village Region',
            road: 'Very Long Farm Address Farmland Extension Village Region',
            city: 'Bengaluru',
          ),
        );
        expect(tester.takeException(), isNull);
        expect(find.byIcon(Icons.location_on_outlined), findsOneWidget);
        final card = tester.getRect(find.byType(MachineCard));
        expect(card.width, lessThanOrEqualTo(320));
      });
    }
  }

  // SDK fonts make these previews readable; Windows golden baselines follow
  // the repository's existing convention and are excluded from Linux CI.
  for (final dark in [false, true]) {
    testWidgets('machine card golden dark=$dark', tags: ['golden'], (
      tester,
    ) async {
      final configFile = File('.dart_tool/package_config.json');
      final packages =
          jsonDecode(configFile.readAsStringSync())['packages'] as List;
      final flutter = packages.firstWhere((p) => p['name'] == 'flutter');
      final root = Directory.fromUri(
        configFile.absolute.uri.resolve('${flutter['rootUri']}/'),
      ).parent.parent.path;
      final font = FontLoader('Roboto');
      for (final weight in ['regular', 'medium', 'bold']) {
        font.addFont(
          Future.value(
            ByteData.sublistView(
              File(
                '$root/bin/cache/artifacts/material_fonts/'
                'roboto-$weight.ttf',
              ).readAsBytesSync(),
            ),
          ),
        );
      }
      await font.load();
      final icons = FontLoader('MaterialIcons')
        ..addFont(
          Future.value(
            ByteData.sublistView(
              File(
                '$root/bin/cache/artifacts/material_fonts/materialicons-regular.otf',
              ).readAsBytesSync(),
            ),
          ),
        );
      await icons.load();
      await _pump(tester, dark: dark, onTap: () {});
      await expectLater(
        find.byKey(const ValueKey('card-preview')),
        matchesGoldenFile(
          '../components/goldens/'
          'machine_card_${dark ? 'dark' : 'light'}.png',
        ),
      );
    });
  }
}
