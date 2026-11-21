import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/services/weather_service.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_weather_provider.dart';
import 'package:yantrago/models/machine_location.dart';
import 'package:yantrago/models/user.dart';
import 'package:yantrago/models/weather.dart';
import 'package:yantrago/models/weather_forecast.dart';

const _user = User(
  id: 'u1',
  organizationId: 'o1',
  email: 'u1@example.invalid',
  fullName: 'Test User',
  role: 'CUSTOMER',
  active: true,
);

class _Consent extends MapConsentController {
  _Consent(this._decision);
  final MapConsent _decision;
  @override
  Future<MapConsent> build() async => _decision;
}

class _WeatherService extends WeatherService {
  _WeatherService() : super(Dio());
  int weatherCalls = 0;
  int forecastCalls = 0;

  @override
  Future<Weather?> getMachineWeather(String id, {String? locationKey}) async {
    weatherCalls++;
    return Weather(conditionCode: weatherCalls == 1 ? 721 : 800);
  }

  @override
  Future<WeatherForecast?> getMachineForecast(
    String id, {
    String? locationKey,
  }) async {
    forecastCalls++;
    return null;
  }
}

ProviderContainer _container(
  _WeatherService service, {
  MapConsent consent = MapConsent.granted,
}) {
  final container = ProviderContainer(
    overrides: [
      currentUserProvider.overrideWith((ref) => _user),
      mapConsentProvider.overrideWith(() => _Consent(consent)),
      weatherServiceProvider.overrideWithValue(service),
      machineLocationProvider.overrideWith(
        (ref, id) async =>
            const MachineLocation(latitude: 12.97, longitude: 77.59),
      ),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

/// Lets the consent -> location -> weather future chain resolve.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 5; i++) {
    await tester.pump(const Duration(milliseconds: 1));
  }
}

void main() {
  testWidgets('current weather re-fetches every refresh interval', (
    tester,
  ) async {
    final service = _WeatherService();
    final container = _container(service);
    container.listen(machineWeatherProvider('m1'), (_, __) {});
    await _settle(tester);
    expect(service.weatherCalls, 1);
    expect(
      container.read(machineWeatherProvider('m1')).valueOrNull?.conditionCode,
      721,
    );

    await tester.pump(weatherRefreshInterval - const Duration(seconds: 1));
    expect(service.weatherCalls, 1);
    await tester.pump(const Duration(seconds: 1));
    await _settle(tester);
    expect(service.weatherCalls, 2);
    expect(
      container.read(machineWeatherProvider('m1')).valueOrNull?.conditionCode,
      800,
    );

    await tester.pump(weatherRefreshInterval);
    await _settle(tester);
    expect(service.weatherCalls, 3);
    // The armed refresh timer must be cancelled before the test ends.
    container.dispose();
  });

  testWidgets('forecast re-fetches every forecast refresh interval', (
    tester,
  ) async {
    final service = _WeatherService();
    final container = _container(service);
    container.listen(machineWeatherForecastProvider('m1'), (_, __) {});
    await _settle(tester);
    expect(service.forecastCalls, 1);

    await tester.pump(weatherForecastRefreshInterval);
    await _settle(tester);
    expect(service.forecastCalls, 2);
    container.dispose();
  });

  testWidgets('no refresh is scheduled without map consent', (tester) async {
    final service = _WeatherService();
    final container = _container(service, consent: MapConsent.declined);
    container.listen(machineWeatherProvider('m1'), (_, __) {});
    await _settle(tester);
    await tester.pump(weatherRefreshInterval * 2);
    expect(service.weatherCalls, 0);
  });

  testWidgets('refresh stops once the provider is no longer listened to', (
    tester,
  ) async {
    final service = _WeatherService();
    final container = _container(service);
    final sub = container.listen(machineWeatherProvider('m1'), (_, __) {});
    await _settle(tester);
    sub.close();
    await tester.pump(weatherRefreshInterval * 2);
    expect(service.weatherCalls, 1);
  });
}
