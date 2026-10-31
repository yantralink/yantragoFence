import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/services/reverse_geocoding_service.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/models/machine_location.dart';
import 'package:yantrago/models/user.dart';

User _user(String id, {String org = 'o1'}) => User(
  id: id,
  organizationId: org,
  email: '$id@example.invalid',
  fullName: 'Test User',
  role: 'CUSTOMER',
  active: true,
);
final _session = StateProvider<User?>((ref) => _user('u1'));

ProviderContainer _container({List<Override> overrides = const []}) {
  final container = ProviderContainer(
    overrides: [
      currentUserProvider.overrideWith((ref) => ref.watch(_session)),
      ...overrides,
    ],
  );
  container.listen(mapConsentProvider, (_, __) {});
  addTearDown(container.dispose);
  return container;
}

class _Geocoder extends ReverseGeocodingService {
  _Geocoder() : super(Dio());
  int calls = 0;
  @override
  Future<ResolvedAddress?> reverseGeocodeMachine(
    String id, {
    String languageCode = 'en',
    String? locationKey,
  }) async {
    calls++;
    return const ResolvedAddress(displayName: 'Test Farm');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final stored = <String, String>{};
  Completer<void>? writeGate;
  Completer<void>? readGate;
  const channel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    stored.clear();
    writeGate = null;
    readGate = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          final args = call.arguments as Map;
          if (call.method == 'read') {
            if (readGate != null) await readGate!.future;
            return stored[args['key']];
          }
          if (call.method == 'write') {
            if (writeGate != null) await writeGate!.future;
            stored[args['key'] as String] = args['value'] as String;
          }
          return null;
        });
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null),
  );

  test('account switch cannot inherit cached or persisted consent', () async {
    final container = _container();
    await container.read(mapConsentProvider.future);
    await container.read(mapConsentProvider.notifier).grant();
    expect(await container.read(mapConsentProvider.future), MapConsent.granted);
    container.read(_session.notifier).state = _user('u2');
    expect(
      await container.read(mapConsentProvider.future),
      MapConsent.undecided,
    );
    container.invalidate(mapConsentProvider);
    expect(
      await container.read(mapConsentProvider.future),
      MapConsent.undecided,
    );
  });

  test('same user in another organization requires new consent', () async {
    final container = _container();
    await container.read(mapConsentProvider.future);
    await container.read(mapConsentProvider.notifier).grant();
    container.read(_session.notifier).state = _user('u1', org: 'o2');
    expect(
      await container.read(mapConsentProvider.future),
      MapConsent.undecided,
    );
  });

  test('same account restores its choice; signed out has no consent', () async {
    final container = _container();
    await container.read(mapConsentProvider.future);
    await container.read(mapConsentProvider.notifier).grant();
    container.invalidate(mapConsentProvider);
    expect(await container.read(mapConsentProvider.future), MapConsent.granted);
    await container.read(mapConsentProvider.notifier).decline();
    container.invalidate(mapConsentProvider);
    expect(
      await container.read(mapConsentProvider.future),
      MapConsent.declined,
    );
    container.read(_session.notifier).state = null;
    expect(
      await container.read(mapConsentProvider.future),
      MapConsent.undecided,
    );
  });

  test('late consent write cannot grant the next account permission', () async {
    final container = _container();
    await container.read(mapConsentProvider.future);
    writeGate = Completer<void>();
    final oldGrant = container.read(mapConsentProvider.notifier).grant();
    await pumpEventQueue();
    container.read(_session.notifier).state = _user('u2');
    await container.read(mapConsentProvider.future);
    writeGate!.complete();
    await oldGrant;
    expect(
      await container.read(mapConsentProvider.future),
      MapConsent.undecided,
    );
    container.invalidate(mapConsentProvider);
    expect(
      await container.read(mapConsentProvider.future),
      MapConsent.undecided,
    );
  });

  test(
    'geocoding stays blocked while the next account consent is loading',
    () async {
      final geocoder = _Geocoder();
      final container = _container(
        overrides: [
          reverseGeocodingProvider.overrideWithValue(geocoder),
          machineLocationProvider.overrideWith(
            (ref, id) async =>
                const MachineLocation(latitude: 12.97, longitude: 77.59),
          ),
        ],
      );
      await container.read(mapConsentProvider.future);
      await container.read(mapConsentProvider.notifier).grant();
      container.listen(machineAddressProvider('m1'), (_, __) {});
      await container.read(machineAddressProvider('m1').future);
      expect(geocoder.calls, 1);
      readGate = Completer<void>();
      container.read(_session.notifier).state = _user('u2');
      await pumpEventQueue();
      expect(geocoder.calls, 1);
      readGate!.complete();
      expect(await container.read(machineAddressProvider('m1').future), isNull);
      expect(geocoder.calls, 1);
    },
  );

  test(
    'legacy consent without account identity requires opt-in again',
    () async {
      stored['map_consent'] = 'granted';
      expect(
        await _container().read(mapConsentProvider.future),
        MapConsent.undecided,
      );
    },
  );
}
