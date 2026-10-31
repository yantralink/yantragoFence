import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/storage/secure_storage.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';

/// SEC-15: user's affirmative consent for third-party map/geocoding
/// transfers.
///
/// Showing the machine on the map loads tiles from OpenStreetMap and the
/// backend may forward the machine's coordinates to a geocoding provider.
/// Because machine coordinates are farmer-linked precise location data,
/// this transfer requires prior affirmative consent (Google Play
/// user-data policy). Persisted consent includes the account identity and is
/// cleared on logout; a shared phone must never reuse another user's consent.
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
      MapConsentController.new,
    );

class MapConsentController extends AsyncNotifier<MapConsent> {
  @override
  Future<MapConsent> build() async {
    final account = ref.watch(
      currentUserProvider.select(
        (user) => user == null ? null : (user.id, user.organizationId),
      ),
    );
    if (account == null) return MapConsent.undecided;
    // Fail closed: an unreadable store means "not asked yet", never
    // an implicit grant.
    String? raw;
    try {
      raw = await SecureStorage.getMapConsent();
    } catch (_) {
      raw = null;
    }
    if (raw == _storedValue(account, MapConsent.granted)) {
      return MapConsent.granted;
    }
    if (raw == _storedValue(account, MapConsent.declined)) {
      return MapConsent.declined;
    }
    // Legacy unscoped values require a fresh opt-in.
    return MapConsent.undecided;
  }

  /// Records the user's explicit opt-in. Called only from an affirmative
  /// UI action (button tap), never implicitly. If persistence fails the
  /// in-session choice still applies — the affirmative act itself is what
  /// matters for consent.
  Future<void> grant() => _record(MapConsent.granted);

  /// Records an explicit decline — the map feature stays off until the
  /// user later grants consent.
  Future<void> decline() => _record(MapConsent.declined);

  Future<void> _record(MapConsent value) async {
    final user = ref.read(currentUserProvider);
    if (user == null) return;
    final account = (user.id, user.organizationId);
    var active = true;
    ref.onDispose(() => active = false);
    try {
      await SecureStorage.setMapConsent(_storedValue(account, value));
    } catch (_) {
      // Session-only choice — fail closed on next launch.
    }
    if (!active) return;
    final current = ref.read(currentUserProvider);
    if (current != null && (current.id, current.organizationId) == account) {
      state = AsyncData(value);
    }
  }

  String _storedValue((String, String?) account, MapConsent value) =>
      jsonEncode([account.$1, account.$2, value.name]);
}
