import '../core/utils/date_utils.dart';
import 'telemetry_series.dart';

/// Rule-based battery health from
/// GET /api/v1/machines/{machineId}/battery-health.
/// [score] is -1 when there is too little data; [projection] is the
/// dotted trend-line overlay for the chart (empty unless declining).
class BatteryHealth {
  final int score;
  final String status;
  final String insight;
  final double? latestVoltage;
  final double? slopeMvPerDay;
  final int? estimatedDaysUntilLow;
  final List<TelemetryPoint> projection;

  const BatteryHealth({
    required this.score,
    required this.status,
    required this.insight,
    this.latestVoltage,
    this.slopeMvPerDay,
    this.estimatedDaysUntilLow,
    this.projection = const [],
  });

  factory BatteryHealth.fromJson(Map<String, dynamic> json) {
    return BatteryHealth(
      score: (json['score'] as num?)?.toInt() ?? -1,
      status: json['status'] as String? ?? 'INSUFFICIENT_DATA',
      insight: json['insight'] as String? ?? 'INSUFFICIENT_DATA',
      latestVoltage: (json['latestVoltage'] as num?)?.toDouble(),
      slopeMvPerDay: (json['slopeMvPerDay'] as num?)?.toDouble(),
      estimatedDaysUntilLow:
          (json['estimatedDaysUntilLow'] as num?)?.toInt(),
      projection: _projection(json['projection']),
    );
  }

  static List<TelemetryPoint> _projection(Object? list) {
    if (list is! List) return const [];
    return [
      for (final item in list)
        if (item is Map<String, dynamic> &&
            AppDateUtils.parse(item['at'] as String?) != null &&
            item['voltage'] is num)
          TelemetryPoint(
            AppDateUtils.parse(item['at'] as String?)!,
            (item['voltage'] as num).toDouble(),
          ),
    ];
  }
}
