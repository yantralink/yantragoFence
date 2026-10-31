import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/services/reverse_geocoding_service.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';

/// Machine address provider — resolves a machine's current location to a
/// human-readable address via the backend geocoding proxy.
///
/// SEC-15: returns null until the user has granted map/geocoding consent —
/// resolving an address forwards machine coordinates (farmer-linked
/// precise location) to a third-party provider via the backend, which is
/// only allowed after explicit opt-in. Watches the locale controller so a
/// language switch re-resolves addresses (the language is part of the
/// geocoding cache identity).
final machineAddressProvider = FutureProvider.autoDispose
    .family<ResolvedAddress?, String>((ref, machineId) async {
      if (ref.watch(currentUserProvider) == null) return null;
      final consent = ref.watch(mapConsentProvider);
      // Loading can retain the previous account's value in Riverpod.
      if (consent.isLoading ||
          consent.hasError ||
          consent.valueOrNull != MapConsent.granted) {
        return null;
      }

      final languageCode = ref
          .watch(localeControllerProvider)
          .locale
          .languageCode;
      final geocodingService = ref.watch(reverseGeocodingProvider);
      // Share the existing location lookup and resolve again after its refresh.
      // No geocoding is needed for machines that have never reported GPS.
      var disposed = false;
      ref.onDispose(() => disposed = true);
      final location = await ref.watch(
        machineLocationProvider(machineId).future,
      );
      if (disposed || location == null) return null;
      return geocodingService.reverseGeocodeMachine(
        machineId,
        languageCode: languageCode,
        locationKey: '${location.latitude},${location.longitude}',
      );
    });
