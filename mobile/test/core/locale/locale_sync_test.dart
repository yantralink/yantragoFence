import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yantrago/core/auth/auth_service.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/locale/locale_pending_store.dart';
import 'package:yantrago/core/locale/locale_preferences.dart';
import 'package:yantrago/core/locale/locale_sync_provider.dart';
import 'package:yantrago/core/network/network_error_messages.dart';
import 'package:yantrago/models/user.dart';

class _FakeAuthService implements AuthService {
  _FakeAuthService(this.behavior);

  /// 'ok' → succeed; 'transient' → connection timeout;
  /// 'rejected' → 403; or a function of call count.
  String behavior;
  int calls = 0;
  final List<String> requestedLocales = [];

  @override
  Future<User> updatePreferredLocale(String locale) async {
    calls++;
    requestedLocales.add(locale);
    if (behavior == 'transient') {
      throw DioException(
        requestOptions: RequestOptions(path: '/me/locale'),
        type: DioExceptionType.connectionTimeout,
      );
    }
    if (behavior == 'rejected') {
      // Mimic the production interceptor's code attachment for a 403.
      throw DioException(
        requestOptions: RequestOptions(path: '/me/locale', extra: {
          kNetworkErrorCodeExtraKey: NetworkErrorCode.forbidden,
        }),
        response: Response(
          requestOptions: RequestOptions(path: '/me/locale'),
          statusCode: 403,
        ),
        type: DioExceptionType.badResponse,
      );
    }
    return _user(locale);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

User _user(String? locale) => User(
      id: 'u1',
      email: 'user@example.com',
      fullName: 'U',
      role: 'CUSTOMER',
      active: true,
      preferredLocale: locale,
    );

ProviderContainer _container({
  required SharedPreferences prefs,
  required _FakeAuthService authService,
}) {
  return ProviderContainer(overrides: <Override>[
    sharedPreferencesProvider.overrideWithValue(prefs),
    deviceLocalesProvider.overrideWithValue(const [Locale('en')]),
    authServiceProvider.overrideWithValue(authService),
    localeSyncBackoffProvider
        .overrideWithValue(const [Duration.zero, Duration.zero]),
  ]);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('LocalePendingStore', () {
    test('markers are account-scoped — accounts never share state',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      await LocalePendingStore.write(prefs, 'u1', 'o1', 'hi');
      expect(LocalePendingStore.read(prefs, 'u1', 'o1'), 'hi');
      // Different user / org: no leakage.
      expect(LocalePendingStore.read(prefs, 'u2', 'o1'), isNull);
      expect(LocalePendingStore.read(prefs, 'u1', 'o2'), isNull);
      // Clearing one account leaves the other untouched.
      await LocalePendingStore.write(prefs, 'u2', 'o1', 'mr');
      await LocalePendingStore.clear(prefs, 'u1', 'o1');
      expect(LocalePendingStore.read(prefs, 'u1', 'o1'), isNull);
      expect(LocalePendingStore.read(prefs, 'u2', 'o1'), 'mr');
    });
  });

  group('LocaleSyncController', () {
    test('localeChanged syncs and clears pending on success', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('ok');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);

      await container
          .read(localeSyncProvider.notifier)
          .localeChanged(locale: 'hi', userId: 'u1', organizationId: 'o1');

      expect(authService.calls, 1);
      expect(authService.requestedLocales, ['hi']);
      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.synced);
      expect(LocalePendingStore.read(prefs, 'u1', 'o1'), isNull);
    });

    test('transient failure keeps pending state and never claims sync',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('transient');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);

      await container
          .read(localeSyncProvider.notifier)
          .localeChanged(locale: 'mr', userId: 'u1', organizationId: 'o1');

