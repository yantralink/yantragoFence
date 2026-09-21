import 'package:intl/intl.dart';

/// Date utilities — formatting and parsing helpers.
class AppDateUtils {
  AppDateUtils._();

  static final DateFormat _dateFormat = DateFormat('dd MMM yyyy');
  static final DateFormat _timeFormat = DateFormat('HH:mm:ss');
  static final DateFormat _dateTimeFormat = DateFormat('dd MMM yyyy, HH:mm:ss');
  static final DateFormat _shortDateTime = DateFormat('dd/MM HH:mm');

  static String formatDate(DateTime? date) {
    if (date == null) return '--';
    return _dateFormat.format(date);
  }

  static String formatTime(DateTime? date) {
    if (date == null) return '--';
    return _timeFormat.format(date);
  }

  static String formatDateTime(DateTime? date) {
    if (date == null) return '--';
    return _dateTimeFormat.format(date);
  }

  static String formatShortDateTime(DateTime? date) {
    if (date == null) return '--';
    return _shortDateTime.format(date);
  }

  static String timeAgo(DateTime? date) {
    if (date == null) return 'Never';
    final now = DateTime.now();
    final diff = now.difference(date);

    if (diff.inSeconds < 0) return 'Just now';
    if (diff.inSeconds < 60) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 30) return '${diff.inDays}d ago';
    return formatDate(date);
  }

  /// Parses an ISO-8601 timestamp from the backend.
  ///
  /// The backend serializes LocalDateTime as ISO-8601 WITHOUT a timezone
  /// suffix (e.g. "2026-09-21T15:18:05.123"). Dart's DateTime.tryParse
  /// treats such strings as LOCAL time, but they are actually UTC.
  /// This method detects missing timezone info and parses as UTC, then
  /// converts to local time so that timeAgo/difference calculations work
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
