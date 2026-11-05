package com.yantrago.api.service;

import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.dto.weather.WeatherForecastDto;
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

/** SEC-15: forecast proxy tests — mirrors WeatherServiceTest coverage. */
@ExtendWith(MockitoExtension.class)
class WeatherForecastServiceTest {

    @Mock
    private LocationService locationService;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private WeatherForecastService service;

    private static final UUID MACHINE_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String API_KEY = "test-key";

    private static final String FORECAST_JSON = """
            {"city":{"timezone":19800},"list":[
              {"dt":1730390400,"main":{"temp":24.3},
               "weather":[{"id":800,"main":"Clear","icon":"01d"}]},
              {"dt":1730401200,"main":{"temp":null},"weather":[]},
              {"dt":null,"main":{"temp":9},
               "weather":[{"id":500,"main":"Rain","icon":"10d"}]}
            ]}
            """;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        service = serviceWith(API_KEY, true, true, 10800);
    }

    private WeatherForecastService serviceWith(String apiKey, boolean enabled,
                                               boolean forecastEnabled, long ttlSeconds) {
        WeatherProviderClient client = new WeatherProviderClient(
                restClientBuilder, "https://provider.test", apiKey,
                1, 10_000, 5_000, 10_000, 0, false);
        return new WeatherForecastService(locationService, client,
                enabled, forecastEnabled, ttlSeconds, 100, 2);
    }

    private LocationDto location(double lat, double lon) {
        return new LocationDto(DEVICE_ID, MACHINE_ID, lat, lon,
                null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void getForecast_normalizesSlotsAndSkipsInvalidTimestamps() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.9716, 77.5946));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/data/2.5/forecast")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(FORECAST_JSON, MediaType.APPLICATION_JSON));

        WeatherForecastDto forecast = service.getForecast(MACHINE_ID);

        assertNotNull(forecast);
        assertNotNull(forecast.fetchedAt());
        assertEquals(2, forecast.slots().size()); // null-dt slot skipped
        var first = forecast.slots().get(0);
        assertEquals("2024-10-31T16:00:00Z", first.forecastAt());
        assertEquals(24.3, first.temperatureCelsius());
        assertEquals(800, first.conditionCode());
        assertEquals("01d", first.conditionIcon());
        var second = forecast.slots().get(1);
        assertNull(second.temperatureCelsius());
        assertNull(second.conditionCode());
        server.verify();
    }

    @Test
    void getForecast_emptyProviderList_returnsEmptySlots() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.97, 77.59));
        server.expect(requestTo(org.hamcrest.Matchers.anything()))
                .andRespond(withSuccess("{\"list\":[]}", MediaType.APPLICATION_JSON));

        WeatherForecastDto forecast = service.getForecast(MACHINE_ID);
        assertNotNull(forecast);
        assertTrue(forecast.slots().isEmpty());
    }

    @Test
    void getForecast_missingApiKey_failsBeforeProviderCall() {
        service = serviceWith(" ", true, true, 10800);
        assertThrows(WeatherProviderException.class,
                () -> service.getForecast(MACHINE_ID));
        server.verify();
        verifyNoInteractions(locationService);
    }

    @Test
    void getForecast_disabled_throwsBeforeLocationLookup() {
        service = serviceWith(API_KEY, false, true, 10800);
        assertThrows(WeatherProviderException.class,
                () -> service.getForecast(MACHINE_ID));
        verifyNoInteractions(locationService);
    }

    @Test
    void getForecast_forecastDisabled_throwsBeforeLocationLookup() {
        service = serviceWith(API_KEY, true, false, 10800);
        assertThrows(WeatherProviderException.class,
                () -> service.getForecast(MACHINE_ID));
        verifyNoInteractions(locationService);
    }

    @Test
    void getForecast_noLocation_returnsNull() {
        when(locationService.getCurrentLocation(MACHINE_ID)).thenReturn(null);
        assertNull(service.getForecast(MACHINE_ID));
        server.verify();
    }

    @Test
    void getForecast_invalidCoordinates_returnsNull() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(95.0, 200.0));
        assertNull(service.getForecast(MACHINE_ID));
        server.verify();
    }

    @Test
    void getForecast_noMachineAccess_propagatesSecurityException() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenThrow(new SecurityException("no access"));
        assertThrows(SecurityException.class,
                () -> service.getForecast(MACHINE_ID));
    }

    @Test
    void getForecast_providerError_throwsWeatherProviderException() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.97, 77.59));
        server.expect(requestTo(org.hamcrest.Matchers.anything()))
                .andRespond(withServerError());
        assertThrows(WeatherProviderException.class,
                () -> service.getForecast(MACHINE_ID));
    }

    @Test
    void getForecast_secondCallSameBucket_usesCache() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.9716, 77.5946));
        server.expect(requestTo(org.hamcrest.Matchers.anything()))
                .andRespond(withSuccess(FORECAST_JSON, MediaType.APPLICATION_JSON));

        service.getForecast(MACHINE_ID);
        WeatherForecastDto second = service.getForecast(MACHINE_ID);

        assertNotNull(second);
        server.verify(); // exactly one provider call for two lookups
    }

    @Test
    void getForecast_roundsCoordinatesToTwoDecimals() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(12.9716, 77.5946));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("lat=12.97")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("lon=77.59")))
                .andRespond(withSuccess(FORECAST_JSON, MediaType.APPLICATION_JSON));

        service.getForecast(MACHINE_ID);
        server.verify();
    }
}
