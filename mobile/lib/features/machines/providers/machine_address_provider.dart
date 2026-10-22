import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/services/reverse_geocoding_service.dart';

/// Machine address provider — resolves a machine's current location to a
/// human-readable address via the backend geocoding proxy.
///
/// SEC-15: returns null until the user has granted map/geocoding consent —
/// resolving an address forwards machine coordinates (farmer-linked
/// precise location) to a third-party provider via the backend, which is
/// only allowed after explicit opt-in. Watches the locale controller so a
/// language switch re-resolves addresses (the language is part of the
/// geocoding cache identity).
final machineAddressProvider = FutureProvider.autoDispose.family<ResolvedAddress?, String>(
    (ref, machineId) async {
  final consent = ref.watch(mapConsentProvider).valueOrNull;
  if (consent != MapConsent.granted) return null;

  final languageCode =
      ref.watch(localeControllerProvider).locale.languageCode;
  final geocodingService = ref.watch(reverseGeocodingProvider);
  return geocodingService.reverseGeocodeMachine(
    machineId,
    languageCode: languageCode,
  );
});
