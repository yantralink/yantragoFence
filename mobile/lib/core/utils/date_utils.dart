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

    if (diff.inSeconds < 60) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 30) return '${diff.inDays}d ago';
    return formatDate(date);
  }

  static DateTime? parse(String? isoString) {
    if (isoString == null || isoString.isEmpty) return null;
    return DateTime.tryParse(isoString);
  }
}
