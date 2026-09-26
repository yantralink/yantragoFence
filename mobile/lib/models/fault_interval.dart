import '../core/utils/date_utils.dart';

/// One FENCE_FAULT incident returned by
/// GET /api/v1/machines/{machineId}/faults.
/// [resolvedAt] is null and [ongoing] true while the incident is open.
class FaultInterval {
  final String id;
  final DateTime triggeredAt;
  final DateTime? resolvedAt;
  final String incidentState;
  final int occurrenceCount;
  final int durationMinutes;
  final bool ongoing;

  const FaultInterval({
    required this.id,
    required this.triggeredAt,
    this.resolvedAt,
    this.incidentState = '',
    this.occurrenceCount = 1,
    this.durationMinutes = 0,
    this.ongoing = false,
  });

  factory FaultInterval.fromJson(Map<String, dynamic> json) {
    return FaultInterval(
      id: json['id'] as String? ?? '',
      triggeredAt:
          AppDateUtils.parse(json['triggeredAt'] as String?) ??
              DateTime.fromMillisecondsSinceEpoch(0),
      resolvedAt: AppDateUtils.parse(json['resolvedAt'] as String?),
      incidentState: json['incidentState'] as String? ?? '',
      occurrenceCount: (json['occurrenceCount'] as num?)?.toInt() ?? 1,
      durationMinutes: (json['durationMinutes'] as num?)?.toInt() ?? 0,
      ongoing: json['ongoing'] as bool? ?? false,
    );
  }
}
