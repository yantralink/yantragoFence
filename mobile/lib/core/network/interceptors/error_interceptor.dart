import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Error interceptor — normalizes API error responses.
///
/// Converts DioException into human-readable error messages.
class ErrorInterceptor extends Interceptor {
  final Ref _ref;

  ErrorInterceptor(this._ref);

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    String message;

    switch (err.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        message = 'Connection timed out. Please check your network.';
        break;
      case DioExceptionType.connectionError:
        message = 'Cannot connect to server. Please check your internet.';
        break;
      case DioExceptionType.badResponse:
        final statusCode = err.response?.statusCode;
        final data = err.response?.data;
        if (data is Map && data['message'] != null) {
          message = data['message'] as String;
        } else if (statusCode == 401) {
          message = 'Unauthorized. Please log in again.';
        } else if (statusCode == 403) {
          message = 'You do not have permission to perform this action.';
        } else if (statusCode == 404) {
          message = 'Resource not found.';
        } else if (statusCode != null && statusCode >= 500) {
          message = 'Server error. Please try again later.';
        } else {
          message = 'Request failed with status $statusCode.';
        }
        break;
      case DioExceptionType.cancel:
        message = 'Request was cancelled.';
        break;
      case DioExceptionType.badCertificate:
        message = 'SSL certificate error.';
        break;
      case DioExceptionType.unknown:
        message = err.message ?? 'An unexpected error occurred.';
        break;
    }

    handler.next(
      DioException(
        requestOptions: err.requestOptions,
        type: err.type,
        response: err.response,
        error: err.error,
        message: message,
      ),
    );
  }
}
