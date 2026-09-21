import 'package:shared_preferences/shared_preferences.dart';

import 'supported_locales.dart';

/// Device-level language preference storage (SharedPreferences, NOT secure
/// storage — the language choice is not a secret).
///
/// This is deliberately a SEPARATE store from SecureStorage: logout clears
/// tokens via SecureStorage.clearAll(), which never touches these keys, so
/// the login-screen language choice survives logout and account switches.
/// Account-scoped sync (Phase 5) layers on top of this device preference.
class LocalePreferences {
  LocalePreferences._();

  static const String languageKey = 'app_language';

  /// Returns the saved code only when it is a supported language.
  ///
  /// Anything else (missing, corrupt, unsupported, region form like "hi-IN")
  /// is treated as "no explicit choice" so device-locale fallback applies.
  static String? readValidLanguageCode(SharedPreferences prefs) {
    final code = prefs.getString(languageKey);
    return code != null && kSupportedLanguageCodes.contains(code) ? code : null;
  }

  /// Persists a supported language code.
  ///
  /// Throws if [code] is not supported (programming error) or when the
  /// underlying store fails — the caller surfaces that as a save failure.
  static Future<void> writeLanguageCode(
    SharedPreferences prefs,
    String code,
  ) {
    if (!kSupportedLanguageCodes.contains(code)) {
      throw ArgumentError('Unsupported language code: $code');
    }
    return prefs.setString(languageKey, code);
  }
}
