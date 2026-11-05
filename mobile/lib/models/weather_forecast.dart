/// Normalized forecast response from the backend proxy
/// (`GET /api/v1/machines/{id}/weather/forecast`). Immutable;
/// fromJson matches the backend WeatherForecastDto camelCase shape.
class WeatherForecast {
  /// ISO-8601 UTC instant when the backend fetched the provider data.
  final String? fetchedAt;
  final List<ForecastSlot> slots;

  const WeatherForecast({this.fetchedAt, this.slots = const []});

  factory WeatherForecast.fromJson(Map<String, dynamic> json) {
    final raw = json['slots'];
    return WeatherForecast(
      fetchedAt: json['fetchedAt'] as String?,
      slots: raw is List
          ? raw
              .whereType<Map<String, dynamic>>()
              .map(ForecastSlot.fromJson)
              .toList()
          : const [],
    );
  }
}

/// One normalized 3-hour forecast sample (UTC instant).
class ForecastSlot {
  /// ISO-8601 UTC instant of the sample.
  final String forecastAt;
  final double? temperatureCelsius;
  final int? conditionCode;
  final String? conditionIcon;

  const ForecastSlot({
    required this.forecastAt,
    this.temperatureCelsius,
    this.conditionCode,
    this.conditionIcon,
  });

  /// Parsed UTC instant, or null when malformed — never epoch-fallback.
  DateTime? get instant => DateTime.tryParse(forecastAt);

  factory ForecastSlot.fromJson(Map<String, dynamic> json) {
    return ForecastSlot(
      forecastAt: json['forecastAt'] as String? ?? '',
      temperatureCelsius:
          (json['temperatureCelsius'] as num?)?.toDouble(),
      conditionCode: (json['conditionCode'] as num?)?.toInt(),
      conditionIcon: json['conditionIcon'] as String?,
    );
  }
}
