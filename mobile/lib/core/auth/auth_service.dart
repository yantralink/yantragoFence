import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/core/storage/secure_storage.dart';
import 'package:yantrago/models/user.dart';
import 'package:dio/dio.dart';

/// Auth service — handles login, logout, token refresh, and current user.
///
/// Per AGENTS.md rule 9: all sensitive operations require authorization.
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
class AuthService {
  final Dio _dio;

  AuthService(this._dio);

  /// Logs in with email (or phone number for customers) and password.
  /// Returns the user and tokens on success.
  Future<AuthResult> login({
    required String email,
    required String password,
  }) async {
    final response = await _dio.post('/api/v1/auth/login', data: {
      'email': email,
      'password': password,
    });

    final result = AuthResult.fromJson(response.data as Map<String, dynamic>);

    // Persist tokens
    await SecureStorage.setAccessToken(result.accessToken);
    await SecureStorage.setRefreshToken(result.refreshToken);
    await SecureStorage.setUserId(result.user.id);
    await SecureStorage.setOrgId(result.user.organizationId);

    return result;
  }

  /// Logs out the current user.
  Future<void> logout() async {
    try {
      await _dio.post('/api/v1/auth/logout');
    } finally {
      await SecureStorage.clearAll();
    }
  }

  /// Gets the current user profile.
  Future<User> getCurrentUser() async {
    final response = await _dio.get('/api/v1/auth/me');
    return User.fromJson(response.data as Map<String, dynamic>);
  }

  /// Checks if a token exists in secure storage.
  Future<bool> isAuthenticated() async {
    final token = await SecureStorage.getAccessToken();
    return token != null && token.isNotEmpty;
  }
}

/// Auth result returned on successful login.
/// Backend response format:
/// {
///   "accessToken": "...",
///   "refreshToken": "...",
///   "tokenType": "Bearer",
///   "expiresIn": 900,
///   "user": { "id": "...", "email": "...", "fullName": "...", "organizationId": "..." }
/// }
class AuthResult {
  final User user;
  final String accessToken;
  final String refreshToken;

  AuthResult({
    required this.user,
    required this.accessToken,
    required this.refreshToken,
  });

  factory AuthResult.fromJson(Map<String, dynamic> json) {
    return AuthResult(
      user: User.fromJson(json['user'] as Map<String, dynamic>),
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
    );
  }
}

/// AuthService provider.
final authServiceProvider = Provider<AuthService>((ref) {
  return AuthService(ref.watch(apiClientProvider));
});
