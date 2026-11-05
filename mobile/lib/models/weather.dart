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
/// Vendored WeatherIcons font family (erikflowers/weather-icons, MIT) —
/// declared in pubspec.yaml; codepoints match weather_icons_g.dart.
const String _wiFont = 'WeatherIcons';

IconData _wi(int dayCode, int nightCode, {required bool isDay}) =>
    IconData(isDay ? dayCode : nightCode, fontFamily: _wiFont);

IconData weatherConditionIcon(int? code, {bool isDay = true}) {
  if (code == null) return Icons.cloud_off_outlined;
  if (code >= 200 && code < 300) {
    return (code >= 230 && code < 240)
        ? _wi(0xf00e, 0xf03a, isDay: isDay) // storm showers
        : _wi(0xf010, 0xf03b, isDay: isDay); // thunderstorm
  }
  if (code >= 300 && code < 400) {
    return _wi(0xf00b, 0xf039, isDay: isDay); // drizzle
  }
  if (code >= 500 && code < 600) {
    return (code >= 520 && code < 600)
        ? _wi(0xf009, 0xf037, isDay: isDay) // showers
        : _wi(0xf008, 0xf036, isDay: isDay); // rain
  }
  if (code >= 600 && code < 700) {
    if (code == 612) return _wi(0xf068, 0xf069, isDay: isDay); // sleet storm
    if (code == 611) return _wi(0xf0b2, 0xf0b3, isDay: isDay); // sleet
    return (code >= 622 && code < 700)
        ? _wi(0xf065, 0xf066, isDay: isDay) // heavy snow
        : _wi(0xf00a, 0xf038, isDay: isDay); // snow
  }
  if (code >= 700 && code < 800) return _wi(0xf003, 0xf04a, isDay: isDay); // fog
  if (code == 800) return _wi(0xf00d, 0xf02e, isDay: isDay); // clear
  if (code == 801) {
    return _wi(0xf00c, 0xf083, isDay: isDay); // few clouds (sun + cloud)
  }
  if (code == 802) return _wi(0xf002, 0xf031, isDay: isDay); // scattered
  return _wi(0xf000, 0xf02f, isDay: isDay); // broken/overcast
}
