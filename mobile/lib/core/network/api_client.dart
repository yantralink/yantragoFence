import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/network/interceptors/auth_interceptor.dart';
import 'package:yantrago/core/network/interceptors/error_interceptor.dart';
import 'package:dio/dio.dart';

/// Dio HTTP client provider with auth and error interceptors.
///
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
/// The auth interceptor injects the Bearer token into every request.
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
    ErrorInterceptor(ref),
    LogInterceptor(
      request: true,
      requestHeader: false,
      responseHeader: false,
      responseBody: true,
      error: true,
      logPrint: (obj) => print('[DIO] $obj'),
    ),
  ]);

  return dio;
});
