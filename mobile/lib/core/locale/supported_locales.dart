/// Supported app locales — single source of truth for language codes and
/// native display names.
///
/// Language names are hardcoded per-language in their native script and are
/// NOT localized via ARB (a Hindi user sees "English", not "अंग्रेज़ी").
/// Keep the codes in sync with lib/l10n/app_*.arb and the backend
/// `preferred_locale` CHECK constraint (en/hi/mr).
const String kDefaultLanguageCode = 'en';

/// Ordered map of language code → native display name.
const Map<String, String> kLanguageChoices = <String, String>{
  'en': 'English',
  'hi': 'हिन्दी (Hindi)',
  'mr': 'मराठी (Marathi)',
};

/// The set of language codes the app accepts.
const Set<String> kSupportedLanguageCodes = kLanguageChoices.keys;
