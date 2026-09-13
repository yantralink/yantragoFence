import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';

/// Theft protection status for a machine (Phase 11).
///
/// Returned by GET /api/v1/machines/{id}/theft-protection.
class TheftProtectionStatus {
  final String machineId;
  final bool protectionEnabled;
  final bool geofenceActive;
  final bool movementRuleActive;
  final String? geofenceId;
  final double? geofenceLatitude;
  final double? geofenceLongitude;
  final int? geofenceRadiusMeters;
  final double? machineLatitude;
  final double? machineLongitude;

  const TheftProtectionStatus({
    required this.machineId,
    required this.protectionEnabled,
    required this.geofenceActive,
    required this.movementRuleActive,
    this.geofenceId,
    this.geofenceLatitude,
    this.geofenceLongitude,
    this.geofenceRadiusMeters,
    this.machineLatitude,
    this.machineLongitude,
  });

  factory TheftProtectionStatus.fromJson(Map<String, dynamic> json) {
    return TheftProtectionStatus(
      machineId: json['machineId'] as String? ?? '',
      protectionEnabled: json['protectionEnabled'] as bool? ?? false,
      geofenceActive: json['geofenceActive'] as bool? ?? false,
      movementRuleActive: json['movementRuleActive'] as bool? ?? false,
      geofenceId: json['geofenceId'] as String?,
      geofenceLatitude: (json['geofenceLatitude'] as num?)?.toDouble(),
      geofenceLongitude: (json['geofenceLongitude'] as num?)?.toDouble(),
      geofenceRadiusMeters: json['geofenceRadiusMeters'] as int?,
      machineLatitude: (json['machineLatitude'] as num?)?.toDouble(),
      machineLongitude: (json['machineLongitude'] as num?)?.toDouble(),
    );
  }
}

/// Notifier for enabling/disabling theft protection.
class TheftProtectionNotifier
    extends StateNotifier<AsyncValue<TheftProtectionStatus>> {
  TheftProtectionNotifier(this._dio, this._machineId)
      : super(const AsyncValue.loading());

  final Dio _dio;
  final String _machineId;

  Future<void> load() async {
    state = const AsyncValue.loading();
    try {
      final response = await _dio.get(
        '/api/v1/machines/$_machineId/theft-protection',
      );
      state = AsyncValue.data(TheftProtectionStatus.fromJson(
          response.data as Map<String, dynamic>));
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> enable() async {
    try {
      final response = await _dio.post(
        '/api/v1/machines/$_machineId/theft-protection/enable',
      );
      state = AsyncValue.data(TheftProtectionStatus.fromJson(
          response.data as Map<String, dynamic>));
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> disable() async {
    try {
      final response = await _dio.post(
        '/api/v1/machines/$_machineId/theft-protection/disable',
      );
      state = AsyncValue.data(TheftProtectionStatus.fromJson(
          response.data as Map<String, dynamic>));
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}

final theftProtectionNotifierProvider = StateNotifierProvider.family<
    TheftProtectionNotifier,
    AsyncValue<TheftProtectionStatus>,
    String>((ref, machineId) {
  final notifier =
      TheftProtectionNotifier(ref.watch(apiClientProvider), machineId);
  final user = ref.watch(currentUserProvider);
  if (user != null) {
    notifier.load();
  }
  return notifier;
});
