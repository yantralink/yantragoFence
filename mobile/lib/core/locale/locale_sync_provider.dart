import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/auth/auth_service.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/locale/locale_pending_store.dart';
import 'package:yantrago/core/locale/supported_locales.dart';
import 'package:yantrago/core/network/network_error_messages.dart';
import 'package:yantrago/core/storage/secure_storage.dart';

/// Backoff delays between automatic sync retries. Overridable in tests.
final localeSyncBackoffProvider = Provider<List<Duration>>(
    (ref) => const [Duration(seconds: 2), Duration(seconds: 8)]);

/// Lifecycle of the account's notification-language preference sync.
enum LocaleSyncStatus {
  /// Server preference confirmed (or nothing to sync — guest device choice).
  synced,

  /// The latest selection is saved locally and on-device but NOT yet
  /// confirmed by the server. Retries are scheduled or awaiting reconnect.
  pending,

  /// The server permanently rejected the preference (validation/permission).
  /// No automatic retry; manual retry is available.
  failed,
}

class LocaleSyncState {
  final LocaleSyncStatus status;

  /// The locale awaiting server confirmation, when [status] is pending.
  final String? pendingLocale;

  const LocaleSyncState({this.status = LocaleSyncStatus.synced,
      this.pendingLocale});

  const LocaleSyncState.synced()
      : status = LocaleSyncStatus.synced,
        pendingLocale = null;

  @override
  bool operator ==(Object other) =>
      other is LocaleSyncState &&
      other.status == status &&
      other.pendingLocale == pendingLocale;

  @override
  int get hashCode => Object.hash(status, pendingLocale);
}

/// Account-scoped synchronization between the app's UI language and the
/// server's notification-language preference (Phase 5).
///
/// Guarantees:
/// - The UI locale switches immediately (locale controller); server sync is
///   tracked separately — a pending state is never reported as saved.
/// - The latest desired locale is durably marked per account BEFORE the
///   request, so process death or logout cannot silently lose intent.
/// - Writes are serialized; a stale response can never clear a newer
///   pending value (revision guard).
/// - Transient failures (network/5xx) retry with bounded backoff and on
///   app resume; validation/permission rejections stop retrying.
/// - Account switch clears all account-scoped state; a prior user's write
///   is never replayed with the next user's credentials.
final localeSyncProvider =
    NotifierProvider<LocaleSyncController, LocaleSyncState>(
        LocaleSyncController.new);

class LocaleSyncController extends Notifier<LocaleSyncState> {
  int _desiredRevision = 0;
  int _generation = 0; // account epoch — bumped on account switch/logout
  Future<void> _queue = Future.value();
  bool _sessionPrimed = false;

  @override
  LocaleSyncState build() => const LocaleSyncState.synced();

  SharedPreferences? get _prefs => ref.read(sharedPreferencesProvider);

  AuthService get _authService => ref.read(authServiceProvider);

  /// Called by the auth layer once a session is resolved (login or cold
  /// resume with a valid session).
  ///
  /// Precedence (per plan section 2):
  /// 1. A durable pending edit for THIS account wins — apply it to the UI
  ///    and re-attempt the sync.
  /// 2. Otherwise a valid server preference wins — apply it to the UI.
  /// 3. Otherwise (server pref unset) push the locally resolved language so
  ///    an account that never chose gets its first sync.
  Future<void> sessionResolved({
    required String? userId,
    required String? organizationId,
    required String? serverPreferredLocale,
  }) async {
    _sessionPrimed = true;
    final pending =
        _prefs == null ? null : LocalePendingStore.read(_prefs!, userId, organizationId);

    if (pending != null && kSupportedLanguageCodes.contains(pending)) {
      // 1. The user's latest local intent outranks the server snapshot.
      // Await the device persist so the UI locale and durable state are
      // consistent before the sync attempt starts.
      await ref.read(localeControllerProvider.notifier).setLocale(Locale(pending));
      state = LocaleSyncState(
          status: LocaleSyncStatus.pending, pendingLocale: pending);
      _enqueueSync(userId: userId, organizationId: organizationId);
      return;
    }

    if (serverPreferredLocale != null &&
        kSupportedLanguageCodes.contains(serverPreferredLocale)) {
      // 2. Server preference wins; make it the effective UI locale too.
      final current = ref.read(localeControllerProvider).locale.languageCode;
      if (current != serverPreferredLocale) {
        await ref
            .read(localeControllerProvider.notifier)
            .setLocale(Locale(serverPreferredLocale));
      }
      state = const LocaleSyncState.synced();
      return;
    }

    // 3. Server pref unset — first sync of the resolved device language.
    final resolved = ref.read(localeControllerProvider).locale.languageCode;
    if (kSupportedLanguageCodes.contains(resolved)) {
      state = LocaleSyncState(
          status: LocaleSyncStatus.pending, pendingLocale: resolved);
      await localeChanged(resolved,
          userId: userId, organizationId: organizationId);
    }
  }

