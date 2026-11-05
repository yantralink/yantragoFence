/// One day's aggregated weather forecast for a machine's location.
/// Mock/preview model — matches the planned backend
/// `WeatherForecastDto` daily-row shape (camelCase JSON).
class DailyForecast {
  /// Calendar day in the user's local timezone.
  final DateTime date;

  /// Provider condition id (OWM weather id) — localize from this,
  /// never from [conditionText] which is English-only.
  final int? conditionCode;
  final String? conditionText;

  /// Provider icon code (e.g. "03d") — trailing `d`/`n` = day/night.
  final String? conditionIcon;
  final double? tempMinCelsius;
  final double? tempMaxCelsius;

  /// True when the day has fewer forecast samples than a full day —
  /// the UI labels it "Partial forecast" instead of implying
  /// full-day extremes.
  final bool isPartial;

  const DailyForecast({
    required this.date,
    this.conditionCode,
    this.conditionText,
    this.conditionIcon,
    this.tempMinCelsius,
    this.tempMaxCelsius,
    this.isPartial = false,
  });

  bool get isDay => !(conditionIcon ?? 'd').endsWith('n');
}
