import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/models/battery_health.dart';
import 'package:yantrago/models/fault_interval.dart';
import 'package:yantrago/models/machine_activity.dart';
import 'package:yantrago/models/telemetry_series.dart';

/// Time range for analytics graphs — chips on the Analytics page.
enum AnalyticsRange { today, days7, days30 }

extension AnalyticsRangeWindow on AnalyticsRange {
  /// Inclusive window ending now.
  (DateTime, DateTime) window() {
    final to = DateTime.now();
    final from = switch (this) {
      AnalyticsRange.today => DateTime(to.year, to.month, to.day),
      AnalyticsRange.days7 => to.subtract(const Duration(days: 7)),
      AnalyticsRange.days30 => to.subtract(const Duration(days: 30)),
    };
    return (from, to);
  }
}

/// Currently selected range chip. Default: Today.
final analyticsRangeProvider =
    StateProvider<AnalyticsRange>((ref) => AnalyticsRange.today);

/// Machine selected in the Analytics dropdown. null = auto-select the
/// first machine once the list loads.
final analyticsMachineIdProvider = StateProvider<String?>((ref) => null);

/// Effective machine id — explicit selection, else the first machine.
/// A stale selection (machine unassigned since) falls back to the
/// first machine so the dropdown never holds an invalid value.
final effectiveAnalyticsMachineProvider = Provider<String?>((ref) {
  final selected = ref.watch(analyticsMachineIdProvider);
  final machines = ref.watch(machineListProvider).valueOrNull;
  if (machines == null || machines.isEmpty) return null;
  if (selected != null && machines.any((m) => m.id == selected)) {
    return selected;
  }
  return machines.first.id;
});

/// Historical telemetry for the selected machine + range.
/// Re-fetches when either changes or the user changes.
final telemetrySeriesProvider =
    FutureProvider<TelemetrySeries>((ref) async {
  final user = ref.watch(currentUserProvider);
  final machineId = ref.watch(effectiveAnalyticsMachineProvider);
  if (user == null || machineId == null) return TelemetrySeries.empty;

  final (from, to) = ref.watch(analyticsRangeProvider).window();
  final dio = ref.watch(apiClientProvider);
  final response = await dio.get(
    '/api/v1/telemetry/$machineId',
    queryParameters: {
      'from': from.toIso8601String(),
      'to': to.toIso8601String(),
    },
  );
  if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
    return TelemetrySeries.fromJson(response.data as Map<String, dynamic>);
  }
  return TelemetrySeries.empty;
});

Map<String, String> _rangeParams(AnalyticsRange range) {
  final (from, to) = range.window();
  return {'from': from.toIso8601String(), 'to': to.toIso8601String()};
}

/// FENCE_FAULT incidents for the selected machine + range.
final faultTimelineProvider =
    FutureProvider<List<FaultInterval>>((ref) async {
  final user = ref.watch(currentUserProvider);
  final machineId = ref.watch(effectiveAnalyticsMachineProvider);
  if (user == null || machineId == null) return const [];

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get(
    '/api/v1/machines/$machineId/faults',
    queryParameters: _rangeParams(ref.watch(analyticsRangeProvider)),
  );
  if (response.statusCode == 200 && response.data is List) {
    return [
      for (final item in response.data as List)
        if (item is Map<String, dynamic>) FaultInterval.fromJson(item),
    ];
  }
  return const [];
});

/// Ignition sessions + command markers for the selected machine + range.
final machineActivityProvider =
    FutureProvider<MachineActivity>((ref) async {
  final user = ref.watch(currentUserProvider);
  final machineId = ref.watch(effectiveAnalyticsMachineProvider);
  if (user == null || machineId == null) return MachineActivity.empty;

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get(
    '/api/v1/machines/$machineId/sessions',
    queryParameters: _rangeParams(ref.watch(analyticsRangeProvider)),
  );
  if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
    return MachineActivity.fromJson(response.data as Map<String, dynamic>);
  }
  return MachineActivity.empty;
});

/// Smart Battery Health — score, trend insight, and projection for the
/// battery card. Deliberately independent of the selected range chip:
/// the backend analyzes a fixed 7-day window since trend detection
/// needs multi-day history.
final batteryHealthProvider = FutureProvider<BatteryHealth>((ref) async {
  final user = ref.watch(currentUserProvider);
  final machineId = ref.watch(effectiveAnalyticsMachineProvider);
  if (user == null || machineId == null) {
    return const BatteryHealth(
        score: -1, status: 'INSUFFICIENT_DATA', insight: 'INSUFFICIENT_DATA');
  }

  final dio = ref.watch(apiClientProvider);
  final response =
      await dio.get('/api/v1/machines/$machineId/battery-health');
  if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
    return BatteryHealth.fromJson(response.data as Map<String, dynamic>);
  }
  return const BatteryHealth(
      score: -1, status: 'INSUFFICIENT_DATA', insight: 'INSUFFICIENT_DATA');
});