  /// Records a language selection made by an authenticated user: the device
  /// preference is already persisted (locale controller); mark the durable
  /// account-scoped intent and start/queue the server sync.
  Future<void> localeChanged({
    required String locale,
    required String? userId,
    required String? organizationId,
  }) async {
    if (!kSupportedLanguageCodes.contains(locale)) return;
    final prefs = _prefs;
    _desiredRevision++;
    if (prefs != null) {
      await LocalePendingStore.write(prefs, userId, organizationId, locale);
    }
    state = LocaleSyncState(
        status: LocaleSyncStatus.pending, pendingLocale: locale);
    _enqueueSync(userId: userId, organizationId: organizationId);
  }

  /// Warm-resume convergence (plan item 7): applies the server's
  /// last-accepted preference when this device has no newer local intent.
  /// Two devices may diverge while one is offline; they converge here (and
  /// at login) without polling or extra channels.
  Future<void> serverLocaleRefreshed({
    required String? userId,
    required String? organizationId,
    required String? serverPreferredLocale,
  }) async {
    // A local un-synced edit outranks the server snapshot.
    if (state.status == LocaleSyncStatus.pending) return;
    if (serverPreferredLocale == null ||
        !kSupportedLanguageCodes.contains(serverPreferredLocale)) {
      return;
    }
    final current = ref.read(localeControllerProvider).locale.languageCode;
    if (current == serverPreferredLocale) return;
    await ref
        .read(localeControllerProvider.notifier)
        .setLocale(Locale(serverPreferredLocale));
    state = const LocaleSyncState.synced();
  }

  /// Manual retry (settings page) — resets the retry budget.
  ///
  /// Best-effort identity lookup: if SecureStorage is unavailable there is
  /// no authenticated account to sync for.
  Future<void> retry() async {
    String? userId;
    String? orgId;
    try {
      userId = await SecureStorage.getUserId();
      orgId = await SecureStorage.getOrgId();
    } catch (_) {
      return;
    }
    if (userId == null) return;
    _enqueueSync(userId: userId, organizationId: orgId);
  }

  /// App-resume hook: re-attempt a pending sync if one exists.
  Future<void> resumed() async {
    if (!_sessionPrimed) return;
    if (state.status != LocaleSyncStatus.pending) return;
    await retry();
  }

  /// Account switch / logout / forced-401 cleanup: cancels scheduled retries
  /// and clears in-memory state. The durable marker is removed for the
  /// given account (the auth layer passes the account being signed out).
  Future<void> accountCleared({
    required String? userId,
    required String? organizationId,
  }) async {
    _generation++; // invalidates any in-flight/scheduled attempts
    _sessionPrimed = false;
    final prefs = _prefs;
    if (prefs != null && userId != null) {
      await LocalePendingStore.clear(prefs, userId, organizationId);
    }
    state = const LocaleSyncState.synced();
  }

  /// Serializes sync attempts so overlapping selections run in order and
  /// the latest desired locale always wins.
  void _enqueueSync({required String? userId, required String? organizationId}) {
    final generation = _generation;
    _queue = _queue.then((_) async {
      if (generation != _generation) return; // account switched meanwhile
      await _runAttempts(
        userId: userId,
        organizationId: organizationId,
        generation: generation,
      );
    });
  }

  Future<void> _runAttempts({
    required String? userId,
    required String? organizationId,
    required int generation,
  }) async {
    final backoff = ref.read(localeSyncBackoffProvider);
    for (var attempt = 0;; attempt++) {
      final revisionAtStart = _desiredRevision;
      final desired = state.pendingLocale;
      if (desired == null) return; // nothing pending

      final outcome = await _tryPut(desired);

      if (generation != _generation) return; // superseded by account switch
      switch (outcome) {
        case _SyncOutcome.confirmed:
          // Only clear pending if no newer selection arrived meanwhile.
          if (revisionAtStart == _desiredRevision) {
            final prefs = _prefs;
            if (prefs != null) {
              await LocalePendingStore.clear(prefs, userId, organizationId);
            }
            state = const LocaleSyncState.synced();
          }
          return;
        case _SyncOutcome.rejected:
          state = LocaleSyncState(
              status: LocaleSyncStatus.failed, pendingLocale: desired);
          return;
        case _SyncOutcome.transient:
          if (attempt >= backoff.length) {
            // Budget exhausted — stay pending; resume/manual retry later.
            return;
          }
          await Future<void>.delayed(backoff[attempt]);
          if (generation != _generation) return;
      }
    }
  }

  Future<_SyncOutcome> _tryPut(String locale) async {
    try {
      await _authService.updatePreferredLocale(locale);
      return _SyncOutcome.confirmed;
    } on DioException catch (e) {
      final code = networkErrorCodeFrom(e) ?? NetworkErrorCode.unexpected;
      switch (code) {
        case NetworkErrorCode.unauthorized:
        case NetworkErrorCode.forbidden:
        case NetworkErrorCode.notFound:
        case NetworkErrorCode.badCertificate:
          // Auth/permission problems are handled by the auth layer; do not
          // spin retries against a rejecting server.
          return _SyncOutcome.rejected;
        case NetworkErrorCode.connectionTimeout:
        case NetworkErrorCode.connectionError:
        case NetworkErrorCode.serverError:
        case NetworkErrorCode.cancelled:
        case NetworkErrorCode.requestFailed:
        case NetworkErrorCode.unexpected:
          return _SyncOutcome.transient;
      }
    } catch (_) {
      return _SyncOutcome.transient;
    }
  }
}

enum _SyncOutcome { confirmed, rejected, transient }
