// Widget tests for the Analytics Battery Health card (Phase 2).

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/analytics/widgets/battery_health_card.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/models/telemetry_series.dart';

Future<void> _pump(WidgetTester tester, Widget child) async {
  await tester.binding.setSurfaceSize(const Size(390, 600));
  await tester.pumpWidget(MaterialApp(
    theme: AppTheme.lightTheme,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(body: SingleChildScrollView(child: child)),
  ));
  await tester.pump(const Duration(milliseconds: 100));
}

TelemetryPoint _v(int minute, double volts) =>
    TelemetryPoint(DateTime(2026, 9, 26, 10, minute), volts);

void main() {
  group('BatteryHealthCard', () {
    testWidgets('empty series shows the no-data empty state', (tester) async {
      await _pump(tester, const BatteryHealthCard(voltage: []));
      expect(find.text('No data in this period'), findsOneWidget);
      expect(find.text('External Battery Health'), findsOneWidget);
    });

    testWidgets('healthy latest voltage shows Healthy chip', (tester) async {
      await _pump(tester, BatteryHealthCard(voltage: [
        _v(0, 12.6), _v(5, 12.7), _v(10, 12.8), _v(15, 12.9),
      ]));
      expect(find.text('Healthy'), findsOneWidget);
      expect(find.text('Now: 12.9 V'), findsOneWidget);
    });

    testWidgets('below-band voltage shows Low chip', (tester) async {
      await _pump(tester, BatteryHealthCard(voltage: [
        _v(0, 12.4), _v(5, 12.1), _v(10, 11.5),
      ]));
      expect(find.text('Low'), findsOneWidget);
      expect(find.text('Now: 11.5 V'), findsOneWidget);
    });

    testWidgets('above-band voltage shows High chip', (tester) async {
      await _pump(tester, BatteryHealthCard(voltage: [
        _v(0, 14.1), _v(5, 14.4),
      ]));
      expect(find.text('High'), findsOneWidget);
    });
  });

  group('TelemetrySeries', () {
    test('parses voltage, battery, and gsm lists; skips bad rows', () {
      final series = TelemetrySeries.fromJson({
        'voltageReadings': [
          {'recordedAt': '2026-09-26T10:00:00', 'voltage': 12.8},
          {'recordedAt': 'bad', 'voltage': 12.9},
          {'recordedAt': '2026-09-26T10:05:00'},
        ],
        'batteryReadings': [
          {'recordedAt': '2026-09-26T10:00:00', 'batteryPct': 100.0},
        ],
        'gsmReadings': [],
      });
      expect(series.voltage.length, 1);
      expect(series.voltage.first.value, 12.8);
      expect(series.batteryPct.single.value, 100.0);
      expect(series.gsmSignal, isEmpty);
    });

    test('non-map or missing payload yields empty series', () {
      expect(TelemetrySeries.fromJson({}).voltage, isEmpty);
    });
  });
}
