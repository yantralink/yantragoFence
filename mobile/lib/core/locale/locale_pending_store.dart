import 'package:shared_preferences/shared_preferences.dart';

/// Durable, account-scoped pending-locale marker (Phase 5).
///
/// Records the latest desired language that the user selected while the
/// server sync had not yet been confirmed. Keyed by organization + user so
/// two accounts on one shared phone can never read each other's pending
/// write (backend environment is a single install for the app; including it
/// would add nothing — the key namespace is already per-device store).
///
/// Lives in SharedPreferences (NOT secure storage): a language preference is
/// not a secret, and the marker must survive process death while tokens live
/// in the separate SecureStorage store.
class LocalePendingStore {
  LocalePendingStore._();

  /// Stored under the given [prefs] backend; injectable for tests.
  static String keyFor(String? userId, String? organizationId) =>
      'locale_pending_${organizationId ?? 'none'}_${userId ?? 'none'}';

  /// Returns the pending locale for this account, or null when no un-synced
  /// edit exists.
  static String? read(
    SharedPreferences prefs,
    String? userId,
    String? organizationId,
  ) {
    return prefs.getString(keyFor(userId, organizationId));
  }

  /// Records the latest desired locale for this account (overwrites any
  /// earlier pending value — latest selection wins).
  static Future<void> write(
    SharedPreferences prefs,
    String? userId,
    String? organizationId,
    String locale,
  ) {
    return prefs.setString(keyFor(userId, organizationId), locale);
  }

  /// Clears the marker once the server has confirmed exactly this locale.
  static Future<void> clear(
    SharedPreferences prefs,
    String? userId,
    String? organizationId,
  ) {
    return prefs.remove(keyFor(userId, organizationId));
  }
}
