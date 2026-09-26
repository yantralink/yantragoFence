import '../core/utils/date_utils.dart';

/// A single time-series reading.
class TelemetryPoint {
  final DateTime at;
  final double value;

  const TelemetryPoint(this.at, this.value);
}

/// Historical telemetry for a machine — maps GET
/// /api/v1/telemetry/{machineId}?from&to which returns
/// voltageReadings / batteryReadings / gsmReadings lists.
/// GSM is parsed but unused on the Analytics screen today.
class TelemetrySeries {
  final List<TelemetryPoint> voltage;
  final List<TelemetryPoint> batteryPct;
  final List<TelemetryPoint> gsmSignal;

  const TelemetrySeries({
    this.voltage = const [],
    this.batteryPct = const [],
    this.gsmSignal = const [],
  });

  factory TelemetrySeries.fromJson(Map<String, dynamic> json) {
    return TelemetrySeries(
      voltage: _parse(json['voltageReadings'], 'voltage'),
      batteryPct: _parse(json['batteryReadings'], 'batteryPct'),
      gsmSignal: _parse(json['gsmReadings'], 'gsmSignal'),
    );
  }

  static List<TelemetryPoint> _parse(Object? list, String valueKey) {
    if (list is! List) return const [];
    final points = <TelemetryPoint>[];
    for (final item in list) {
      if (item is! Map<String, dynamic>) continue;
      final at = AppDateUtils.parse(item['recordedAt'] as String?);
      final value = (item[valueKey] as num?)?.toDouble();
      if (at == null || value == null) continue;
      points.add(TelemetryPoint(at, value));
    }
    return points;
  }

  static const TelemetrySeries empty = TelemetrySeries();
}
