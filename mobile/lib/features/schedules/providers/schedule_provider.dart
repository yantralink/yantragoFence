import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/schedules/models/machine_schedule.dart';

/// Schedule for a machine — GET /api/v1/machines/{id}/schedule.
///
/// Returns null when no schedule is configured (backend responds 204 with an
/// empty body). Watches [currentUserProvider] so it re-fetches on auth change.
/// Per AGENTS.md rule 7: organization_id comes from JWT, never the request body.
final machineScheduleProvider =
    FutureProvider.family<MachineSchedule?, String>((ref, machineId) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return null;

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/v1/machines/$machineId/schedule');
  final data = response.data;
  if (data == null || (data is Map && data.isEmpty)) return null;
  return MachineSchedule.fromJson(Map<String, dynamic>.from(data as Map));
});

/// Recent schedule execution runs — GET /api/v1/machines/{id}/schedule/runs.
/// Newest first (backend orders by scheduled_slot desc).
final scheduleRunsProvider =
    FutureProvider.family<List<ScheduleRun>, String>((ref, machineId) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return const [];

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get(
    '/api/v1/machines/$machineId/schedule/runs',
    queryParameters: {'page': 0, 'size': 20},
  );
  final data = response.data;
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List<dynamic>
      : data as List<dynamic>;
  return list
      .map((r) => ScheduleRun.fromJson(Map<String, dynamic>.from(r as Map)))
      .toList();
});

/// Mutation helper — PUT upsert, PATCH enabled, DELETE.
/// Callers invalidate [machineScheduleProvider] after a successful call.
class ScheduleActions {
  final Dio _dio;
  const ScheduleActions(this._dio);

  Future<MachineSchedule> upsert(String machineId, MachineSchedule draft) async {
    final response = await _dio.put(
      '/api/v1/machines/$machineId/schedule',
      data: draft.toUpsertJson(),
    );
    return MachineSchedule.fromJson(
        Map<String, dynamic>.from(response.data as Map));
  }

  Future<MachineSchedule> setEnabled(String machineId, bool enabled) async {
    final response = await _dio.patch(
      '/api/v1/machines/$machineId/schedule/enabled',
      data: {'enabled': enabled},
    );
    return MachineSchedule.fromJson(
        Map<String, dynamic>.from(response.data as Map));
  }

  Future<void> delete(String machineId) =>
      _dio.delete('/api/v1/machines/$machineId/schedule');
}

final scheduleActionsProvider =
    Provider<ScheduleActions>((ref) => ScheduleActions(ref.watch(apiClientProvider)));
