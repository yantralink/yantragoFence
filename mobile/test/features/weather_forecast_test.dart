// Behavior tests for the expandable 5-day forecast card on the map
// screen (docs/EXPANDABLE_5_DAY_WEATHER_FORECAST_PLAN.md).

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/machines/widgets/weather_forecast_card.dart';
import 'package:yantrago/models/daily_forecast.dart';

void main() {
  List<DailyForecast> fixtures() {
    DailyForecast d(int offset, int code, double lo, double hi,
            {bool partial = false}) =>
        DailyForecast(
          date: DateTime(2026, 11, 5 + offset),
          conditionCode: code,
          conditionIcon: '01d',
          tempMinCelsius: lo,
          tempMaxCelsius: hi,
          isPartial: partial,
        );
    return [
      d(0, 800, 19, 29),
      d(1, 801, 20, 30),
      d(2, 500, 21, 27, partial: true),
      d(3, 202, 20, 26),
      d(4, 803, 19, 28),
    ];
  }

  Future<void> pumpCard(WidgetTester tester) async {
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: Scaffold(
        body: SingleChildScrollView(
          child: WeatherForecastCard(
            status: WeatherForecastStatus.ready,
            days: fixtures(),
            today: DateTime(2026, 11, 5),
          ),
        ),
      ),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('collapsed by default shows header only', (tester) async {
    await pumpCard(tester);
    expect(find.text('5-Day Forecast'), findsOneWidget);
    expect(find.text('Today'), findsNothing);
    expect(find.text('29° / 19°'), findsNothing);
  });

  testWidgets('tap expands rows, tap again collapses', (tester) async {
    await pumpCard(tester);
    await tester.tap(find.text('5-Day Forecast'));
    await tester.pumpAndSettle();
    expect(find.text('Today'), findsOneWidget);
    expect(find.text('29° / 19°'), findsOneWidget);
    expect(find.text('Partial forecast'), findsOneWidget);
    // Finer forecast labels: 801 → Partly Cloudy, 500 → Light Rain.
    expect(find.text('Partly Cloudy'), findsOneWidget);
    expect(find.text('Light Rain'), findsOneWidget);

    await tester.tap(find.text('5-Day Forecast'));
    await tester.pumpAndSettle();
    expect(find.text('Today'), findsNothing);
  });

  testWidgets('unavailable state never fabricates rows', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(
        body: WeatherForecastCard(
          status: WeatherForecastStatus.unavailable,
        ),
      ),
    ));
    await tester.pumpAndSettle();
    expect(find.text('Weather forecast unavailable'), findsOneWidget);
    await tester.tap(find.text('5-Day Forecast'));
    await tester.pumpAndSettle();
    // Unavailable is non-expandable — still no rows.
    expect(find.text('Today'), findsNothing);
  });
}
