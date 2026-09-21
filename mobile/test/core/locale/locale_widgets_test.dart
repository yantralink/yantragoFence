import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yantrago/core/auth/auth_service.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/locale/locale_preferences.dart';
import 'package:yantrago/core/locale/locale_sync_provider.dart';
import 'package:yantrago/core/widgets/language_picker_row.dart';
import 'package:yantrago/features/profile/pages/language_settings_page.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/models/user.dart';

/// Test-only auth service: the locale sync succeeds instantly.
class _FakeAuthService implements AuthService {
  _FakeAuthService({this.throwTransient = false});

  final bool throwTransient;

  @override
  Future<User> updatePreferredLocale(String locale) async {
    if (throwTransient) {
      throw DioException(
        requestOptions: RequestOptions(path: '/me/locale'),
        type: DioExceptionType.connectionTimeout,
      );
    }
    return User(
      id: 'u1',
      email: 'user@example.com',
      fullName: 'U',
      role: 'CUSTOMER',
      active: true,
      preferredLocale: locale,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Pushes [child] from a host route so Navigator.pop() in the page under
/// test behaves like it does in the real app (there is something to pop).
class _PageHost extends StatelessWidget {
  final Widget child;
  const _PageHost({required this.child});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: FilledButton(
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => child),
          ),
          child: const Text('open'),
        ),
      ),
    );
  }
}

Future<ProviderContainer> _root(
  WidgetTester tester,
  Widget app, {
  SharedPreferences? prefs,
  _FakeAuthService? authService,
}) async {
  late ProviderContainer container;
  await tester.pumpWidget(
    ProviderScope(
      overrides: <Override>[
        sharedPreferencesProvider.overrideWithValue(prefs),
        deviceLocalesProvider.overrideWithValue(const [Locale('en')]),
        authServiceProvider
            .overrideWithValue(authService ?? _FakeAuthService()),
        localeSyncBackoffProvider
            .overrideWithValue(const [Duration.zero, Duration.zero]),
      ],
      child: Builder(builder: (context) {
        container = ProviderScope.containerOf(context);
        return app;
      }),
    ),
  );
  await tester.pump();
  return container;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('picker shows all three languages in native script',
      (tester) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    final prefs = await SharedPreferences.getInstance();
    await _root(
      tester,
      const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: LanguagePickerRow()),
      ),
    );
    expect(find.text('English'), findsOneWidget);
    expect(find.text('हिन्दी (Hindi)'), findsOneWidget);
    expect(find.text('मराठी (Marathi)'), findsOneWidget);
  });

  testWidgets('tapping a choice switches the locale and persists it',
      (tester) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    final prefs = await SharedPreferences.getInstance();
    final container = await _root(
      tester,
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(body: LanguagePickerRow()),
      ),
      prefs: prefs,
    );

    await tester.tap(find.text('हिन्दी (Hindi)'));
    await tester.pumpAndSettle();

    expect(container.read(localeControllerProvider).locale.languageCode, 'hi');
    expect(prefs.getString(LocalePreferences.languageKey), 'hi');
  });

  testWidgets(
      'settings page: failed persistence stays on page and shows retry banner',
      (tester) async {
    // Null prefs => persistence unavailable => the save reports failure
    // instead of claiming restart persistence succeeded.
    final container = await _root(
      tester,
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const _PageHost(child: LanguageSettingsPage()),
      ),
    );

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('हिन्दी (Hindi)'));
    await tester.pump();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    // The UI locale switched, the save is reported failed, and the page did
    // NOT pop (the user was not promised persistence).
    expect(container.read(localeControllerProvider).locale.languageCode, 'hi');
    expect(container.read(localeControllerProvider).saveStatus,
        LocaleSaveStatus.failed);
    expect(find.byType(LanguageSettingsPage), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
  });

  testWidgets(
      'settings page: successful save switches locale, persists, and pops',
      (tester) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    final prefs = await SharedPreferences.getInstance();
    final container = await _root(
      tester,
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: _PageHost(child: const LanguageSettingsPage()),
      ),
    );

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('मराठी (Marathi)'));
    await tester.pump();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(container.read(localeControllerProvider).locale.languageCode, 'mr');
    expect(container.read(localeControllerProvider).saveStatus,
        LocaleSaveStatus.idle);
    expect(prefs.getString(LocalePreferences.languageKey), 'mr');
    // Save popped back to the host page.
    expect(find.byType(LanguageSettingsPage), findsNothing);
  });

  testWidgets(
      'settings page shows sync-pending banner when server sync not confirmed',
      (tester) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    final prefs = await SharedPreferences.getInstance();
    final container = await _root(
      tester,
      const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: LanguageSettingsPage(),
      ),
      prefs: prefs,
      authService: _FakeAuthService(throwTransient: true),
    );

    // A selection made earlier is still un-synced (transient failures).
    await container
        .read(localeSyncProvider.notifier)
        .localeChanged(locale: 'hi', userId: 'u1', organizationId: 'o1');
    await tester.pumpAndSettle();

    final l10n = AppLocalizations('en');
    expect(container.read(localeSyncProvider).status,
        LocaleSyncStatus.pending);
    expect(find.text(l10n.languageSyncPending), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
  });
}
