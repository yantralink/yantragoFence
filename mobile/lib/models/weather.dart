import 'package:flutter/material.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';

/// Current weather for a machine's location, resolved server-side via
/// the backend weather proxy (`GET /api/v1/machines/{id}/weather`).
/// Immutable, fromJson matches the backend WeatherDto camelCase shape.
class Weather {
  final double? temperatureCelsius;
  final double? apparentTemperatureCelsius;
  final int? humidityPercent;
  final double? precipitationMm;

  /// Provider condition id (OWM weather id) — always localize from
  /// this, never from [conditionText] which is English-only.
  final int? conditionCode;
  final String? conditionText;

  /// Provider icon code (e.g. "03d") — trailing `d`/`n` = day/night.
  final String? conditionIcon;
  final double? windSpeedKph;
  final int? windDirectionDegrees;
  final double? windGustsKph;

  /// Provider observation time (ISO-8601 instant).
  final String? observedAt;

  const Weather({
    this.temperatureCelsius,
    this.apparentTemperatureCelsius,
    this.humidityPercent,
    this.precipitationMm,
    this.conditionCode,
    this.conditionText,
    this.conditionIcon,
    this.windSpeedKph,
    this.windDirectionDegrees,
    this.windGustsKph,
    this.observedAt,
  });

  bool get isDay => !(conditionIcon ?? 'd').endsWith('n');

  DateTime? get observedTime =>
      observedAt == null ? null : DateTime.tryParse(observedAt!);

  /// Wind direction as a compass abbreviation (N/NE/E/...).
  String? get windCompass {
    final deg = windDirectionDegrees;
    if (deg == null) return null;
    const dirs = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    return dirs[(((deg % 360) + 22.5) ~/ 45) % 8];
  }

  factory Weather.fromJson(Map<String, dynamic> json) {
    return Weather(
      temperatureCelsius: (json['temperatureCelsius'] as num?)?.toDouble(),
      apparentTemperatureCelsius:
          (json['apparentTemperatureCelsius'] as num?)?.toDouble(),
      humidityPercent: (json['humidityPercent'] as num?)?.toInt(),
      precipitationMm: (json['precipitationMm'] as num?)?.toDouble(),
      conditionCode: (json['conditionCode'] as num?)?.toInt(),
      conditionText: json['conditionText'] as String?,
      conditionIcon: json['conditionIcon'] as String?,
      windSpeedKph: (json['windSpeedKph'] as num?)?.toDouble(),
      windDirectionDegrees: (json['windDirectionDegrees'] as num?)?.toInt(),
      windGustsKph: (json['windGustsKph'] as num?)?.toDouble(),
      observedAt: json['observedAt'] as String?,
    );
  }
}

/// Maps an OWM condition id (2xx–8xx) to a localized label — raw
/// provider `conditionText` is English-only and must never render.
String weatherConditionLabel(AppLocalizations l10n, int? code) {
  if (code == null) return l10n.weatherUnavailable;
  if (code >= 200 && code < 300) return l10n.weatherThunderstorm;
  if (code >= 300 && code < 400) return l10n.weatherDrizzle;
  if (code >= 500 && code < 600) return l10n.weatherRain;
  if (code >= 600 && code < 700) return l10n.weatherSnow;
  if (code >= 700 && code < 800) return l10n.weatherFog;
  if (code == 800) return l10n.weatherClear;
  return l10n.weatherClouds;
}

/// Forecast-specific label — finer distinctions than the broad
/// [weatherConditionLabel] groups (801/802 partly cloudy, light-rain
/// codes vs heavier rain). Everything else delegates to the shared
/// mapping so labels stay consistent with the current-weather strip.
String weatherForecastConditionLabel(AppLocalizations l10n, int? code) {
  if (code == 801 || code == 802) return l10n.weatherConditionPartlyCloudy;
  if (code != null &&
      ((code >= 500 && code <= 501) || (code >= 520 && code <= 522))) {
    return l10n.weatherConditionLightRain;
  }
  return weatherConditionLabel(l10n, code);
}

/// Maps an OWM condition id + day/night flag to a Material glyph.
IconData weatherConditionIcon(int? code, {bool isDay = true}) {
  if (code == null) return Icons.cloud_off_outlined;
  if (code >= 200 && code < 300) return Icons.thunderstorm_outlined;
  if (code >= 300 && code < 400) return Icons.grain;
  if (code >= 500 && code < 600) return Icons.umbrella_outlined;
  if (code >= 600 && code < 700) return Icons.ac_unit;
  if (code >= 700 && code < 800) return Icons.foggy;
  if (code == 800) {
    return isDay ? Icons.wb_sunny_outlined : Icons.nightlight_outlined;
  }
  return isDay ? Icons.wb_cloudy_outlined : Icons.nights_stay_outlined;
}
