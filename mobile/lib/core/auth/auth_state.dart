import 'package:yantrago/core/network/network_error_messages.dart';
import 'package:yantrago/models/user.dart';

/// Auth state — represents the current authentication status.
sealed class AuthState {
  const AuthState();
}

class AuthInitial extends AuthState {
  const AuthInitial();
}

class AuthLoading extends AuthState {
  const AuthLoading();
}

class Authenticated extends AuthState {
  final User user;
  const Authenticated(this.user);
}

class Unauthenticated extends AuthState {
  const Unauthenticated();
}

class AuthError extends AuthState {
  /// Typed failure category — the widget layer maps it to a localized
  /// message. Never display [detail] to users.
  final NetworkErrorCode? code;

  /// Developer-facing context for logs only (e.g. exception toString).
  final String detail;
  const AuthError(this.code, {this.detail = ''});
}
