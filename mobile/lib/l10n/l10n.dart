import 'package:flutter/material.dart';

import 'package:yantrago/l10n/generated/app_localizations.dart';

/// Convenience accessor for localized strings.
///
/// `AppLocalizations.of(context)` returns null when no localization
/// delegate is installed (e.g. component/widget tests that pump a plain
/// MaterialApp). The fallback resolves English directly so widgets never
/// crash and tests keep their English expectations.
extension L10nContext on BuildContext {
  AppLocalizations get l10n =>
      AppLocalizations.of(this) ?? lookupAppLocalizations(const Locale('en'));
}
