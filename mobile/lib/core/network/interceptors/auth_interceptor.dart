import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/locale/locale_sync_provider.dart';
import 'package:yantrago/core/storage/secure_storage.dart';

/// Auth interceptor — injects JWT Bearer token into every request.
///
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
/// The backend extracts it from the token.
class AuthInterceptor extends Interceptor {
  final Ref _ref;

  /// Serializes token refreshes: when several requests 401 at once (app
  /// resume, screen mount), exactly one /auth/refresh call runs and the
  /// others await it, then retry with the new token. Parallel refreshes
  /// of the same token trip the backend's reuse detection and wipe the
  /// session.
  Future<bool>? _refreshing;

  AuthInterceptor(this._ref);

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    // Skip auth header for login endpoint
    if (options.path.contains('/auth/login')) {
      return handler.next(options);
    }

    final token = await SecureStorage.getAccessToken();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    if (err.response?.statusCode != 401) {
      return handler.next(err);
    }

    _refreshing ??= _refreshTokens(err.requestOptions)
        .whenComplete(() => _refreshing = null);
    final refreshed = await _refreshing!;

    if (refreshed) {
      // Retry the original request with the newly stored access token.
      final newToken = await SecureStorage.getAccessToken();
      final opts = err.requestOptions;
      opts.headers['Authorization'] = 'Bearer $newToken';
      try {
        final retryResponse = await Dio().fetch(opts);
        return handler.resolve(retryResponse);
      } catch (_) {
        return handler.next(err);
      }
    }
    handler.next(err);
  }

  /// Calls POST /auth/refresh and persists BOTH rotated tokens.
  /// The backend rotates the refresh token on every call — dropping it
  /// here leaves a revoked token in storage, which the next refresh then
  /// presents and triggers reuse detection (full session revocation).
  Future<bool> _refreshTokens(RequestOptions failed) async {
    final refreshToken = await SecureStorage.getRefreshToken();
    if (refreshToken == null) return false;
    try {
      final response = await Dio().post(
        '${failed.baseUrl}/api/v1/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      if (response.statusCode == 200) {
        final data = response.data as Map<String, dynamic>;
        final newAccess = data['accessToken'] as String?;
        final newRefresh = data['refreshToken'] as String?;
        if (newAccess != null && newRefresh != null) {
          await SecureStorage.setAccessToken(newAccess);
          await SecureStorage.setRefreshToken(newRefresh);
          return true;
        }
      }
      return false;
    } catch (_) {
      // Refresh failed — this is a CONFIRMED invalid session (the server
      // rejected both tokens). Phase 5: clear account-scoped locale sync
      // state BEFORE the storage wipe so no pending write survives with
      // dangling identity, then clear tokens.
      try {
        final userId = await SecureStorage.getUserId();
        final orgId = await SecureStorage.getOrgId();
        await _ref.read(localeSyncProvider.notifier).accountCleared(
              userId: userId,
              organizationId: orgId,
            );
      } catch (_) {
        // Never block the auth teardown on cleanup.
      }
      await SecureStorage.clearAll();
      return false;
    }
  }
}
