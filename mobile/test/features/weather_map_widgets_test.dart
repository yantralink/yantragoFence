import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/machines/widgets/environmental_strip.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/models/weather.dart';

const _clear = Weather(
  temperatureCelsius: 24.3,
  apparentTemperatureCelsius: 26.1,
  humidityPercent: 58,
  conditionCode: 800,
  conditionText: 'Clear',
  conditionIcon: '01d',
  windSpeedKph: 11.9,
  windDirectionDegrees: 315,
  windGustsKph: 18.4,
  observedAt: '2026-10-31T16:00:00Z',
);

Future<void> _pump(WidgetTester tester, Widget child) async {
  await tester.pumpWidget(MaterialApp(
    theme: AppTheme.lightTheme,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(body: child),
  ));
}

void main() {
  group('Weather.fromJson', () {
    test('maps the backend WeatherDto shape', () {
      final w = Weather.fromJson(const {
        'temperatureCelsius': 24.3,
        'apparentTemperatureCelsius': 26.1,
        'humidityPercent': 58,
        'precipitationMm': 0.5,
        'conditionCode': 800,
        'conditionText': 'Clear',
        'conditionIcon': '01d',
        'windSpeedKph': 11.9,
        'windDirectionDegrees': 315,
        'windGustsKph': 18.4,
        'observedAt': '2026-10-31T16:00:00Z',
      });
      expect(w.temperatureCelsius, 24.3);
      expect(w.precipitationMm, 0.5);
      expect(w.conditionCode, 800);
      expect(w.isDay, isTrue);
      expect(w.windCompass, 'NW');
      expect(w.observedTime?.year, 2026);
    });

    test('null precipitation stays null; night icon flips isDay', () {
      final w = Weather.fromJson(const {
        'conditionCode': 800,
        'conditionIcon': '01n',
      });
      expect(w.precipitationMm, isNull);
      expect(w.isDay, isFalse);
    });
  });

  group('weatherConditionLabel / weatherConditionIcon', () {
    test('covers every OWM id group', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(weatherConditionLabel(l10n, 211), 'Thunderstorm');
      expect(weatherConditionLabel(l10n, 300), 'Drizzle');
      expect(weatherConditionLabel(l10n, 502), 'Rain');
      expect(weatherConditionLabel(l10n, 601), 'Snow');
      expect(weatherConditionLabel(l10n, 741), 'Foggy');
      expect(weatherConditionLabel(l10n, 800), 'Clear');
      expect(weatherConditionLabel(l10n, 802), 'Cloudy');
      expect(weatherConditionLabel(l10n, null), 'Weather unavailable');
    });

    test('day/night icon variants', () {
      expect(weatherConditionIcon(800, isDay: true),
          const IconData(0xf00d, fontFamily: 'WeatherIcons'));
      expect(weatherConditionIcon(800, isDay: false),
          const IconData(0xf02e, fontFamily: 'WeatherIcons'));
      expect(weatherConditionIcon(null), Icons.cloud_off_outlined);
    });
  });

  group('EnvironmentalStrip', () {
    testWidgets('renders real weather fields', (tester) async {
      await _pump(tester,
          const EnvironmentalStrip(weather: _clear, isLoading: false));
      expect(find.text('Clear'), findsOneWidget);
      expect(find.text('24°C'), findsOneWidget);
      expect(find.textContaining('11.9 km/h'), findsOneWidget);
    });

    testWidgets('loading state shows progress, never blank',
        (tester) async {
      await _pump(tester,
          const EnvironmentalStrip(weather: null, isLoading: true));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('null weather shows honest unavailable label',
        (tester) async {
      await _pump(tester,
          const EnvironmentalStrip(weather: null, isLoading: false));
      expect(find.text('Weather unavailable'), findsOneWidget);
    });
  });
}
