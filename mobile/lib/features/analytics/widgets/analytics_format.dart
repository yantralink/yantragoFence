import 'package:intl/intl.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';

/// "45 min" / "2h 5m" — compact duration for timeline rows.
String formatMinutes(AppLocalizations l10n, int minutes) {
  if (minutes >= 60) {
    return l10n.durationHm(minutes ~/ 60, minutes % 60);
  }
  return l10n.durationM(minutes);
}

/// "09:15 – 10:02" within one day, "26/09 09:15 – 27/09 06:00"
/// across days.
String formatTimeRange(DateTime start, DateTime? end) {
  final startLabel = DateFormat('dd/MM HH:mm').format(start);
  if (end == null) return '$startLabel – …';
  final sameDay = start.day == end.day &&
      start.month == end.month &&
      start.year == end.year;
  final endLabel = sameDay
      ? DateFormat('HH:mm').format(end)
      : DateFormat('dd/MM HH:mm').format(end);
  return '$startLabel – $endLabel';
}
