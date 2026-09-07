import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Reverse geocoding service — converts lat/lon to a human-readable address.
///
/// Uses the Nominatim (OpenStreetMap) reverse geocoding API which is free
/// and requires no API key. Results are cached to avoid repeated lookups
/// for the same coordinates.
///
/// Usage policy: max 1 request per second (Nominatim fair use).
/// We cache results so repeated UI rebuilds don't trigger new requests.

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
}

final reverseGeocodingProvider =
    Provider<ReverseGeocodingService>((ref) => ReverseGeocodingService());

class ReverseGeocodingService {
  final Dio _dio = Dio(BaseOptions(
    baseUrl: 'https://nominatim.openstreetmap.org',
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
    headers: {
      'Accept-Language': 'en',
      'User-Agent': 'YantraGO-App/1.0',
    },
  ));

  /// Cache: "lat,lon" (rounded to 4 decimal places) → ResolvedAddress
  final Map<String, ResolvedAddress> _cache = {};

  /// Reverse geocodes lat/lon to a human-readable address.
  /// Returns null if geocoding fails.
  Future<ResolvedAddress?> reverseGeocode(double lat, double lon) async {
    // Round to ~11m precision for cache key
    final cacheKey = '${lat.toStringAsFixed(4)},${lon.toStringAsFixed(4)}';
    if (_cache.containsKey(cacheKey)) {
      return _cache[cacheKey];
    }

    try {
      final response = await _dio.get('/reverse', queryParameters: {
        'format': 'jsonv2',
        'lat': lat,
        'lon': lon,
        'addressdetails': 1,
      });

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data as Map<String, dynamic>;
        final address = data['address'] as Map<String, dynamic>?;

        final resolved = ResolvedAddress(
          displayName: data['display_name'] as String? ?? '',
          road: address?['road'] as String?,
          neighbourhood: address?['neighbourhood'] as String?,
          suburb: address?['suburb'] as String?,
          city: (address?['city'] ?? address?['town'] ?? address?['village'])
              as String?,
          county: address?['county'] as String?,
          state: address?['state'] as String?,
          postcode: address?['postcode'] as String?,
          country: address?['country'] as String?,
        );

        _cache[cacheKey] = resolved;
        return resolved;
      }
    } catch (e) {
      // Network error or parsing error — return null, UI will show coordinates
    }
    return null;
  }
}
