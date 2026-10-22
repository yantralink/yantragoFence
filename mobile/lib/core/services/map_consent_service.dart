import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/storage/secure_storage.dart';

/// SEC-15: user's affirmative consent for third-party map/geocoding
/// transfers.
///
/// Showing the machine on the map loads tiles from OpenStreetMap and the
/// backend may forward the machine's coordinates to a geocoding provider.
/// Because machine coordinates are farmer-linked precise location data,
/// this transfer requires prior affirmative consent (Google Play
/// user-data policy) — stored per install so it is asked once, and
/// cleared on logout so a new user on a shared device is asked again.
enum MapConsent {
  /// User has not been asked yet (or storage is unreadable).
  undecided,

  /// User explicitly allowed third-party map/geocoding transfers.
  granted,

  /// User declined. Map tiles and geocoding must not be contacted —
  /// the UI falls back to raw coordinates only.
  declined,
}

final mapConsentProvider =
    AsyncNotifierProvider<MapConsentController, MapConsent>(
        MapConsentController.new);

class MapConsentController extends AsyncNotifier<MapConsent> {
  @override
  Future<MapConsent> build() async {
    // Fail closed: an unreadable store means "not asked yet", never
    // an implicit grant.
    String? raw;
    try {
      raw = await SecureStorage.getMapConsent();
    } catch (_) {
      raw = null;
    }
    return switch (raw) {
      'granted' => MapConsent.granted,
      'declined' => MapConsent.declined,
      _ => MapConsent.undecided,
    };
  }

  /// Records the user's explicit opt-in. Called only from an affirmative
  /// UI action (button tap), never implicitly. If persistence fails the
  /// in-session choice still applies — the affirmative act itself is what
  /// matters for consent.
  Future<void> grant() async {
    try {
      await SecureStorage.setMapConsent('granted');
    } catch (_) {
      // Session-only consent — re-asked on next launch.
    }
    state = const AsyncData(MapConsent.granted);
  }

  /// Records an explicit decline — the map feature stays off until the
  /// user later grants consent.
  Future<void> decline() async {
    try {
      await SecureStorage.setMapConsent('declined');
    } catch (_) {
      // Session-only decline — fail closed on next launch anyway.
    }
    state = const AsyncData(MapConsent.declined);
  }
}
