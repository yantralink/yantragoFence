/// Input validators — form field validation helpers.
class Validators {
  Validators._();

  static String? email(String? value) {
    if (value == null || value.isEmpty) {
      return 'Email is required';
    }
    final regex = RegExp(r'^[\w\.-]+@[\w\.-]+\.\w+$');
    if (!regex.hasMatch(value)) {
      return 'Enter a valid email address';
    }
    return null;
  }

  static String? password(String? value) {
    if (value == null || value.isEmpty) {
      return 'Password is required';
    }
    if (value.length < 6) {
      return 'Password must be at least 6 characters';
    }
    return null;
  }

  static String? required(String? value, {String fieldName = 'This field'}) {
    if (value == null || value.isEmpty) {
      return '$fieldName is required';
    }
    return null;
  }

  static String? imei(String? value) {
    if (value == null || value.isEmpty) {
      return 'IMEI is required';
    }
    if (value.length != 15) {
      return 'IMEI must be 15 digits';
    }
    if (!RegExp(r'^\d+$').hasMatch(value)) {
      return 'IMEI must contain only digits';
    }
    return null;
  }

  static String? phone(String? value) {
    if (value == null || value.isEmpty) {
      return 'Phone number is required';
    }
    if (!RegExp(r'^\+?[\d\s-]{10,15}$').hasMatch(value)) {
      return 'Enter a valid phone number';
    }
    return null;
  }
}
