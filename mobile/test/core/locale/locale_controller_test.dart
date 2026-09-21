import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/locale/locale_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('LocaleController.resolveInitialLocale', () {
    test('saved choice wins over device locale', () {
      final locale = LocaleController.resolveInitialLocale(
        'mr',
        const [Locale('hi', 'IN')],
      );
      expect(locale.languageCode, 'mr');
    });

    test('device locale resolved to supported language, region stripped', () {
      final locale = LocaleController.resolveInitialLocale(
        null,
        const [Locale('en', 'US'), Locale('hi', 'IN')],
      );
      expect(locale.languageCode, 'hi');
    });

    test('first supported device locale wins when several match', () {
      final locale = LocaleController.resolveInitialLocale(
        null,
        const [Locale('en'), Locale('mr'), Locale('hi')],
      );
      expect(locale.languageCode, 'en');
    });

    test('unsupported device locales fall back to English', () {
      final locale = LocaleController.resolveInitialLocale(
        null,
        const [Locale('de'), Locale('fr')],
      );
      expect(locale.languageCode, 'en');
    });

    test('empty device locale list falls back to English', () {
      final locale = LocaleController.resolveInitialLocale(null, const []);
      expect(locale.languageCode, 'en');
    });
  });

  group('LocalePreferences', () {
    test('round-trips a supported code', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      await LocalePreferences.writeLanguageCode(prefs, 'hi');
      expect(LocalePreferences.readValidLanguageCode(prefs), 'hi');
    });

    test('treats missing, corrupt, region-form and unsupported as unset', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{
        LocalePreferences.languageKey: 'hi-IN',
      });
      var prefs = await SharedPreferences.getInstance();
      // Region form is not a canonical code — "no explicit choice".
      expect(LocalePreferences.readValidLanguageCode(prefs), isNull);

      SharedPreferences.setMockInitialValues(<String, Object>{
        LocalePreferences.languageKey: 'de',
      });
      prefs = await SharedPreferences.getInstance();
      expect(LocalePreferences.readValidLanguageCode(prefs), isNull);

      SharedPreferences.setMockInitialValues(<String, Object>{});
      prefs = await SharedPreferences.getInstance();
      expect(LocalePreferences.readValidLanguageCode(prefs), isNull);
    });

    test('rejects unsupported codes on write', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final prefs = await SharedPreferences.getInstance();
      expect(
        () => LocalePreferences.writeLanguageCode(prefs, 'de'),
        throwsArgumentError,
      );
    });
  });

  group('LocaleController (with provider container)', () {
    late ProviderContainer container;
    late SharedPreferences prefs;

    setUp(() async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      prefs = await SharedPreferences.getInstance();
      container = ProviderContainer(overrides: <Override>[
        sharedPreferencesProvider.overrideWithValue(prefs),
        deviceLocalesProvider.overrideWithValue(const [Locale('en')]),
      ]);
      addTearDown(container.dispose);
    });

    test('defaults to English when no saved choice and device is en', () {
      expect(container.read(localeControllerProvider).locale.languageCode,
          'en');
      expect(container.read(localeControllerProvider).saveStatus,
          LocaleSaveStatus.idle);
    });

    test('restores the saved choice on startup', () async {
      await prefs.setString(LocalePreferences.languageKey, 'hi');
      final fresh = ProviderContainer(overrides: <Override>[
        sharedPreferencesProvider.overrideWithValue(prefs),
        deviceLocalesProvider.overrideWithValue(const [Locale('en')]),
      ]);
      addTearDown(fresh.dispose);
      expect(fresh.read(localeControllerProvider).locale.languageCode, 'hi');
    });

    test('setLocale switches immediately and persists', () async {
      await container
          .read(localeControllerProvider.notifier)
          .setLocale(const Locale('mr'));
      expect(container.read(localeControllerProvider).locale.languageCode,
          'mr');
      expect(container.read(localeControllerProvider).saveStatus,
          LocaleSaveStatus.idle);
      expect(prefs.getString(LocalePreferences.languageKey), 'mr');
    });

    test('rapid selections coalesce — latest wins on UI and disk', () async {
      final notifier = container.read(localeControllerProvider.notifier);
      // No awaits between selections: the writes overlap.
      final first = notifier.setLocale(const Locale('hi'));
      final second = notifier.setLocale(const Locale('mr'));
      await first;
      await second;
      expect(container.read(localeControllerProvider).locale.languageCode,
          'mr');
      expect(prefs.getString(LocalePreferences.languageKey), 'mr');
    });

    test('an older in-flight write cannot restore a superseded locale',
        () async {
      final notifier = container.read(localeControllerProvider.notifier);
      final first = notifier.setLocale(const Locale('hi'));
      final second = notifier.setLocale(const Locale('mr'));
      // First write completes LAST — the revision guard must drop its state
      // update so 'hi' cannot overwrite 'mr'.
      await second;
      await first;
      expect(container.read(localeControllerProvider).locale.languageCode,
          'mr');
      expect(prefs.getString(LocalePreferences.languageKey), 'mr');
    });

    test('storage failure keeps the session usable and reports failed',
        () async {
      final failing = ProviderContainer(overrides: <Override>[
        // Null prefs => persistence unavailable (startup preload failed).
        sharedPreferencesProvider.overrideWithValue(null),
        deviceLocalesProvider.overrideWithValue(const [Locale('en')]),
      ]);
      addTearDown(failing.dispose);
      await failing
          .read(localeControllerProvider.notifier)
          .setLocale(const Locale('hi'));
      final state = failing.read(localeControllerProvider);
      expect(state.locale.languageCode, 'hi');
      expect(state.saveStatus, LocaleSaveStatus.failed);
    });

    test('retrySave persists after storage recovers', () async {
      // A mutable holder lets the same container simulate storage becoming
      // available again — exactly the state retrySave is designed for.
      SharedPreferences? holder;
      final container2 = ProviderContainer(overrides: <Override>[
        sharedPreferencesProvider.overrideWith((ref) => holder),
        deviceLocalesProvider.overrideWithValue(const [Locale('en')]),
      ]);
      addTearDown(container2.dispose);

      await container2
          .read(localeControllerProvider.notifier)
          .setLocale(const Locale('hi'));
      expect(container2.read(localeControllerProvider).saveStatus,
          LocaleSaveStatus.failed);
      expect(prefs.getString(LocalePreferences.languageKey), isNull);

      // Storage "recovers", then the user taps Retry.
      holder = prefs;
      await container2.read(localeControllerProvider.notifier).retrySave();
      expect(container2.read(localeControllerProvider).saveStatus,
          LocaleSaveStatus.idle);
      expect(prefs.getString(LocalePreferences.languageKey), 'hi');
    });
  });
}
