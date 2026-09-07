import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/services/reverse_geocoding_service.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';

/// Machine address provider — combines the current GPS location for a machine
/// with reverse geocoding to produce a human-readable address.
///
/// Watches machineLocationProvider for the coordinates, then calls
/// ReverseGeocodingService to resolve them to an address.
final machineAddressProvider = FutureProvider.family<ResolvedAddress?, String>(
    (ref, machineId) async {
  final location = await ref.watch(machineLocationProvider(machineId).future);
  if (location == null) return null;

  final geocodingService = ref.watch(reverseGeocodingProvider);
  return geocodingService.reverseGeocode(location.latitude, location.longitude);
});
