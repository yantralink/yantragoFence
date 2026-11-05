package com.yantrago.api.service;

import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.dto.weather.WeatherDto;
import com.yantrago.api.exception.WeatherProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * SEC-15: weather proxy tests — access validation delegates to
 * LocationService, coordinates are rounded before transmission, the
 * service fails closed without an API key, provider calls are throttled
 * and cached per time bucket, and failures surface as 502-mapped
 * exceptions without leaking internals.
 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private LocationService locationService;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private WeatherService service;

    private static final UUID MACHINE_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String API_KEY = "test-key";

    private static final String PROVIDER_JSON = """
            {
              "main": {"temp": 24.3, "feels_like": 26.1, "humidity": 58},
              "weather": [{"id": 800, "main": "Clear", "icon": "01d"}],
              "wind": {"speed": 3.31, "deg": 315, "gust": 5.11},
              "rain": {"1h": 0.5},
              "dt": 1730390400
            }
            """;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        // 1ms provider interval + 10s max wait keeps tests fast.
        service = serviceWith(API_KEY);
    }

    private WeatherService serviceWith(String apiKey) {
        return new WeatherService(locationService, clientWith(apiKey),
                true, 1800, 100, 2);
    }

    private WeatherProviderClient clientWith(String apiKey) {
        return new WeatherProviderClient(
                restClientBuilder, "https://provider.test", apiKey,
                1, 10_000, 5_000, 10_000, 0, false);
    }

    private LocationDto location(double lat, double lon) {
        return new LocationDto(DEVICE_ID, MACHINE_ID, lat, lon,
                null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void getCurrentWeather_mapsProviderResponseAndConvertsWind() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.9716, 77.5946));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/data/2.5/weather")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(PROVIDER_JSON, MediaType.APPLICATION_JSON));

        WeatherDto weather = service.getCurrentWeather(MACHINE_ID);

        assertNotNull(weather);
        assertEquals(24.3, weather.temperatureCelsius());
        assertEquals(26.1, weather.apparentTemperatureCelsius());
        assertEquals(58, weather.humidityPercent());
        assertEquals(0.5, weather.precipitationMm());
        assertEquals(800, weather.conditionCode());
        assertEquals("Clear", weather.conditionText());
        assertEquals("01d", weather.conditionIcon());
        // m/s → km/h conversion
        assertEquals(3.31 * 3.6, weather.windSpeedKph(), 0.001);
        assertEquals(315, weather.windDirectionDegrees());
        assertEquals(5.11 * 3.6, weather.windGustsKph(), 0.001);
        assertEquals("2024-10-31T16:00:00Z", weather.observedAt());
        server.verify();
    }

    @Test
    void getCurrentWeather_missingApiKey_failsBeforeProviderCall() {
        service = serviceWith(" ");
        assertThrows(WeatherProviderException.class,
                () -> service.getCurrentWeather(MACHINE_ID));
        server.verify(); // zero provider calls
        verifyNoInteractions(locationService);
    }

    @Test
    void getCurrentWeather_noLocation_returnsNull() {
        when(locationService.getCurrentLocation(MACHINE_ID)).thenReturn(null);
        assertNull(service.getCurrentWeather(MACHINE_ID));
        server.verify(); // zero provider calls
    }

    @Test
    void getCurrentWeather_invalidCoordinates_returnsNull() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(95.0, 200.0));
        assertNull(service.getCurrentWeather(MACHINE_ID));
        server.verify();
    }

    @Test
    void getCurrentWeather_noMachineAccess_propagatesSecurityException() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenThrow(new SecurityException("no access"));
        assertThrows(SecurityException.class,
                () -> service.getCurrentWeather(MACHINE_ID));
    }

    @Test
    void getCurrentWeather_providerError_throwsWeatherProviderException() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.97, 77.59));
        server.expect(requestTo(org.hamcrest.Matchers.anything()))
                .andRespond(withServerError());
        assertThrows(WeatherProviderException.class,
                () -> service.getCurrentWeather(MACHINE_ID));
    }

    @Test
    void getCurrentWeather_secondCallSameBucket_usesCache() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.9716, 77.5946));
        server.expect(requestTo(org.hamcrest.Matchers.anything()))
                .andRespond(withSuccess(PROVIDER_JSON, MediaType.APPLICATION_JSON));

        service.getCurrentWeather(MACHINE_ID);
        WeatherDto second = service.getCurrentWeather(MACHINE_ID);

        assertNotNull(second);
        server.verify(); // exactly one provider call for two lookups
    }

    @Test
    void getCurrentWeather_roundsCoordinatesToTwoDecimals() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.9716, 77.5946));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("lat=12.97")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("lon=77.59")))
                .andRespond(withSuccess(PROVIDER_JSON, MediaType.APPLICATION_JSON));

        service.getCurrentWeather(MACHINE_ID);
        server.verify();
    }

    @Test
    void getCurrentWeather_disabled_throwsWeatherProviderException() {
        service = new WeatherService(
                locationService, clientWith(API_KEY),
                false, 1800, 100, 2);
        assertThrows(WeatherProviderException.class,
                () -> service.getCurrentWeather(MACHINE_ID));
        verifyNoInteractions(locationService);
    }
}
