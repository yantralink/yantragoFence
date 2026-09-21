// Unit tests for the localization foundation (Phase 1).
//
// These tests construct AppLocalizations('en') directly from the generated
// import (CI runs `flutter gen-l10n` before `flutter test`), so no delegates
// or BuildContext are needed.

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/network/interceptors/error_interceptor.dart';
import 'package:yantrago/core/network/network_error_messages.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/core/utils/validation_error_messages.dart';
import 'package:yantrago/core/utils/validators.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';

void main() {
  final AppLocalizations l10n = AppLocalizations('en');

  group('Validators → ValidationErrorCode', () {
    test('email', () {
      expect(Validators.email(null), ValidationErrorCode.emailRequired);
      expect(Validators.email(''), ValidationErrorCode.emailRequired);
      expect(Validators.email('nope'), ValidationErrorCode.emailInvalid);
      expect(Validators.email('a@b.co'), isNull);
    });

    test('password', () {
      expect(Validators.password(null), ValidationErrorCode.passwordRequired);
      expect(Validators.password('12345'), ValidationErrorCode.passwordTooShort);
      expect(Validators.password('123456'), isNull);
    });

    test('required', () {
      expect(Validators.required(null), ValidationErrorCode.requiredField);
      expect(Validators.required('x'), isNull);
    });

    test('imei', () {
      expect(Validators.imei(null), ValidationErrorCode.imeiRequired);
      expect(Validators.imei('1234'), ValidationErrorCode.imeiInvalid);
      expect(Validators.imei('12345678901234a'), ValidationErrorCode.imeiDigits);
      expect(Validators.imei('123456789012345'), isNull);
    });

    test('phone', () {
      expect(Validators.phone(null), ValidationErrorCode.phoneRequired);
      expect(Validators.phone('abc'), ValidationErrorCode.phoneInvalid);
      expect(Validators.phone('+91 98765 43210'), isNull);
    });
  });

  group('validationErrorMessage mapping', () {
    test('every code maps to a non-empty English message', () {
      final expectations = <ValidationErrorCode, String>{
        ValidationErrorCode.emailRequired: 'Email is required',
        ValidationErrorCode.emailInvalid: 'Enter a valid email address',
        ValidationErrorCode.passwordRequired: 'Password is required',
        ValidationErrorCode.passwordTooShort:
            'Password must be at least 6 characters',
        ValidationErrorCode.requiredField: 'This field is required',
        ValidationErrorCode.imeiRequired: 'IMEI is required',
        ValidationErrorCode.imeiInvalid: 'IMEI must be 15 digits',
        ValidationErrorCode.imeiDigits: 'IMEI must contain only digits',
        ValidationErrorCode.phoneRequired: 'Phone number is required',
        ValidationErrorCode.phoneInvalid: 'Enter a valid phone number',
      };
      expectations.forEach((code, expected) {
        expect(validationErrorMessage(l10n, code), expected,
            reason: 'code $code');
      });
    });
  });

  group('networkErrorCodeFrom + networkErrorMessage mapping', () {
    test('every code maps to a non-empty English message', () {
      final expectations = <NetworkErrorCode, String>{
        NetworkErrorCode.connectionTimeout:
            'Connection timed out. Please check your network.',
        NetworkErrorCode.connectionError:
            'Cannot connect to server. Please check your internet.',
        NetworkErrorCode.unauthorized: 'Unauthorized. Please log in again.',
        NetworkErrorCode.forbidden:
            'You do not have permission to perform this action.',
        NetworkErrorCode.notFound: 'Resource not found.',
        NetworkErrorCode.serverError: 'Server error. Please try again later.',
        NetworkErrorCode.requestFailed: 'Request failed.',
        NetworkErrorCode.cancelled: 'Request was cancelled.',
        NetworkErrorCode.badCertificate: 'SSL certificate error.',
        NetworkErrorCode.unexpected: 'An unexpected error occurred.',
      };
      expectations.forEach((code, expected) {
        expect(networkErrorMessage(l10n, code), expected,
            reason: 'code $code');
      });
    });

    test('null code falls back to the generic message', () {
      expect(networkErrorMessage(l10n, null),
          'An unexpected error occurred.');
    });

    test('ErrorInterceptor attaches the code to the request options', () async {
      // The interceptor needs a Ref (it is unused in onError, but the
      // constructor requires one) — obtain one from a container.
      final container = ProviderContainer();
      addTearDown(container.dispose);
      final interceptor = ErrorInterceptor(
        container.read(_refProvider),
      );
      final options = RequestOptions(path: '/api/v1/x');
      final original = DioException(
        requestOptions: options,
        type: DioExceptionType.connectionTimeout,
      );

      DioException? forwarded;
      interceptor.onError(
        original,
        _CapturingHandler((err) => forwarded = err),
      );

      expect(forwarded, isNotNull);
      expect(networkErrorCodeFrom(forwarded!),
          NetworkErrorCode.connectionTimeout);
    });
  });

  group('relativeTime', () {
    test('null timestamp renders the placeholder', () {
      expect(relativeTime(l10n, null), '--');
    });

    test('formats each relative bucket in English', () {
      final now = DateTime.now();
      expect(relativeTime(l10n, now), 'just now');
      expect(relativeTime(l10n, now.subtract(const Duration(minutes: 5))),
          '5 min ago');
      expect(relativeTime(l10n, now.subtract(const Duration(hours: 3))),
          '3 h ago');
      expect(relativeTime(l10n, now.subtract(const Duration(days: 2))),
          '2 d ago');
    });
  });

  group('AppLocalizations wiring', () {
    test('supportedLocales contains en, hi and mr', () {
      final codes =
          AppLocalizations.supportedLocales.map((l) => l.languageCode);
      expect(codes, containsAll(<String>['en', 'hi', 'mr']));
    });
  });
}

/// Provider exposing the container's Ref so ErrorInterceptor can be built
/// in tests without network access.
final _refProvider = Provider<Ref>((ref) => ref);

/// Minimal interceptor handler that forwards the modified error to a sink.
class _CapturingHandler extends ErrorInterceptorHandler {
  _CapturingHandler(this._onNext);

  final void Function(DioException error) _onNext;

  @override
  void next(DioException err) {
    _onNext(err);
  }
}
