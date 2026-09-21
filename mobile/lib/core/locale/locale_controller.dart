import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'locale_preferences.dart';
import 'supported_locales.dart';

/// Device locale list, overridable in tests.
final deviceLocalesProvider = Provider<List<Locale>>((ref) {
  return WidgetsBinding.instance.platformDispatcher.locales;
});

/// SharedPreferences instance, preloaded in main() before runApp and injected
/// via ProviderScope overrides. Tests override it with mock initial values.
///
/// Null (default) means storage was unavailable at startup — the controller
/// then falls back to device-locale resolution and marks writes as failed.
final sharedPreferencesProvider = Provider<SharedPreferences?>((ref) => null);

/// Status of persisting the current locale to device storage.
enum LocaleSaveStatus {
  /// Nothing to persist (or the last write succeeded).
  idle,

  /// A write is in flight.
  saving,

  /// The last write failed — the in-session UI switch is applied, but the
  /// choice may NOT survive an app restart. The UI must offer retry.
  failed,
}

class LocaleControllerState {
  final Locale locale;
  final LocaleSaveStatus saveStatus;

  const LocaleControllerState(this.locale, this.saveStatus);

  @override
  bool operator ==(Object other) =>
      other is LocaleControllerState &&
      other.locale == locale &&
      other.saveStatus == saveStatus;

  @override
  int get hashCode => Object.hash(locale, saveStatus);
}

/// Locale controller — holds the app's effective UI locale and persists the
/// explicit choice to device storage.
///
/// Phase 3 policy:
/// - Initial resolution (ordered): valid saved choice → first supported
///   device locale (region stripped: `hi-IN` → `hi`) → English.
/// - `setLocale` switches the UI immediately, then persists. A failed write
///   is surfaced as [LocaleSaveStatus.failed] with retry — it never claims
///   restart persistence succeeded.
/// - Rapid selections are coalesced via a monotonic revision counter: a
///   superseded write's late completion cannot restore an older choice or
///   flip the status of a newer one.
/// - Account-scoped server sync is Phase 5; this phase only persists the
///   device-level preference (which survives logout by design).
final localeControllerProvider =
    NotifierProvider<LocaleController, LocaleControllerState>(
        LocaleController.new);

class LocaleController extends Notifier<LocaleControllerState> {
  int _writeRevision = 0;

  @override
  LocaleControllerState build() {
    final prefs = ref.watch(sharedPreferencesProvider);
    final saved = prefs == null ? null : LocalePreferences.readValidLanguageCode(prefs);
    final resolved =
        resolveInitialLocale(saved, ref.watch(deviceLocalesProvider));
    // Initial resolution cannot await (sync build); see _ensureDateSymbols —
    // registration completes before the first frame formats a date.
    unawaited(_ensureDateSymbols(resolved.languageCode));
    Intl.defaultLocale = resolved.languageCode;
    return LocaleControllerState(resolved, LocaleSaveStatus.idle);
  }

  /// Ordered initial resolution: saved choice wins; otherwise the first
  /// supported device locale; otherwise English.
  static Locale resolveInitialLocale(
    String? savedCode,
    List<Locale> deviceLocales,
  ) {
    // savedCode is already validated by the caller (null = no valid choice).
    if (savedCode != null) return Locale(savedCode);
    for (final deviceLocale in deviceLocales) {
      if (kSupportedLanguageCodes.contains(deviceLocale.languageCode)) {
        return Locale(deviceLocale.languageCode);
      }
    }
    return const Locale(kDefaultLanguageCode);
  }

  /// Switches the UI locale immediately, then persists the choice.
  ///
  /// If date-symbol registration fails for the requested locale the switch
  /// is aborted safely (previous locale stays active) and reported as a
  /// failed save.
  Future<void> setLocale(Locale locale) async {
    if (locale == state.locale) return;
    final revision = ++_writeRevision;

    final symbolsReady = await _ensureDateSymbols(locale.languageCode);
    if (!symbolsReady) {
      _updateFor(revision, LocaleControllerState(
        state.locale,
        LocaleSaveStatus.failed,
      ));
      return;
    }

    Intl.defaultLocale = locale.languageCode;
    _updateFor(revision, LocaleControllerState(
      locale,
      LocaleSaveStatus.saving,
    ));
    await _persist(locale, revision);
  }

  /// Re-attempts persisting the CURRENT locale after a failed save.
  Future<void> retrySave() async {
    await _persist(state.locale, ++_writeRevision);
  }

  Future<void> _persist(Locale locale, int revision) async {
    final prefs = ref.read(sharedPreferencesProvider);
    try {
      if (prefs == null) {
        throw StateError('SharedPreferences unavailable');
      }
      await LocalePreferences.writeLanguageCode(prefs, locale.languageCode);
      _updateFor(revision, LocaleControllerState(
        locale,
        LocaleSaveStatus.idle,
      ));
    } catch (_) {
      _updateFor(revision, LocaleControllerState(
        locale,
        LocaleSaveStatus.failed,
      ));
    }
  }

  /// Applies [next] only if [revision] is still the latest write — the
  /// coalescing guard. Also drops the update when the notifier was disposed.
  void _updateFor(int revision, LocaleControllerState next) {
    if (revision != _writeRevision) return;
    try {
      state = next;
    } on StateError {
      // Notifier element already disposed — drop the late update.
    }
  }

  /// Registers intl date symbols for [code] (and always for English).
  ///
  /// Awaited on explicit switches so widgets never format a date with
  /// missing symbols. In build() (initial resolution) it is fire-and-forget:
  /// the embedded intl data registers synchronously on the first microtask,
  /// before the first frame formats anything.
  Future<bool> _ensureDateSymbols(String code) async {
    var ok = true;
    for (final c in <String>{code, kDefaultLanguageCode}) {
      try {
        await initializeDateFormatting(c, null);
      } catch (_) {
        if (c == code) ok = false;
      }
    }
    return ok;
  }
}
