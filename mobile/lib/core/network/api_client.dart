import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/network/interceptors/auth_interceptor.dart';
import 'package:yantrago/core/network/interceptors/error_interceptor.dart';
import 'package:dio/dio.dart';

/// Dio HTTP client provider with auth and error interceptors.
///
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
/// The auth interceptor injects the Bearer token into every request.
///
/// SEC-08: Response-body logging is disabled in all builds. Debug
/// instrumentation is explicitly enabled only in debug mode and is
/// redacted — it logs only request method, path, and status code,
/// never headers, bodies, tokens, or PII.
final apiClientProvider = Provider<Dio>((ref) {
  final dio = Dio(BaseOptions(
    baseUrl: AppConfig.apiBaseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 30),
    sendTimeout: const Duration(seconds: 10),
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  ));

  dio.interceptors.addAll([
    AuthInterceptor(ref),
    ErrorInterceptor(),
  ]);

  // SEC-08: Only add logging in debug mode, and only safe metadata
  // (method, path, status code). Never log headers, bodies, tokens,
  // passwords, coordinates, or push tokens.
  if (kDebugMode) {
    dio.interceptors.add(_SafeDebugLogInterceptor());
  }

  return dio;
});

/// SEC-08: Safe debug-only log interceptor.
///
/// Logs only:
/// - Request method and path (no query params, no headers, no body)
/// - Response status code and duration
/// - Error status code
///
/// Never logs: response bodies, request bodies, headers, tokens,
/// passwords, coordinates, customer data, or push tokens.
class _SafeDebugLogInterceptor extends Interceptor {
  static final Map<int, DateTime> _requestStartTimes = {};

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    _requestStartTimes[options.hashCode] = DateTime.now();
    // Log only method and path — no headers, no body, no query params.
    debugPrint('[DIO] ${options.method} ${options.path}');
    handler.next(options);
  }

  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) {
    final start = _requestStartTimes.remove(response.requestOptions.hashCode);
    final duration = start != null
        ? DateTime.now().difference(start).inMilliseconds
        : null;
    // Log only status code and duration — no body, no headers.
    debugPrint(
        '[DIO] ${response.statusCode} ${response.requestOptions.method} '
        '${response.requestOptions.path}${duration != null ? ' (${duration}ms)' : ''}');
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    _requestStartTimes.remove(err.requestOptions.hashCode);
    // Log only error type and status code — no body, no headers.
    final status = err.response?.statusCode ?? 'N/A';
    debugPrint('[DIO] ERROR $status ${err.requestOptions.method} '
        '${err.requestOptions.path} — ${err.type}');
    handler.next(err);
  }
}
