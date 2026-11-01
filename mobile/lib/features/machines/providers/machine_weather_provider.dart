import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/services/weather_service.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/models/weather.dart';

/// Machine weather provider — resolves current weather for a machine's
/// location via the backend weather proxy.
///
/// SEC-15: returns null until the user has granted map consent —
/// resolving weather forwards (rounded) machine coordinates
/// (farmer-linked precise location) to a third-party provider via the
/// backend, which is only allowed after explicit opt-in. Re-fetches
/// when the machine's location changes (the locationKey is part of the
/// request coalescing identity).
final machineWeatherProvider = FutureProvider.autoDispose
    .family<Weather?, String>((ref, machineId) async {
      if (ref.watch(currentUserProvider) == null) return null;
      final consent = ref.watch(mapConsentProvider);
      // Loading can retain the previous account's value in Riverpod.
      if (consent.isLoading ||
          consent.hasError ||
          consent.valueOrNull != MapConsent.granted) {
        return null;
      }

      final weatherService = ref.watch(weatherServiceProvider);
      // Share the existing location lookup and resolve again after its
      // refresh. No weather lookup for machines that never reported GPS.
      var disposed = false;
      ref.onDispose(() => disposed = true);
      final location = await ref.watch(
        machineLocationProvider(machineId).future,
      );
      if (disposed || location == null) return null;
      return weatherService.getMachineWeather(
        machineId,
        locationKey: '${location.latitude},${location.longitude}',
      );
    });
