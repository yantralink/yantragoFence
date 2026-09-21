import 'package:dio/dio.dart';

import 'package:yantrago/l10n/generated/app_localizations.dart';

/// Extra key under which [ErrorInterceptor] attaches the typed
/// [NetworkErrorCode] to the request options of every DioException.
const String kNetworkErrorCodeExtraKey = 'networkErrorCode';

/// Typed network error codes — attached by the error interceptor, mapped
/// to localized messages by [networkErrorMessage].
enum NetworkErrorCode {
  connectionTimeout,
  connectionError,
  unauthorized,
  forbidden,
  notFound,
  serverError,
  requestFailed,
  cancelled,
  badCertificate,
  unexpected,
}

/// Extracts the [NetworkErrorCode] attached by [ErrorInterceptor].
///
/// Returns null when no code is present (e.g. an error that never passed
/// through the interceptor).
NetworkErrorCode? networkErrorCodeFrom(DioException error) {
  final code = error.requestOptions.extra[kNetworkErrorCodeExtraKey];
  return code is NetworkErrorCode ? code : null;
}

/// Maps a [NetworkErrorCode] to its localized user-facing message.
///
/// Widget layer helper. A null or unknown code falls back to the generic
/// localized message — never show raw backend messages or
/// exception.toString() to users.
String networkErrorMessage(AppLocalizations l10n, NetworkErrorCode? code) {
  switch (code) {
    case NetworkErrorCode.connectionTimeout:
      return l10n.errorConnectionTimeout;
    case NetworkErrorCode.connectionError:
      return l10n.errorConnectionError;
    case NetworkErrorCode.unauthorized:
      return l10n.errorUnauthorized;
    case NetworkErrorCode.forbidden:
      return l10n.errorForbidden;
    case NetworkErrorCode.notFound:
      return l10n.errorNotFound;
    case NetworkErrorCode.serverError:
      return l10n.errorServerError;
    case NetworkErrorCode.requestFailed:
      return l10n.errorRequestFailed;
    case NetworkErrorCode.cancelled:
      return l10n.errorCancelled;
    case NetworkErrorCode.badCertificate:
      return l10n.errorBadCertificate;
    case NetworkErrorCode.unexpected:
    case null:
      return l10n.errorUnexpected;
  }
}
