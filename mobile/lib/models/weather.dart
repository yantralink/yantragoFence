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
/// IconData.codePoint must be const, so glyphs are declared individually.
const String _wiFont = 'WeatherIcons';

IconData _wi(IconData day, IconData night, {required bool isDay}) =>
    isDay ? day : night;

// Day glyphs
const IconData _wiStormShowersD = IconData(0xf00e, fontFamily: _wiFont);
const IconData _wiThunderstormD = IconData(0xf010, fontFamily: _wiFont);
const IconData _wiSprinkleD = IconData(0xf00b, fontFamily: _wiFont);
const IconData _wiShowersD = IconData(0xf009, fontFamily: _wiFont);
const IconData _wiRainD = IconData(0xf008, fontFamily: _wiFont);
const IconData _wiSleetStormD = IconData(0xf068, fontFamily: _wiFont);
const IconData _wiSleetD = IconData(0xf0b2, fontFamily: _wiFont);
const IconData _wiSnowWindD = IconData(0xf065, fontFamily: _wiFont);
const IconData _wiSnowD = IconData(0xf00a, fontFamily: _wiFont);
const IconData _wiFogD = IconData(0xf003, fontFamily: _wiFont);
const IconData _wiSunnyD = IconData(0xf00d, fontFamily: _wiFont);
const IconData _wiFewCloudsD = IconData(0xf00c, fontFamily: _wiFont);
const IconData _wiScatteredD = IconData(0xf002, fontFamily: _wiFont);
const IconData _wiOvercastD = IconData(0xf000, fontFamily: _wiFont);
// Night glyphs
const IconData _wiStormShowersN = IconData(0xf03a, fontFamily: _wiFont);
const IconData _wiThunderstormN = IconData(0xf03b, fontFamily: _wiFont);
const IconData _wiSprinkleN = IconData(0xf039, fontFamily: _wiFont);
const IconData _wiShowersN = IconData(0xf037, fontFamily: _wiFont);
const IconData _wiRainN = IconData(0xf036, fontFamily: _wiFont);
const IconData _wiSleetStormN = IconData(0xf069, fontFamily: _wiFont);
const IconData _wiSleetN = IconData(0xf0b3, fontFamily: _wiFont);
const IconData _wiSnowWindN = IconData(0xf066, fontFamily: _wiFont);
const IconData _wiSnowN = IconData(0xf038, fontFamily: _wiFont);
const IconData _wiFogN = IconData(0xf04a, fontFamily: _wiFont);
const IconData _wiClearN = IconData(0xf02e, fontFamily: _wiFont);
const IconData _wiFewCloudsN = IconData(0xf083, fontFamily: _wiFont);
const IconData _wiScatteredN = IconData(0xf031, fontFamily: _wiFont);
const IconData _wiOvercastN = IconData(0xf02f, fontFamily: _wiFont);

IconData weatherConditionIcon(int? code, {bool isDay = true}) {
  if (code == null) return Icons.cloud_off_outlined;
  if (code >= 200 && code < 300) {
    return (code >= 230 && code < 240)
        ? _wi(_wiStormShowersD, _wiStormShowersN, isDay: isDay)
        : _wi(_wiThunderstormD, _wiThunderstormN, isDay: isDay);
  }
  if (code >= 300 && code < 400) {
    return _wi(_wiSprinkleD, _wiSprinkleN, isDay: isDay);
  }
  if (code >= 500 && code < 600) {
    return (code >= 520 && code < 600)
        ? _wi(_wiShowersD, _wiShowersN, isDay: isDay)
        : _wi(_wiRainD, _wiRainN, isDay: isDay);
  }
  if (code >= 600 && code < 700) {
    if (code == 612) return _wi(_wiSleetStormD, _wiSleetStormN, isDay: isDay);
    if (code == 611) return _wi(_wiSleetD, _wiSleetN, isDay: isDay);
    return (code >= 622 && code < 700)
        ? _wi(_wiSnowWindD, _wiSnowWindN, isDay: isDay)
        : _wi(_wiSnowD, _wiSnowN, isDay: isDay);
  }
  if (code >= 700 && code < 800) return _wi(_wiFogD, _wiFogN, isDay: isDay);
  if (code == 800) return _wi(_wiSunnyD, _wiClearN, isDay: isDay);
  if (code == 801) return _wi(_wiFewCloudsD, _wiFewCloudsN, isDay: isDay);
  if (code == 802) return _wi(_wiScatteredD, _wiScatteredN, isDay: isDay);
  return _wi(_wiOvercastD, _wiOvercastN, isDay: isDay);
}
