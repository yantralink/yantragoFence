import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/core/utils/validators.dart';

/// Maps a [ValidationErrorCode] to its localized message.
///
/// Widget layer helper — validator callbacks in form fields call this with
/// the code returned by [Validators]. Never call it from a validator class
/// itself; codes keep the validation layer context-free.
String validationErrorMessage(
  AppLocalizations l10n,
  ValidationErrorCode code,
) {
  switch (code) {
    case ValidationErrorCode.emailRequired:
      return l10n.validationEmailRequired;
    case ValidationErrorCode.emailInvalid:
      return l10n.validationEmailInvalid;
    case ValidationErrorCode.passwordRequired:
      return l10n.validationPasswordRequired;
    case ValidationErrorCode.passwordTooShort:
      return l10n.validationPasswordTooShort;
    case ValidationErrorCode.requiredField:
      return l10n.validationRequiredField;
    case ValidationErrorCode.imeiRequired:
      return l10n.validationImeiRequired;
    case ValidationErrorCode.imeiInvalid:
      return l10n.validationImeiInvalid;
    case ValidationErrorCode.imeiDigits:
      return l10n.validationImeiDigits;
    case ValidationErrorCode.phoneRequired:
      return l10n.validationPhoneRequired;
    case ValidationErrorCode.phoneInvalid:
      return l10n.validationPhoneInvalid;
  }
}
