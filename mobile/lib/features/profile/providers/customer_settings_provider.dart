import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';

/// Customer theft protection defaults (Phase 11).
///
/// Per-customer settings for geofence radius and speed threshold.
/// Used when the customer enables theft protection for a new machine.
class CustomerSettings {
  final int defaultGeofenceRadiusMeters;
  final int defaultSpeedThresholdKmh;

  const CustomerSettings({
    this.defaultGeofenceRadiusMeters = 200,
    this.defaultSpeedThresholdKmh = 10,
  });

  factory CustomerSettings.fromJson(Map<String, dynamic> json) {
    return CustomerSettings(
      defaultGeofenceRadiusMeters:
          json['defaultGeofenceRadiusMeters'] as int? ?? 200,
      defaultSpeedThresholdKmh:
          json['defaultSpeedThresholdKmh'] as int? ?? 10,
    );
  }

  Map<String, dynamic> toJson() => {
        'defaultGeofenceRadiusMeters': defaultGeofenceRadiusMeters,
        'defaultSpeedThresholdKmh': defaultSpeedThresholdKmh,
      };
}

/// Notifier for updating customer settings.
class CustomerSettingsNotifier extends StateNotifier<AsyncValue<CustomerSettings>> {
  CustomerSettingsNotifier(this._dio) : super(const AsyncValue.loading());

  final Dio _dio;

  Future<void> load() async {
    state = const AsyncValue.loading();
    try {
      final response =
          await _dio.get(AppConfig.customerSettingsEndpoint);
      state = AsyncValue.data(
          CustomerSettings.fromJson(response.data as Map<String, dynamic>),
      );
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> update(int radiusMeters, int speedThreshold) async {
    try {
      final response = await _dio.put(
        AppConfig.customerSettingsEndpoint,
        data: {
          'defaultGeofenceRadiusMeters': radiusMeters,
          'defaultSpeedThresholdKmh': speedThreshold,
        },
      );
      state = AsyncValue.data(
          CustomerSettings.fromJson(response.data as Map<String, dynamic>),
      );
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
}

final customerSettingsNotifierProvider =
    StateNotifierProvider<CustomerSettingsNotifier, AsyncValue<CustomerSettings>>(
  (ref) {
    final notifier = CustomerSettingsNotifier(ref.watch(apiClientProvider));
    // Auto-load when user is authenticated
    final user = ref.watch(currentUserProvider);
    if (user != null) {
      notifier.load();
    }
    return notifier;
  },
);
