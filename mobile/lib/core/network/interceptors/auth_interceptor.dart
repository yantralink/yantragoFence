import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/storage/secure_storage.dart';

/// Auth interceptor — injects JWT Bearer token into every request.
///
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
/// The backend extracts it from the token.
class AuthInterceptor extends Interceptor {
  final Ref _ref;

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
    // If 401, try to refresh token
    if (err.response?.statusCode == 401) {
      final refreshToken = await SecureStorage.getRefreshToken();
      if (refreshToken != null) {
        // Attempt token refresh
        try {
          final dio = Dio();
          final response = await dio.post(
            '${err.requestOptions.baseUrl}/api/auth/refresh',
            data: {'refreshToken': refreshToken},
          );
          if (response.statusCode == 200) {
            final newToken = response.data['accessToken'] as String?;
            if (newToken != null) {
              await SecureStorage.setAccessToken(newToken);
              // Retry original request with new token
              final opts = err.requestOptions;
              opts.headers['Authorization'] = 'Bearer $newToken';
              final retryResponse = await dio.fetch(opts);
              return handler.resolve(retryResponse);
            }
          }
        } catch (_) {
          // Refresh failed — clear tokens and let the error propagate
          await SecureStorage.clearAll();
        }
      }
    }
    handler.next(err);
  }
}
