import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/auth/auth_service.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/auth/token_manager.dart';
import 'package:yantrago/core/locale/locale_sync_provider.dart';
import 'package:yantrago/core/network/network_error_messages.dart';
import 'package:yantrago/core/storage/secure_storage.dart';
import 'package:yantrago/features/push/providers/push_notification_provider.dart';
import 'package:yantrago/models/user.dart';

/// Auth state notifier — manages authentication lifecycle.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
/// Per AGENTS.md rule 9: sensitive operations require authorization.
///
/// Phase 5: integrates push token registration on login and
/// token deactivation on logout (shared-phone account switching), plus
/// locale preference synchronization on session resolve/teardown.
class AuthStateNotifier extends StateNotifier<AuthState> {
  final AuthService _authService;
  final PushNotificationService _pushService;
  final Ref _ref;

  AuthStateNotifier(this._authService, this._pushService, this._ref)
      : super(const AuthInitial()) {
    _checkExistingSession();
  }

  /// Checks for existing tokens in secure storage on app start.
  ///
  /// Phase 5: only CONFIRMED invalid authentication (server rejected the
  /// token with 401/403) clears storage. Transient failures (offline
  /// startup, timeouts, 5xx) keep tokens and account-scoped pending state —
  /// going offline must not erase a not-yet-synced language choice.
  Future<void> _checkExistingSession() async {
    state = const AuthLoading();
    try {
      final hasTokens = await TokenManager.hasTokens();
      if (!hasTokens) {
        state = const Unauthenticated();
        return;
      }
      // Validate token by fetching current user
      final user = await _authService.getCurrentUser();
      await SecureStorage.setUserId(user.id);
      await SecureStorage.setOrgId(user.organizationId);
      await SecureStorage.setOrgName(user.organizationName);
      state = Authenticated(user);
      // Re-register FCM token for this account (token rotation)
      await _pushService.registerTokenAfterLogin();
      // Session is valid — resolve locale precedence (pending edit >
      // server preference > first sync of the device locale).
      await _ref.read(localeSyncProvider.notifier).sessionResolved(
            userId: user.id,
            organizationId: user.organizationId,
            serverPreferredLocale: user.preferredLocale,
          );
    } on DioException catch (e) {
      final code = networkErrorCodeFrom(e) ?? NetworkErrorCode.unexpected;
      final confirmedInvalid = e.response?.statusCode == 401 ||
          e.response?.statusCode == 403 ||
          code == NetworkErrorCode.unauthorized ||
          code == NetworkErrorCode.forbidden;
      if (confirmedInvalid) {
        // Server explicitly rejected the stored token.
        await _clearAccountLocaleState();
        await SecureStorage.clearAll();
      }
      // Transient: keep tokens + pending locale intent; fall back to login.
      state = const Unauthenticated();
      return;
    } catch (_) {
      // Non-HTTP failure during validation — treat as transient.
      state = const Unauthenticated();
      return;
    }
  }

  Future<void> _clearAccountLocaleState() async {
    final userId = await SecureStorage.getUserId();
    final orgId = await SecureStorage.getOrgId();
    await _ref
        .read(localeSyncProvider.notifier)
        .accountCleared(userId: userId, organizationId: orgId);
  }

  /// Logs in with email and password.
  Future<void> login({
    required String email,
    required String password,
  }) async {
    state = const AuthLoading();
    try {
      final result = await _authService.login(
        email: email,
        password: password,
      );
      state = Authenticated(result.user);
      // Register FCM token for the newly logged-in account
      await _pushService.registerTokenAfterLogin();
    } catch (e) {
      // Map the failure to a typed code; the widget layer localizes it.
      // Never surface e.toString() to users — keep it as log-only detail.
      final code = e is DioException
          ? networkErrorCodeFrom(e) ?? NetworkErrorCode.unexpected
          : NetworkErrorCode.unexpected;
      state = AuthError(code, detail: e.toString());
      // Reset to unauthenticated after error is consumed
      Future.delayed(const Duration(milliseconds: 100), () {
        if (state is AuthError) state = const Unauthenticated();
      });
      return;
    }
    // Session resolved — apply locale precedence for the fresh session.
    final user = state is Authenticated ? (state as Authenticated).user : null;
    if (user != null) {
      await _ref.read(localeSyncProvider.notifier).sessionResolved(
            userId: user.id,
            organizationId: user.organizationId,
            serverPreferredLocale: user.preferredLocale,
          );
    }
  }

  /// Logs out the current user.
  /// Phase 5: deactivates all device tokens to prevent push to a shared
  /// device, and clears account-scoped locale sync state so this account's
  /// pending write can never replay with the next user's credentials.
  Future<void> logout() async {
    state = const AuthLoading();
    try {
      final user =
          state is Authenticated ? (state as Authenticated).user : null;
      // Clear locale sync intent BEFORE storage teardown.
      await _ref.read(localeSyncProvider.notifier).accountCleared(
            userId: user?.id,
            organizationId: user?.organizationId,
          );
      // Deactivate push tokens before clearing auth state
      await _pushService.deactivateAllTokensOnLogout();
      await _authService.logout();
    } finally {
      state = const Unauthenticated();
    }
  }

  /// Refreshes the current user data.
  Future<void> refreshUser() async {
    if (state is! Authenticated) return;
    try {
      final user = await _authService.getCurrentUser();
      state = Authenticated(user);
    } catch (_) {
      // Ignore refresh errors
    }
  }
}

/// Auth state provider.
final authStateProvider = StateNotifierProvider<AuthStateNotifier, AuthState>(
  (ref) => AuthStateNotifier(
    ref.watch(authServiceProvider),
    ref.watch(pushNotificationServiceProvider),
    ref,
  ),
);

/// Convenience provider to get the current user.
final currentUserProvider = Provider<User?>((ref) {
  final state = ref.watch(authStateProvider);
  return state is Authenticated ? state.user : null;
});
