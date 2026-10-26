// Tests for the automatic ON/OFF schedule feature — model logic and the
// ScheduleCard widget states.
//
// Covers: day bitmask selection/toggling, the cross-midnight "+1 day"
// indicator, JSON parsing of the backend DTO, and the card's
// configured/empty presentations.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/schedules/models/machine_schedule.dart';
import 'package:yantrago/features/schedules/providers/schedule_provider.dart';
import 'package:yantrago/features/schedules/widgets/schedule_card.dart';

MachineSchedule _schedule({
  TimeOfDay on = const TimeOfDay(hour: 18, minute: 0),
  TimeOfDay off = const TimeOfDay(hour: 7, minute: 0),
  int days = 127,
  bool enabled = true,
  ScheduleRun? lastRun,
}) {
  return MachineSchedule(
    id: 's1',
    machineId: 'm1',
    onTime: on,
    offTime: off,
    daysOfWeek: days,
    timezone: 'Asia/Kolkata',
    enabled: enabled,
    nextOnAt: DateTime(2026, 10, 5, 18, 0),
    nextOffAt: DateTime(2026, 10, 6, 7, 0),
    lastRun: lastRun,
  );
}

void main() {
  group('MachineSchedule model', () {
    test('parses backend DTO incl. HH:mm:ss times and nested lastRun', () {
      final s = MachineSchedule.fromJson({
        'id': 's1',
        'machineId': 'm1',
        'onTime': '18:00:00',
        'offTime': '07:00:00',
        'daysOfWeek': 31,
        'timezone': 'Asia/Kolkata',
        'enabled': true,
        'nextOnAt': '2026-10-05T18:00:00',
        'nextOffAt': '2026-10-06T07:00:00',
        'lastRun': {
          'id': 'r1',
          'commandType': 'OFF',
          'status': 'ACKED',
          'attemptCount': 1,
          'scheduledSlot': '2026-10-05T07:00:00',
        },
      });
      expect(s.onTime, const TimeOfDay(hour: 18, minute: 0));
      expect(s.offTime, const TimeOfDay(hour: 7, minute: 0));
      expect(s.daysOfWeek, 31);
      expect(s.lastRun!.commandType, 'OFF');
      expect(s.lastRun!.isSuccess, isTrue);
    });

    test('day bitmask: Mon=1 .. Sun=64, selection and toggle', () {
      final s = _schedule(days: 0);
      expect(s.isDaySelected(DateTime.monday), isFalse);
      expect(MachineSchedule.toggleDay(0, DateTime.monday, true), 1);
      expect(MachineSchedule.toggleDay(0, DateTime.sunday, true), 64);
      expect(MachineSchedule.toggleDay(127, DateTime.wednesday, false),
          127 & ~4);
      expect(_schedule(days: 127).isDaySelected(DateTime.sunday), isTrue);
      expect(_schedule().isDaily, isTrue);
    });

    test('crossesMidnight: OFF <= ON means next-day OFF', () {
      expect(_schedule().crossesMidnight, isTrue);
      expect(
        _schedule(
          on: const TimeOfDay(hour: 8, minute: 0),
          off: const TimeOfDay(hour: 17, minute: 0),
        ).crossesMidnight,
        isFalse,
      );
      // Equal times also count as next-day (backend treats off<=on as +1).
      expect(
        _schedule(
          on: const TimeOfDay(hour: 9, minute: 0),
          off: const TimeOfDay(hour: 9, minute: 0),
        ).crossesMidnight,
        isTrue,
      );
    });

    test('toUpsertJson emits HH:mm times and the day mask', () {
      final s = _schedule(days: 31);
      final json = s.toUpsertJson();
      expect(json['onTime'], '18:00');
      expect(json['offTime'], '07:00');
      expect(json['daysOfWeek'], 31);
      expect(json['timezone'], 'Asia/Kolkata');
      expect(json['enabled'], isTrue);
    });
  });

  group('ScheduleCard', () {
    Widget _app(AsyncValue<MachineSchedule?> schedule) {
      return ProviderScope(
        overrides: [
          machineScheduleProvider
              .overrideWith((ref, machineId) async => schedule.valueOrNull),
        ],
        child: MaterialApp(
          theme: AppTheme.lightTheme,
          home: const Scaffold(body: ScheduleCard(machineId: 'm1')),
        ),
      );
    }

    testWidgets('empty state shows the setup call-to-action', (tester) async {
      await tester.pumpWidget(_app(const AsyncData(null)));
      await tester.pumpAndSettle();
      expect(find.text('Auto Schedule'), findsOneWidget);
      expect(find.text('Set up automatic ON/OFF'), findsOneWidget);
    });

    testWidgets('configured state shows times, days and the edit action',
        (tester) async {
      await tester.pumpWidget(_app(AsyncData(_schedule())));
      await tester.pumpAndSettle();
      expect(find.text('Auto Schedule'), findsOneWidget);
      expect(find.text('Daily'), findsOneWidget);
      expect(find.byType(Switch), findsOneWidget);
      expect(find.text('Edit'), findsOneWidget);
      // Cross-midnight indicator: OFF (07:00) <= ON (18:00).
      expect(find.textContaining('+1 day'), findsOneWidget);
    });
  });
}
