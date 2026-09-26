import 'package:dio/dio.dart';

import 'package:yantrago/core/network/network_error_messages.dart';

/// Error interceptor — normalizes API error responses.
///
/// Converts DioException into a typed [NetworkErrorCode] attached to the
/// error (see the `networkErrorCode` extra key below) plus a
/// developer-facing message for logs/toString. The widget layer maps the
/// code to a localized message via [networkErrorMessage]; raw backend
/// messages and exception strings are never shown to users.
class ErrorInterceptor extends Interceptor {

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    NetworkErrorCode code;
    String message;

    switch (err.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        code = NetworkErrorCode.connectionTimeout;
        message = 'Connection timed out. Please check your network.';
        break;
      case DioExceptionType.connectionError:
        code = NetworkErrorCode.connectionError;
        message = 'Cannot connect to server. Please check your internet.';
        break;
      case DioExceptionType.badResponse:
        final statusCode = err.response?.statusCode;
        if (statusCode == 401) {
          code = NetworkErrorCode.unauthorized;
          message = 'Unauthorized. Please log in again.';
        } else if (statusCode == 403) {
          code = NetworkErrorCode.forbidden;
          message = 'You do not have permission to perform this action.';
        } else if (statusCode == 404) {
          code = NetworkErrorCode.notFound;
          message = 'Resource not found.';
        } else if (statusCode != null && statusCode >= 500) {
          code = NetworkErrorCode.serverError;
          message = 'Server error. Please try again later.';
        } else {
          code = NetworkErrorCode.requestFailed;
          message = 'Request failed with status $statusCode.';
        }
        break;
      case DioExceptionType.cancel:
        code = NetworkErrorCode.cancelled;
        message = 'Request was cancelled.';
        break;
      case DioExceptionType.badCertificate:
        code = NetworkErrorCode.badCertificate;
        message = 'SSL certificate error.';
        break;
      case DioExceptionType.unknown:
      case DioExceptionType.transformTimeout:
        code = NetworkErrorCode.unexpected;
        message = err.message ?? 'An unexpected error occurred.';
        break;
    }

    final options = err.requestOptions;
    // DioException has no `extra` of its own — attach the typed code to the
    // shared RequestOptions so callers can look it up:
    //   networkErrorCodeFrom(DioException)
    options.extra[kNetworkErrorCodeExtraKey] = code;

    handler.next(
      DioException(
        requestOptions: options,
        type: err.type,
        response: err.response,
        error: err.error,
        message: message,
      ),
    );
  }
}
