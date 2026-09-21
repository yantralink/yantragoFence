import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/services/reverse_geocoding_service.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';

/// Machine address provider — combines the current GPS location for a machine
/// with reverse geocoding to produce a human-readable address.
///
/// Watches machineLocationProvider for the coordinates and the locale
/// controller for the UI language (Phase 3: localized addresses; the language
/// is part of the geocoding cache identity). A language switch re-resolves
/// addresses; Nominatim falls back to English for unsupported languages.
final machineAddressProvider = FutureProvider.autoDispose.family<ResolvedAddress?, String>(
    (ref, machineId) async {
  final location = await ref.watch(machineLocationProvider(machineId).future);
  if (location == null) return null;

  final languageCode =
      ref.watch(localeControllerProvider).locale.languageCode;
  final geocodingService = ref.watch(reverseGeocodingProvider);
  return geocodingService.reverseGeocode(
    location.latitude,
    location.longitude,
    languageCode: languageCode,
  );
});