      final state = container.read(localeSyncProvider);
      expect(state.status, LocaleSyncStatus.pending);
      expect(state.pendingLocale, 'mr');
      // Durable marker survives so intent outlives process death.
      expect(LocalePendingStore.read(prefs, 'u1', 'o1'), 'mr');
      expect(authService.calls, 3); // initial + 2 bounded retries
    });

    test('permanent rejection stops retrying and reports failed', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('rejected');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);

      await container
          .read(localeSyncProvider.notifier)
          .localeChanged(locale: 'hi', userId: 'u1', organizationId: 'o1');

      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.failed);
      expect(authService.calls, 1); // no retries on permission errors
    });

    test('rapid selections coalesce — latest wins, stale response ignored',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('ok');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);
      final notifier = container.read(localeSyncProvider.notifier);

      // Overlapping selections — writes are serialized in order.
      await notifier.localeChanged(
          locale: 'hi', userId: 'u1', organizationId: 'o1');
      await notifier.localeChanged(
          locale: 'mr', userId: 'u1', organizationId: 'o1');

      expect(authService.requestedLocales, ['hi', 'mr']);
      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.synced);
      expect(LocalePendingStore.read(prefs, 'u1', 'o1'), isNull);
    });

    test('accountCleared cancels in-flight attempts and clears the marker',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('transient');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);
      final notifier = container.read(localeSyncProvider.notifier);

      final selection =
          notifier.localeChanged(locale: 'hi', userId: 'u1', organizationId: 'o1');
      await notifier.accountCleared(userId: 'u1', organizationId: 'o1');
      await selection;

      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.synced);
      expect(LocalePendingStore.read(prefs, 'u1', 'o1'), isNull);
      // The generation guard stopped the retry loop.
      expect(authService.calls, lessThan(3));
    });

    test('sessionResolved: server preference wins when no pending edit',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('ok');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);

      await container.read(localeSyncProvider.notifier).sessionResolved(
            userId: 'u1',
            organizationId: 'o1',
            serverPreferredLocale: 'hi',
          );

      expect(
          container.read(localeControllerProvider).locale.languageCode, 'hi');
      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.synced);
      expect(authService.calls, 0);
    });

    test('sessionResolved: durable pending edit outranks server preference',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      await LocalePendingStore.write(prefs, 'u1', 'o1', 'mr');
      final authService = _FakeAuthService('ok');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);

      await container.read(localeSyncProvider.notifier).sessionResolved(
            userId: 'u1',
            organizationId: 'o1',
            serverPreferredLocale: 'hi', // server is behind — local wins
          );

      expect(
          container.read(localeControllerProvider).locale.languageCode, 'mr');
      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.pending);
      // The pending intent was re-attempted and confirmed.
      expect(authService.requestedLocales.contains('mr'), isTrue);
    });

    test('sessionResolved: unset server preference syncs the device locale',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(LocalePreferences.languageKey, 'hi');
      final authService = _FakeAuthService('ok');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);

      await container.read(localeSyncProvider.notifier).sessionResolved(
            userId: 'u1',
            organizationId: 'o1',
            serverPreferredLocale: null, // never chose — first sync
          );

      expect(authService.requestedLocales, ['hi']);
      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.synced);
    });

    test('serverLocaleRefreshed: applies the server preference when local is stale',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('ok');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);

      await container.read(localeSyncProvider.notifier).serverLocaleRefreshed(
            userId: 'u1',
            organizationId: 'o1',
            serverPreferredLocale: 'mr',
          );

      expect(
          container.read(localeControllerProvider).locale.languageCode, 'mr');
      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.synced);
    });

    test('serverLocaleRefreshed: never overrides a newer pending local edit',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      final authService = _FakeAuthService('transient');
      final container = _container(prefs: prefs, authService: authService);
      addTearDown(container.dispose);
      final notifier = container.read(localeSyncProvider.notifier);

      // Local edit not yet synced.
      await notifier.localeChanged(
          locale: 'hi', userId: 'u1', organizationId: 'o1');
      // Server reports an older value — the pending edit must win.
      await notifier.serverLocaleRefreshed(
        userId: 'u1',
        organizationId: 'o1',
        serverPreferredLocale: 'en',
      );

      expect(
          container.read(localeControllerProvider).locale.languageCode, 'hi');
      expect(container.read(localeSyncProvider).status,
          LocaleSyncStatus.pending);
    });
  });
}
