import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/features/machines/models/forecast_day_grouper.dart';
import 'package:yantrago/models/weather_forecast.dart';

ForecastSlot slot(String iso, double? temp, int? code,
        {String icon = '01d'}) =>
    ForecastSlot(
        forecastAt: iso,
        temperatureCelsius: temp,
        conditionCode: code,
        conditionIcon: icon);

void main() {
  // Fixed "now": 2026-11-05 10:00 UTC; localOf = UTC for determinism.
  final now = DateTime.utc(2026, 11, 5, 10);
  DateTime utcOf(DateTime d) => d;

  test('groups slots into local days, caps at 5, drops past days', () {
    final slots = [
      slot('2026-11-04T20:00:00Z', 20, 800), // yesterday — dropped
      slot('2026-11-05T03:00:00Z', 22, 800),
      slot('2026-11-05T06:00:00Z', 25, 800),
      slot('2026-11-05T09:00:00Z', 28, 801),
      slot('2026-11-06T03:00:00Z', 21, 500),
      slot('2026-11-10T03:00:00Z', 19, 800), // day+5 — outside window
    ];
    final days = groupForecastIntoDays(slots,
        now: now, localOf: utcOf);
    expect(days.length, 2);
    expect(days[0].date, DateTime(2026, 11, 5));
    expect(days[0].tempMinCelsius, 22);
    expect(days[0].tempMaxCelsius, 28);
    // partial: only 3 slots < 8
    expect(days[0].isPartial, isTrue);
    expect(days[1].date, DateTime(2026, 11, 6));
    expect(days[1].conditionCode, 500);
  });

  test('dedupes identical instants and skips malformed timestamps', () {
    final slots = [
      slot('2026-11-05T03:00:00Z', 22, 800),
      slot('2026-11-05T03:00:00Z', 99, 800), // dupe — ignored
      slot('not-a-date', 30, 800),           // malformed — skipped
    ];
    final days = groupForecastIntoDays(slots, now: now, localOf: utcOf);
    expect(days.length, 1);
    expect(days[0].tempMaxCelsius, 22); // dupe's 99 must not win
  });

  test('all-negative temps produce correct min/max', () {
    final slots = [
      slot('2026-11-05T03:00:00Z', -5, 800),
      slot('2026-11-05T06:00:00Z', -12, 800),
      slot('2026-11-05T09:00:00Z', -3, 800),
    ];
    final days = groupForecastIntoDays(slots, now: now, localOf: utcOf);
    expect(days.single.tempMinCelsius, -12);
    expect(days.single.tempMaxCelsius, -3);
  });

  test('omits day with no temp and no condition', () {
    final slots = [
      slot('2026-11-05T03:00:00Z', null, null),
    ];
    expect(groupForecastIntoDays(slots, now: now, localOf: utcOf), isEmpty);
  });

  test('condition tie breaks to sample closest to local noon', () {
    // 02:00 and 22:00 both Clear; 12:00 Rain wins? no — equal freq,
    // noon sample is Rain only if it has equal frequency; use 2v2.
    final slots = [
      slot('2026-11-05T02:00:00Z', 10, 800), // Clear, far from noon
      slot('2026-11-05T12:00:00Z', 15, 500), // Rain, at noon
      slot('2026-11-05T13:00:00Z', 14, 800), // Clear, near noon
      slot('2026-11-05T14:00:00Z', 13, 500), // Rain, near noon
    ];
    final days = groupForecastIntoDays(slots, now: now, localOf: utcOf);
    // freq 2v2; noon distances: Clear min(|02-12|=600,|13-12|=60)=60,
    // Rain min(|12-12|=0,|14-12|=120)=0 → Rain wins
    expect(days.single.conditionCode, 500);
  });

  test('respects user local timezone, not UTC day', () {
    // Slot at 18:30 UTC = next day 00:00 in UTC+5:30.
    DateTime ist(DateTime d) =>
        d.toUtc().add(const Duration(hours: 5, minutes: 30));
    final slots = [slot('2026-11-05T18:30:00Z', 20, 800)];
    // now = Nov 6 (local calendar). Slot = Nov 6 00:00 IST → kept as
    // Nov 6. Under UTC grouping it would be Nov 5 → dropped, so this
    // proves local-calendar grouping.
    final days = groupForecastIntoDays(slots,
        now: DateTime(2026, 11, 6, 20), localOf: ist);
    expect(days.single.date, DateTime(2026, 11, 6));
  });
}
