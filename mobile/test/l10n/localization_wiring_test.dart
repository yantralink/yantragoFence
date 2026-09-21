// Widget test proving the MaterialApp localization wiring (Phase 1).
//
// Pumps a minimal localized widget inside a MaterialApp with the
// AppLocalizations delegates installed and verifies the context lookup
// resolves English strings.

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/l10n/l10n.dart';

class _Probe extends ConsumerWidget {
  const _Probe();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locale = ref.watch(localeControllerProvider).locale;
    return Directionality(
      textDirection: TextDirection.ltr,
      child: Column(
        children: <Widget>[
          Text(context.l10n.navMachines),
          Text(context.l10n.loginButton),
          Text(locale.languageCode),
        ],
      ),
    );
  }
}

Future<void> _pump(WidgetTester tester, Locale locale) async {
  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        locale: locale,
        localizationsDelegates: const <LocalizationsDelegate<Object>>[
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: const _Probe(),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  testWidgets('AppLocalizations resolves English via the delegates',
      (WidgetTester tester) async {
    await _pump(tester, const Locale('en'));

    expect(find.text('Machines'), findsOneWidget);
    expect(find.text('Login'), findsOneWidget);
    expect(find.text('en'), findsOneWidget);
  });

  testWidgets('context.l10n falls back to English without delegates',
      (WidgetTester tester) async {
    // Plain MaterialApp — no localization delegates installed.
    await tester.pumpWidget(
      const MaterialApp(
        home: Directionality(
          textDirection: TextDirection.ltr,
          child: Text('noop'),
        ),
      ),
    );
    // The fallback path is exercised by every widget test that pumps a
    // page without delegates; assert one translated string directly.
    final context = tester.element(find.text('noop'));
    expect(context.l10n.navMachines, 'Machines');
  });
}
