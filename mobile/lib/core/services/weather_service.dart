import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/weather.dart';
import 'package:yantrago/models/weather_forecast.dart';

/// Weather service — resolves a machine's location weather via the
/// backend proxy, mirroring ReverseGeocodingService.
///
/// SEC-15: weather is proxied through the YantraGO backend
/// (`GET /api/v1/machines/{id}/weather`). The app never sends machine
/// coordinates to a third party directly — the backend applies machine
/// access validation, a bounded cache, and provider rate limiting, and
/// the external provider only ever sees the backend server. Results
/// are coalesced per machine+locationKey while in flight.
final weatherServiceProvider = Provider<WeatherService>((ref) {
  // Recreate on account/tenant changes; old requests must not reach a new user.
  ref.watch(
    currentUserProvider.select((user) => (user?.id, user?.organizationId)),
  );
  final service = WeatherService(ref.watch(apiClientProvider));
  ref.onDispose(service.dispose);
  return service;
});

class WeatherService {
  WeatherService(this._dio);

  /// Shared app Dio — carries the auth interceptor, so the backend can
  /// validate machine access before any provider call is made.
  final Dio _dio;

  final Map<String, Future<Weather?>> _pending = {};
  final Map<String, Future<WeatherForecast?>> _forecastPending = {};
  final CancelToken _cancelToken = CancelToken();

  /// Fetches current weather for [machineId] via the backend weather
  /// proxy. Returns null on 404 (no location), 502 (provider down or
  /// unconfigured), or network errors — the UI shows its fallback.
  Future<Weather?> getMachineWeather(
    String machineId, {
    String? locationKey,
  }) {
    if (_cancelToken.isCancelled) return Future.value(null);
    // Different coordinate snapshots must not share an old in-flight
    // lookup. This key is local only; the backend resolves coordinates.
    final key = '$machineId|${locationKey ?? ''}';
    return _pending.putIfAbsent(
      key,
      () => _fetch(machineId).whenComplete(() {
        _pending.remove(key);
      }),
    );
  }

  Future<Weather?> _fetch(String machineId) async {
    try {
      final response = await _dio.get(
        '/api/v1/machines/$machineId/weather',
        cancelToken: _cancelToken,
      );

      if (response.statusCode == 200 && response.data != null) {
        return Weather.fromJson(response.data as Map<String, dynamic>);
      }
    } on DioException {
      // 404 (no location), 502 (provider down), network error — all
      // fall back to the unavailable state.
    }
    return null;
  }

  /// Fetches the normalized forecast slots for [machineId] via the
  /// backend forecast proxy. Returns null on 404/502/network errors —
  /// the UI shows its unavailable state. Coalesced per
  /// machine+locationKey while in flight.
  Future<WeatherForecast?> getMachineForecast(
    String machineId, {
    String? locationKey,
  }) {
    if (_cancelToken.isCancelled) return Future.value(null);
    final key = 'forecast|$machineId|${locationKey ?? ''}';
    return _forecastPending.putIfAbsent(
      key,
      () => _fetchForecast(machineId).whenComplete(() {
        _forecastPending.remove(key);
      }),
    );
  }

  Future<WeatherForecast?> _fetchForecast(String machineId) async {
    try {
      final response = await _dio.get(
        '/api/v1/machines/$machineId/weather/forecast',
        cancelToken: _cancelToken,
      );
      if (response.statusCode == 200 && response.data != null) {
        return WeatherForecast.fromJson(
            response.data as Map<String, dynamic>);
      }
    } on DioException {
      // 404 (no location), 502 (provider down), network error — all
      // fall back to the unavailable state.
    }
    return null;
  }

  void dispose() {
    _cancelToken.cancel();
    _pending.clear();
    _forecastPending.clear();
  }
}
