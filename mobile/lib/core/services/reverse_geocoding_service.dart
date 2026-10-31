import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';

/// Reverse geocoding service — resolves a machine's location to a
/// human-readable address.
///
/// SEC-15: geocoding is proxied through the YantraGO backend
/// (`GET /api/v1/geocoding/reverse`). The app never sends machine
/// coordinates to a third party directly — the backend applies machine
/// access validation, a bounded cache, and provider rate limiting, and
/// the external provider only ever sees the backend server. Results are
/// coalesced per machine+language while in flight. Address caching by
/// coordinates remains on the backend, so a refresh can follow a moved device.

/// A resolved address with structured fields for detailed display.
class ResolvedAddress {
  final String displayName; // Full one-line address
  final String? road; // Street / road name
  final String? neighbourhood;
  final String? suburb;
  final String? city; // City / town / village
  final String? county;
  final String? state; // State / region
  final String? postcode;
  final String? country;

  const ResolvedAddress({
    required this.displayName,
    this.road,
    this.neighbourhood,
    this.suburb,
    this.city,
    this.county,
    this.state,
    this.postcode,
    this.country,
  });

  /// Returns a multi-line detailed address for display.
  List<String> toDetailLines() {
    final lines = <String>[];
    if (road != null && road!.isNotEmpty) lines.add(road!);
    final area = [
      neighbourhood,
      suburb,
      city,
    ].where((s) => s != null && s.isNotEmpty).join(', ');
    if (area.isNotEmpty) lines.add(area);
    final region = [
      state,
      postcode,
    ].where((s) => s != null && s.isNotEmpty).join(' ');
    if (region.isNotEmpty) lines.add(region);
    if (country != null && country!.isNotEmpty) lines.add(country!);
    return lines.isEmpty ? [displayName] : lines;
  }

  /// Returns a compact 1-2 line address for tight UI layouts.
  ///
  /// Line 1: Road (or first available component)
  /// Line 2: Area (neighbourhood/suburb/city)
  ///
  /// Excludes state, postcode, and country to keep the display short.
  List<String> toShortLines() {
    final lines = <String>[];
    if (road != null && road!.isNotEmpty) {
      lines.add(road!);
    }
    final area = [
      neighbourhood,
      suburb,
      city,
    ].where((s) => s != null && s.isNotEmpty).join(', ');
    if (area.isNotEmpty) {
      lines.add(area);
    }
    if (lines.isEmpty) {
      // Fall back to a trimmed displayName (first comma segment)
      final parts = displayName.split(',').take(2).join(',').trim();
      if (parts.isNotEmpty) lines.add(parts);
    }
    return lines;
  }

  /// Builds from the backend's camelCase AddressDto JSON shape.
  factory ResolvedAddress.fromJson(Map<String, dynamic> json) {
    return ResolvedAddress(
      displayName: json['displayName'] as String? ?? '',
      road: json['road'] as String?,
      neighbourhood: json['neighbourhood'] as String?,
      suburb: json['suburb'] as String?,
      city: json['city'] as String?,
      county: json['county'] as String?,
      state: json['state'] as String?,
      postcode: json['postcode'] as String?,
      country: json['country'] as String?,
    );
  }
}

final reverseGeocodingProvider = Provider<ReverseGeocodingService>((ref) {
  // Recreate on account/tenant changes; old requests must not reach a new user.
  ref.watch(
    currentUserProvider.select((user) => (user?.id, user?.organizationId)),
  );
  final service = ReverseGeocodingService(ref.watch(apiClientProvider));
  ref.onDispose(service.dispose);
  return service;
});

class ReverseGeocodingService {
  ReverseGeocodingService(this._dio);

  /// Shared app Dio — carries the auth interceptor, so the backend can
  /// validate machine access before any provider call is made.
  final Dio _dio;

  final Map<String, Future<ResolvedAddress?>> _pending = {};
  final CancelToken _cancelToken = CancelToken();

  /// Resolves the current location of [machineId] to an address via the
  /// backend geocoding proxy. Returns null when the machine has no
  /// location (404), geocoding is unavailable, or the request fails —
  /// the UI falls back to raw coordinates.
  Future<ResolvedAddress?> reverseGeocodeMachine(
    String machineId, {
    String languageCode = 'en',
    String? locationKey,
  }) {
    if (_cancelToken.isCancelled) return Future.value(null);
    // Different coordinate snapshots must not share an old in-flight lookup.
    // This key is local only; the backend resolves authorized coordinates.
    final key = '$languageCode|$machineId|${locationKey ?? ''}';
    return _pending.putIfAbsent(
      key,
      () => _fetch(machineId, languageCode).whenComplete(() {
        _pending.remove(key);
      }),
    );
  }

  Future<ResolvedAddress?> _fetch(String machineId, String languageCode) async {
    try {
      final response = await _dio.get(
        '/api/v1/geocoding/reverse',
        queryParameters: {'machineId': machineId, 'lang': languageCode},
        cancelToken: _cancelToken,
      );

      if (response.statusCode == 200 && response.data != null) {
        return ResolvedAddress.fromJson(response.data as Map<String, dynamic>);
      }
    } on DioException {
      // 404 (no location), 502 (provider down), network error — all fall
      // back to coordinates-only display.
    }
    return null;
  }

  void dispose() {
    _cancelToken.cancel();
    _pending.clear();
  }
}
