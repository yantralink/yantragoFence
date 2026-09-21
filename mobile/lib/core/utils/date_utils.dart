import 'package:intl/intl.dart';

import 'package:yantrago/l10n/generated/app_localizations.dart';

/// Date utilities — formatting and parsing helpers.
class AppDateUtils {
  AppDateUtils._();

  /// Per-(locale, pattern) formatter cache. Formatters resolve the CURRENT
  /// locale (set by the locale controller via Intl.defaultLocale) at format
  /// time, so a language switch renders localized month names immediately.
  /// Locale data is registered by LocaleController before any switch.
  static final Map<String, DateFormat> _formatCache = {};

  static DateFormat _formatOf(String pattern) {
    final locale = Intl.getCurrentLocale();
    return _formatCache.putIfAbsent(
      '$locale|$pattern',
      () => DateFormat(pattern, locale),
    );
  }

  static String formatDate(DateTime? date) {
    if (date == null) return '--';
    return _formatOf('dd MMM yyyy').format(date);
  }

  static String formatTime(DateTime? date) {
    if (date == null) return '--';
    return _formatOf('HH:mm:ss').format(date);
  }

  static String formatDateTime(DateTime? date) {
    if (date == null) return '--';
    return _formatOf('dd MMM yyyy, HH:mm:ss').format(date);
  }

  static String formatShortDateTime(DateTime? date) {
    if (date == null) return '--';
    return _formatOf('dd/MM HH:mm').format(date);
  }

  /// Parses an ISO-8601 timestamp from the backend.
  ///
  /// The backend serializes LocalDateTime as ISO-8601 WITHOUT a timezone
  /// suffix (e.g. "2026-09-21T15:18:05.123"). Dart's DateTime.tryParse
  /// treats such strings as LOCAL time, but they are actually UTC.
  /// This method detects missing timezone info and parses as UTC, then
  /// converts to local time so that relativeTime/difference calculations work
  /// correctly on the user's device.
  static DateTime? parse(String? isoString) {
    if (isoString == null || isoString.isEmpty) return null;
    final trimmed = isoString.trim();
    final hasTimezone = trimmed.endsWith('Z') ||
        trimmed.endsWith('z') ||
        RegExp(r'[+-]\d{2}:\d{2}$').hasMatch(trimmed);
    if (!hasTimezone) {
      final dt = DateTime.tryParse('${trimmed}Z');
      return dt?.toLocal();
    }
    return DateTime.tryParse(trimmed)?.toLocal();
  }

  /// Strict parse — throws FormatException if the string is not a valid
  /// ISO-8601 timestamp. Use for non-nullable timestamp fields.
  static DateTime parseStrict(String isoString) {
    final result = parse(isoString);
    if (result == null) {
      throw FormatException('Invalid ISO-8601 timestamp: $isoString');
    }
    return result;
  }
}

/// Localized relative time — single shared implementation consolidating
/// the five former per-widget duplicates (command_row, command_status_widget,
/// notification_card, machine_card, alert_card).
///
/// Pure function over [AppLocalizations] (no BuildContext) so it is usable
/// from any widget and from unit tests. Format: "just now" / "{n} min ago" /
/// "{n} h ago" / "{n} d ago"; a null timestamp renders the "--" placeholder.
String relativeTime(AppLocalizations l10n, DateTime? timestamp) {
  if (timestamp == null) return '--';
  final Duration d = DateTime.now().difference(timestamp);
  if (d.inMinutes < 1) return l10n.justNow;
  if (d.inHours < 1) return l10n.minutesAgo(d.inMinutes);
  if (d.inDays < 1) return l10n.hoursAgo(d.inHours);
  return l10n.daysAgo(d.inDays);
}
