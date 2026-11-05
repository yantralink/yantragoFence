import 'package:yantrago/models/daily_forecast.dart';
import 'package:yantrago/models/weather_forecast.dart';

/// Groups normalized UTC forecast slots into local-calendar daily
/// summaries (docs/EXPANDABLE_5_DAY_WEATHER_FORECAST_PLAN.md §3).
///
/// Pure function — the provider owns the transformation; widgets only
/// display the result. [now] and [localOf] are injectable so tests can
/// exercise timezone/DST boundaries deterministically.
///
/// Rules:
/// - Slots sorted ascending, identical instants deduplicated.
/// - Days are the USER's local calendar days (device timezone), never
///   UTC dates or the machine's provider timezone.
/// - Only dates within [today] .. [today]+4 are kept — at most 5 rows,
///   fewer when the window is partial. Missing days are never filled.
/// - High/low are extrema of valid sampled temps (nullable-safe,
///   all-negative temps work).
/// - isPartial = fewer than 8 distinct slots contributed to the day.
/// - Condition = most frequent valid conditionCode; ties break to the
///   sample closest to local noon, then the earlier timestamp.
/// - A day with no usable temp AND no usable condition is omitted.
List<DailyForecast> groupForecastIntoDays(
  List<ForecastSlot> slots, {
  required DateTime now,
  DateTime Function(DateTime utc)? localOf,
}) {
  final toLocal = localOf ?? (d) => d.toLocal();
  final today = DateTime(now.year, now.month, now.day);
  final lastDay = DateTime(today.year, today.month, today.day + 4);

  // Parse, validate, dedupe, sort.
  final seen = <int>{};
  final valid = <({DateTime local, ForecastSlot slot})>[];
  for (final s in slots) {
    final utc = s.instant;
    if (utc == null) continue;
    final ms = utc.millisecondsSinceEpoch;
    if (!seen.add(ms)) continue;
    final local = toLocal(utc);
    final day = DateTime(local.year, local.month, local.day);
    if (day.isBefore(today) || day.isAfter(lastDay)) continue;
    valid.add((local: local, slot: s));
  }
  valid.sort((a, b) => a.local.compareTo(b.local));

  // Group by local calendar day.
  final byDay = <DateTime, List<({DateTime local, ForecastSlot slot})>>{};
  for (final v in valid) {
    final day = DateTime(v.local.year, v.local.month, v.local.day);
    byDay.putIfAbsent(day, () => []).add(v);
  }

  final days = <DailyForecast>[];
  for (final entry in byDay.entries) {
    final daySlots = entry.value;
    final temps = daySlots
        .map((v) => v.slot.temperatureCelsius)
        .whereType<double>()
        .toList();
    final cond = _pickCondition(daySlots);
    if (temps.isEmpty && cond == null) continue; // unusable day

    days.add(DailyForecast(
      date: entry.key,
      conditionCode: cond?.code,
      conditionIcon: cond?.icon,
      tempMinCelsius: temps.isEmpty ? null : temps.reduce((a, b) => a < b ? a : b),
      tempMaxCelsius: temps.isEmpty ? null : temps.reduce((a, b) => a > b ? a : b),
      isPartial: daySlots.length < 8,
    ));
  }
  days.sort((a, b) => a.date.compareTo(b.date));
  return days;
}

/// Most frequent conditionCode; ties → sample closest to local noon,
/// then earliest. Returns (code, icon) of the winning sample.
({int code, String? icon})? _pickCondition(
    List<({DateTime local, ForecastSlot slot})> daySlots) {
  final freq = <int, int>{};
  final noonDist = <int, Duration>{};
  final iconByCode = <int, String?>{};
  final firstSeen = <int, int>{};
  var order = 0;

  for (final v in daySlots) {
    final code = v.slot.conditionCode;
    if (code == null) continue;
    iconByCode.putIfAbsent(code, () => v.slot.conditionIcon);
    firstSeen.putIfAbsent(code, () => order++);
    freq[code] = (freq[code] ?? 0) + 1;
    final minutes = v.local.hour * 60 + v.local.minute;
    final dist = Duration(minutes: (minutes - 12 * 60).abs());
    noonDist[code] = noonDist.containsKey(code) && noonDist[code]!.compareTo(dist) <= 0
        ? noonDist[code]!
        : dist;
  }
  if (freq.isEmpty) return null;

  int best = freq.keys.first;
  for (final code in freq.keys) {
    final f = freq[code]!, bf = freq[best]!;
    if (f > bf ||
        (f == bf &&
            (noonDist[code]!.compareTo(noonDist[best]!) < 0 ||
                (noonDist[code] == noonDist[best] &&
                    firstSeen[code]! < firstSeen[best]!)))) {
      best = code;
    }
  }
  return (code: best, icon: iconByCode[best]);
}
