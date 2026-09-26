// Widget tests for the Analytics Battery Health card (Phase 2).

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/analytics/providers/analytics_provider.dart';
import 'package:yantrago/features/analytics/widgets/activity_timeline_card.dart';
import 'package:yantrago/features/analytics/widgets/battery_health_card.dart';
import 'package:yantrago/features/analytics/widgets/fault_timeline_card.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/models/fault_interval.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/machine_activity.dart';
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

  group('effectiveAnalyticsMachineProvider', () {
    Machine machine(String id) => Machine(
          id: id,
          machineId: 'M-$id',
          name: 'Machine $id',
          status: 'ACTIVE',
          isOnline: true,
          createdAt: DateTime(2026, 1, 1),
        );

    test('defaults to first machine when nothing selected', () async {
      final container = ProviderContainer(overrides: [
        machineListProvider.overrideWith(
            (ref) async => [machine('m1'), machine('m2')]),
      ]);
      addTearDown(container.dispose);
      await container.read(machineListProvider.future);

      expect(container.read(effectiveAnalyticsMachineProvider), 'm1');
    });

    test('stale selection falls back to first machine', () async {
      final container = ProviderContainer(overrides: [
        machineListProvider.overrideWith(
            (ref) async => [machine('m1'), machine('m2')]),
      ]);
      addTearDown(container.dispose);
      await container.read(machineListProvider.future);

      container.read(analyticsMachineIdProvider.notifier).state = 'ghost';
      expect(container.read(effectiveAnalyticsMachineProvider), 'm1');

      container.read(analyticsMachineIdProvider.notifier).state = 'm2';
      expect(container.read(effectiveAnalyticsMachineProvider), 'm2');
    });
  });

  group('FaultTimelineCard', () {
    final start = DateTime(2026, 9, 26);
    final end = DateTime(2026, 9, 27);

    testWidgets('empty state when no faults', (tester) async {
      await _pump(tester, 
          FaultTimelineCard(faults: const [], rangeStart: start, rangeEnd: end));
      expect(find.text('No faults in this period'), findsOneWidget);
    });

    testWidgets('shows fault count chip and detail row', (tester) async {
      final faults = [
        FaultInterval(
          id: 'f1',
          triggeredAt: DateTime(2026, 9, 26, 9, 15),
          resolvedAt: DateTime(2026, 9, 26, 10, 2),
          durationMinutes: 47,
        ),
      ];
      await _pump(tester, FaultTimelineCard(
          faults: faults, rangeStart: start, rangeEnd: end));
      expect(find.text('1 fault'), findsOneWidget);
      expect(find.textContaining('09:15'), findsOneWidget);
      expect(find.text('47 min'), findsOneWidget);
    });

    testWidgets('ongoing fault shows Ongoing chip', (tester) async {
      final faults = [
        FaultInterval(
          id: 'f1',
          triggeredAt: DateTime(2026, 9, 26, 9, 15),
          ongoing: true,
          durationMinutes: 120,
        ),
      ];
      await _pump(tester, FaultTimelineCard(
          faults: faults, rangeStart: start, rangeEnd: end));
      expect(find.text('Ongoing'), findsOneWidget);
    });
  });

  group('ActivityTimelineCard', () {
    final start = DateTime(2026, 9, 26);
    final end = DateTime(2026, 9, 27);

    testWidgets('empty state when no sessions', (tester) async {
      await _pump(tester, ActivityTimelineCard(
          activity: MachineActivity.empty,
          rangeStart: start,
          rangeEnd: end));
      expect(find.text('No activity in this period'), findsOneWidget);
    });

    testWidgets('shows ON-time summary and session rows', (tester) async {
      final activity = MachineActivity(sessions: [
        MachineSession(
          startAt: DateTime(2026, 9, 26, 8, 0),
          endAt: DateTime(2026, 9, 26, 10, 0),
          durationMinutes: 120,
        ),
        MachineSession(
          startAt: DateTime(2026, 9, 26, 14, 0),
          endAt: DateTime(2026, 9, 26, 14, 5),
          durationMinutes: 5,
        ),
      ]);
      await _pump(tester, ActivityTimelineCard(
          activity: activity, rangeStart: start, rangeEnd: end));
      expect(find.textContaining('2 sessions'), findsOneWidget);
      expect(find.textContaining('2h 5m ON'), findsOneWidget);
      expect(find.text('2h 0m'), findsOneWidget);
      expect(find.text('5 min'), findsOneWidget);
    });
  });

  group('Analytics models', () {
    test('FaultInterval parses response fields', () {
      final f = FaultInterval.fromJson({
        'id': 'a-b',
        'triggeredAt': '2026-09-26T09:15:00',
        'resolvedAt': '2026-09-26T10:02:00',
        'incidentState': 'RESOLVED',
        'occurrenceCount': 3,
        'durationMinutes': 47,
        'ongoing': false,
      });
      expect(f.id, 'a-b');
      expect(f.occurrenceCount, 3);
      expect(f.durationMinutes, 47);
      expect(f.ongoing, isFalse);
    });

    test('MachineActivity parses sessions and markers', () {
      final a = MachineActivity.fromJson({
        'sessions': [
          {
            'startAt': '2026-09-26T08:00:00',
            'endAt': null,
            'durationMinutes': 60,
            'ongoing': true,
          }
        ],
        'commandMarkers': [
          {
            'id': 'c1',
            'commandType': 'ON',
            'status': 'DONE',
            'issuedAt': '2026-09-26T07:58:00',
            'completedAt': '2026-09-26T07:58:30',
          }
        ],
      });
      expect(a.sessions, hasLength(1));
      expect(a.sessions.single.ongoing, isTrue);
      expect(a.sessions.single.endAt, isNull);
      expect(a.commandMarkers.single.status, 'DONE');
      expect(a.totalOnMinutes, 60);
    });

    test('MachineActivity tolerates missing payload keys', () {
      final a = MachineActivity.fromJson(const {});
      expect(a.sessions, isEmpty);
      expect(a.commandMarkers, isEmpty);
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
