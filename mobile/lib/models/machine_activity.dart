import '../core/utils/date_utils.dart';

/// One ignition-ON session returned inside the sessions response.
/// [endAt] is null and [ongoing] true while the session is open.
class MachineSession {
  final DateTime startAt;
  final DateTime? endAt;
  final int durationMinutes;
  final bool ongoing;

  const MachineSession({
    required this.startAt,
    this.endAt,
    this.durationMinutes = 0,
    this.ongoing = false,
  });

  factory MachineSession.fromJson(Map<String, dynamic> json) {
    return MachineSession(
      startAt: AppDateUtils.parse(json['startAt'] as String?) ??
          DateTime.fromMillisecondsSinceEpoch(0),
      endAt: AppDateUtils.parse(json['endAt'] as String?),
      durationMinutes: (json['durationMinutes'] as num?)?.toInt() ?? 0,
      ongoing: json['ongoing'] as bool? ?? false,
    );
  }
}

/// A manual ON/OFF command shown as a tick on the activity strip.
class CommandMarker {
  final String id;
  final String commandType;
  final String status;
  final DateTime issuedAt;
  final DateTime? completedAt;

  const CommandMarker({
    required this.id,
    required this.commandType,
    required this.status,
    required this.issuedAt,
    this.completedAt,
  });

  factory CommandMarker.fromJson(Map<String, dynamic> json) {
    return CommandMarker(
      id: json['id'] as String? ?? '',
      commandType: json['commandType'] as String? ?? '',
      status: json['status'] as String? ?? '',
      issuedAt: AppDateUtils.parse(json['issuedAt'] as String?) ??
          DateTime.fromMillisecondsSinceEpoch(0),
      completedAt: AppDateUtils.parse(json['completedAt'] as String?),
    );
  }
}

/// Payload of GET /api/v1/machines/{machineId}/sessions —
/// derived ignition sessions plus manual command markers.
class MachineActivity {
  final List<MachineSession> sessions;
  final List<CommandMarker> commandMarkers;

  const MachineActivity({
    this.sessions = const [],
    this.commandMarkers = const [],
  });

  factory MachineActivity.fromJson(Map<String, dynamic> json) {
    return MachineActivity(
      sessions: _parseList(json['sessions'], MachineSession.fromJson),
      commandMarkers:
          _parseList(json['commandMarkers'], CommandMarker.fromJson),
    );
  }

  static List<T> _parseList<T>(
      Object? list, T Function(Map<String, dynamic>) fromJson) {
    if (list is! List) return const [];
    return [
      for (final item in list)
        if (item is Map<String, dynamic>) fromJson(item),
    ];
  }

  /// Total ON-time across all sessions in the range.
  int get totalOnMinutes =>
      sessions.fold(0, (sum, s) => sum + s.durationMinutes);

  static const MachineActivity empty = MachineActivity();
}
