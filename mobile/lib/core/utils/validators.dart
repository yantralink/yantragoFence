/// Input validators — form field validation helpers.
///
/// Validators return a [ValidationErrorCode] instead of English text so
/// the widget layer maps codes to localized messages (see
/// validationErrorMessage in validation_error_messages.dart). A null
/// return means the value is valid.
enum ValidationErrorCode {
  emailRequired,
  emailInvalid,
  passwordRequired,
  passwordTooShort,
  requiredField,
  imeiRequired,
  imeiInvalid,
  imeiDigits,
  phoneRequired,
  phoneInvalid,
}

class Validators {
  Validators._();

  static ValidationErrorCode? email(String? value) {
    if (value == null || value.isEmpty) {
      return ValidationErrorCode.emailRequired;
    }
    final regex = RegExp(r'^[\w\.-]+@[\w\.-]+\.\w+$');
    if (!regex.hasMatch(value)) {
      return ValidationErrorCode.emailInvalid;
    }
    return null;
  }

  static ValidationErrorCode? password(String? value) {
    if (value == null || value.isEmpty) {
      return ValidationErrorCode.passwordRequired;
    }
    if (value.length < 6) {
      return ValidationErrorCode.passwordTooShort;
    }
    return null;
  }

  static ValidationErrorCode? required(String? value) {
    if (value == null || value.isEmpty) {
      return ValidationErrorCode.requiredField;
    }
    return null;
  }

  static ValidationErrorCode? imei(String? value) {
    if (value == null || value.isEmpty) {
      return ValidationErrorCode.imeiRequired;
    }
    if (value.length != 15) {
      return ValidationErrorCode.imeiInvalid;
    }
    if (!RegExp(r'^\d+$').hasMatch(value)) {
      return ValidationErrorCode.imeiDigits;
    }
    return null;
  }

  static ValidationErrorCode? phone(String? value) {
    if (value == null || value.isEmpty) {
      return ValidationErrorCode.phoneRequired;
    }
    if (!RegExp(r'^\+?[\d\s-]{10,15}$').hasMatch(value)) {
      return ValidationErrorCode.phoneInvalid;
    }
    return null;
  }
}
