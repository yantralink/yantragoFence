import 'package:flutter/material.dart';

import 'package:yantrago/core/utils/date_utils.dart';

/// One schedule execution record (schedule_runs) — returned by
/// GET /api/v1/machines/{id}/schedule/runs.
///
/// Statuses: PENDING (claimed, not yet dispatched), DISPATCHED (command sent,
/// awaiting device), ACKED (device confirmed), FAILED (terminal after
/// retries), MISSED (stale ON slot — never fired for safety).
class ScheduleRun {
  final String id;
  final String commandType;
  final String status;
  final int attemptCount;
  final String? error;
  final DateTime scheduledSlot;
  final DateTime? createdAt;

  const ScheduleRun({
    required this.id,
    required this.commandType,
    required this.status,
    this.attemptCount = 0,
    this.error,
    required this.scheduledSlot,
    this.createdAt,
  });

  factory ScheduleRun.fromJson(Map<String, dynamic> json) {
    return ScheduleRun(
      id: json['id'] as String,
      commandType: json['commandType'] as String,
      status: json['status'] as String? ?? 'PENDING',
      attemptCount: (json['attemptCount'] as num?)?.toInt() ?? 0,
      error: json['error'] as String?,
      scheduledSlot: AppDateUtils.parseStrict(json['scheduledSlot'] as String),
      createdAt: AppDateUtils.parse(json['createdAt'] as String?),
    );
  }

  bool get isSuccess => status == 'ACKED';
  bool get isInFlight => status == 'PENDING' || status == 'DISPATCHED';
  bool get isFailed => status == 'FAILED';
  bool get isMissed => status == 'MISSED';
}

/// Automatic ON/OFF schedule for a machine (one schedule per machine).
///
/// Execution is server-side — this model is display + edit data only.
/// Per the feature design: [daysOfWeek] selects the ON days; the paired OFF
/// fires the next morning even on unselected days (cycle semantics), which
/// is why [crossesMidnight] is derived from the two times.
class MachineSchedule {
  final String id;
  final String machineId;
  final TimeOfDay onTime;
  final TimeOfDay offTime;
  final int daysOfWeek;
  final String timezone;
  final bool enabled;
  final DateTime? nextOnAt;
  final DateTime? nextOffAt;
  final ScheduleRun? lastRun;

  const MachineSchedule({
    required this.id,
    required this.machineId,
    required this.onTime,
    required this.offTime,
    required this.daysOfWeek,
    required this.timezone,
    required this.enabled,
    this.nextOnAt,
    this.nextOffAt,
    this.lastRun,
  });

  static TimeOfDay _tod(String raw) {
    final parts = raw.trim().split(':');
    return TimeOfDay(hour: int.parse(parts[0]), minute: int.parse(parts[1]));
  }

  static String _hhmm(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  factory MachineSchedule.fromJson(Map<String, dynamic> json) {
    return MachineSchedule(
      id: json['id'] as String,
      machineId: json['machineId'] as String,
      onTime: _tod(json['onTime'] as String),
      offTime: _tod(json['offTime'] as String),
      daysOfWeek: (json['daysOfWeek'] as num).toInt(),
      timezone: json['timezone'] as String? ?? 'Asia/Kolkata',
      enabled: json['enabled'] as bool? ?? false,
      nextOnAt: AppDateUtils.parse(json['nextOnAt'] as String?),
      nextOffAt: AppDateUtils.parse(json['nextOffAt'] as String?),
      lastRun: json['lastRun'] != null
          ? ScheduleRun.fromJson(json['lastRun'] as Map<String, dynamic>)
          : null,
    );
  }

  /// Body for PUT /api/v1/machines/{id}/schedule (upsert semantics).
  Map<String, dynamic> toUpsertJson() => {
        'onTime': _hhmm(onTime),
        'offTime': _hhmm(offTime),
        'daysOfWeek': daysOfWeek,
        'timezone': timezone,
        'enabled': enabled,
      };

  static int _mins(TimeOfDay t) => t.hour * 60 + t.minute;

  /// True when OFF is earlier than or equal to ON — OFF fires the next
  /// calendar day ("+1 day" indicator in the editor).
  bool get crossesMidnight => _mins(offTime) <= _mins(onTime);

  /// Whether [weekday] (DateTime.monday=1 … DateTime.sunday=7) is selected.
  bool isDaySelected(int weekday) => daysOfWeek & (1 << (weekday - 1)) != 0;

  /// Day mask with [weekday] added or removed (Mon=1 … Sun=7).
  static int toggleDay(int mask, int weekday, bool selected) {
    final bit = 1 << (weekday - 1);
    return selected ? mask | bit : mask & ~bit;
  }

  bool get isDaily => daysOfWeek == 127;

  MachineSchedule copyWith({
    TimeOfDay? onTime,
    TimeOfDay? offTime,
    int? daysOfWeek,
    String? timezone,
    bool? enabled,
  }) {
    return MachineSchedule(
      id: id,
      machineId: machineId,
      onTime: onTime ?? this.onTime,
      offTime: offTime ?? this.offTime,
      daysOfWeek: daysOfWeek ?? this.daysOfWeek,
      timezone: timezone ?? this.timezone,
      enabled: enabled ?? this.enabled,
      nextOnAt: nextOnAt,
      nextOffAt: nextOffAt,
      lastRun: lastRun,
    );
  }
}
