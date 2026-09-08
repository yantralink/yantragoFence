import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/auth/auth_service.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/auth/token_manager.dart';
import 'package:yantrago/core/storage/secure_storage.dart';
import 'package:yantrago/models/user.dart';

/// Auth state notifier — manages authentication lifecycle.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
/// Per AGENTS.md rule 9: sensitive operations require authorization.
class AuthStateNotifier extends StateNotifier<AuthState> {
  final AuthService _authService;

  AuthStateNotifier(this._authService) : super(const AuthInitial()) {
    _checkExistingSession();
  }

  /// Checks for existing tokens in secure storage on app start.
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
    } catch (_) {
      // Token is invalid or expired
      await SecureStorage.clearAll();
      state = const Unauthenticated();
    }
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
    } catch (e) {
      state = AuthError(e.toString());
      // Reset to unauthenticated after error is consumed
      Future.delayed(const Duration(milliseconds: 100), () {
        if (state is AuthError) state = const Unauthenticated();
      });
    }
  }

  /// Logs out the current user.
  Future<void> logout() async {
    state = const AuthLoading();
    try {
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
  (ref) => AuthStateNotifier(ref.watch(authServiceProvider)),
);

/// Convenience provider to get the current user.
final currentUserProvider = Provider<User?>((ref) {
  final state = ref.watch(authStateProvider);
  return state is Authenticated ? state.user : null;
});
