import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/services/reverse_geocoding_service.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/models/user.dart';

const _user = User(
  id: 'u1',
  organizationId: 'o1',
  email: 'a@example.invalid',
  fullName: 'Customer',
  role: 'CUSTOMER',
  active: true,
);
const _otherUser = User(
  id: 'u2',
  organizationId: 'o2',
  email: 'b@example.invalid',
  fullName: 'Other Customer',
  role: 'CUSTOMER',
  active: true,
);
final _session = StateProvider<User?>((ref) => _user);

class _Consent extends MapConsentController {
  _Consent(this.value);
  final MapConsent value;
  @override
  Future<MapConsent> build() async => value;
}

/// Intercept requests before transport: tests never contact a real provider.
class _Api {
  final dio = Dio();
  final List<RequestOptions> requests = [];
  final List<(RequestOptions, RequestInterceptorHandler)> pending = [];
  String address = 'Old Farm';
  bool pauseGeocoding = false;
  bool hasLocation = true;
  double latitude = 12.97;

  _Api() {
    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          requests.add(options);
          if (options.path.contains('geocoding') && pauseGeocoding) {
            pending.add((options, handler));
          } else {
            _respond(options, handler);
          }
        },
      ),
    );
  }

  int get geocodingCalls =>
      requests.where((r) => r.path.contains('geocoding')).length;

  void _respond(RequestOptions options, RequestInterceptorHandler handler) {
    final geocoding = options.path.contains('geocoding');
    handler.resolve(
      Response(
        requestOptions: options,
        statusCode: geocoding || hasLocation ? 200 : 404,
        data: geocoding
            ? {'displayName': address}
            : {'latitude': latitude, 'longitude': 77.59},
      ),
    );
  }

  void release() {
    for (final (options, handler) in pending) {
      _respond(options, handler);
    }
    pending.clear();
  }

  void releaseAt(int index, String value) {
    final (options, handler) = pending.removeAt(index);
    handler.resolve(
      Response(
        requestOptions: options,
        statusCode: 200,
        data: {'displayName': value},
      ),
    );
  }

  ProviderContainer container({MapConsent consent = MapConsent.granted}) {
    final result = ProviderContainer(
      overrides: [
        apiClientProvider.overrideWithValue(dio),
        currentUserProvider.overrideWith((ref) => ref.watch(_session)),
        mapConsentProvider.overrideWith(() => _Consent(consent)),
      ],
    );
    addTearDown(result.dispose);
    return result;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'coalesces concurrent lookups but fetches a fresh address on next lookup',
    () async {
      final api = _Api()..pauseGeocoding = true;
      final service = ReverseGeocodingService(api.dio);
      addTearDown(service.dispose);
      final first = service.reverseGeocodeMachine('m1');
      final second = service.reverseGeocodeMachine('m1');
      await pumpEventQueue();
      expect(api.geocodingCalls, 1);
      api.release();
      expect((await first)?.displayName, 'Old Farm');
      expect((await second)?.displayName, 'Old Farm');
      api
        ..pauseGeocoding = false
        ..address = 'New Farm';
      expect(
        (await service.reverseGeocodeMachine('m1'))?.displayName,
        'New Farm',
      );
      expect(api.geocodingCalls, 2);
    },
  );

  for (final consent in [MapConsent.undecided, MapConsent.declined]) {
    test(
      '$consent never starts geocoding; coordinates still available',
      () async {
        final api = _Api();
        final container = api.container(consent: consent);
        await container.read(mapConsentProvider.future);
        final subscription = container.listen(
          machineAddressProvider('m1'),
          (_, __) {},
        );
        addTearDown(subscription.close);
        expect(
          await container.read(machineAddressProvider('m1').future),
          isNull,
        );
        final locationSubscription = container.listen(
          machineLocationProvider('m1'),
          (_, __) {},
        );
        addTearDown(locationSubscription.close);
        final loc = await container.read(machineLocationProvider('m1').future);
        expect(loc?.latitude, 12.97);
        expect(api.geocodingCalls, 0);
      },
    );
  }

  test(
    'location invalidation refreshes an already displayed address',
    () async {
      final api = _Api();
      final container = api.container();
      await container.read(mapConsentProvider.future);
      final subscription = container.listen(
        machineAddressProvider('m1'),
        (_, __) {},
      );
      addTearDown(subscription.close);
      expect(
        (await container.read(
          machineAddressProvider('m1').future,
        ))?.displayName,
        'Old Farm',
      );
      api.address = 'New Farm';
      container.invalidate(machineLocationProvider('m1'));
      expect(
        (await container.read(
          machineAddressProvider('m1').future,
        ))?.displayName,
        'New Farm',
      );
      expect(api.geocodingCalls, 2);
    },
  );

  test('no GPS skips geocoding even with consent', () async {
    final api = _Api()..hasLocation = false;
    final container = api.container();
    await container.read(mapConsentProvider.future);
    expect(await container.read(machineAddressProvider('m1').future), isNull);
    expect(api.geocodingCalls, 0);
  });

  test(
    'refresh during geocoding cannot reuse an old location response',
    () async {
      final api = _Api()..pauseGeocoding = true;
      final container = api.container();
      await container.read(mapConsentProvider.future);
      final subscription = container.listen(
        machineAddressProvider('m1'),
        (_, __) {},
      );
      addTearDown(subscription.close);
      await pumpEventQueue();
      expect(api.geocodingCalls, 1);
      api.latitude = 13.5;
      container.invalidate(machineLocationProvider('m1'));
      await pumpEventQueue();
      expect(api.geocodingCalls, 2);
      api.releaseAt(1, 'New Farm');
      await pumpEventQueue();
      expect(
        container.read(machineAddressProvider('m1')).valueOrNull?.displayName,
        'New Farm',
      );
      api.releaseAt(0, 'Old Farm');
      await pumpEventQueue();
      expect(
        container.read(machineAddressProvider('m1')).valueOrNull?.displayName,
        'New Farm',
      );
    },
  );

  test('language is part of request coalescing identity', () async {
    final api = _Api()..pauseGeocoding = true;
    final service = ReverseGeocodingService(api.dio);
    addTearDown(service.dispose);
    final english = service.reverseGeocodeMachine('m1');
    final marathi = service.reverseGeocodeMachine('m1', languageCode: 'mr');
    await pumpEventQueue();
    expect(api.geocodingCalls, 2);
    expect(api.requests.map((r) => r.queryParameters['lang']), ['en', 'mr']);
    api.release();
    await Future.wait([english, marathi]);
  });

  test(
    'account change cancels old lookups and creates an independent service',
    () async {
      final api = _Api()..pauseGeocoding = true;
      final container = api.container();
      final oldService = container.read(reverseGeocodingProvider);
      final oldResult = oldService.reverseGeocodeMachine('m1');
      await pumpEventQueue();
      container.read(_session.notifier).state = _otherUser;
      final newService = container.read(reverseGeocodingProvider);
      expect(identical(oldService, newService), isFalse);
      expect(await oldResult, isNull);
      expect(api.requests.first.cancelToken?.isCancelled, isTrue);
      api
        ..pauseGeocoding = false
        ..address = 'Other Account Farm';
      expect(
        (await newService.reverseGeocodeMachine('m1'))?.displayName,
        'Other Account Farm',
      );
      api.release(); // A late old response cannot populate a new account's state.
      container.read(_session.notifier).state = null;
      expect(await container.read(machineAddressProvider('m1').future), isNull);
    },
  );
}
