package com.yantrago.api.service;

import com.yantrago.api.dto.geocoding.AddressDto;
import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.exception.GeocodingProviderException;
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
 * SEC-15: geocoding proxy tests — access validation delegates to
 * LocationService, coordinates are rounded before transmission, provider
 * calls are throttled and cached, and failures surface as 502-mapped
 * exceptions without leaking internals.
 */
@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private LocationService locationService;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private GeocodingService service;

    private static final UUID MACHINE_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        // 1ms provider interval + 10s max wait keeps tests fast.
        service = new GeocodingService(
                locationService, restClientBuilder,
                "https://provider.test", "Test-Agent/1.0",
                true, 10, 1, 10_000, 4);
    }

    private LocationDto location(double lat, double lon) {
        return new LocationDto(DEVICE_ID, MACHINE_ID, lat, lon,
                null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void reverseGeocode_success_mapsProviderResponse() throws Exception {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(18.520430123, 73.856740987));

        server.expect(requestTo("https://provider.test/reverse?format=jsonv2&lat=18.5204&lon=73.8567&addressdetails=1"))
                .andExpect(header("Accept-Language", "en"))
                .andRespond(withSuccess("""
                        {"display_name":"Pune, Maharashtra, India",
                         "address":{"road":"Farm Road","neighbourhood":"Kothrud",
                                    "city":"Pune","state":"Maharashtra",
                                    "postcode":"411038","country":"India"}}
                        """, MediaType.APPLICATION_JSON));

        AddressDto dto = service.reverseGeocode(MACHINE_ID, "en");

        assertEquals("Pune, Maharashtra, India", dto.displayName());
        assertEquals("Farm Road", dto.road());
        assertEquals("Pune", dto.city());
        assertEquals("Maharashtra", dto.state());
        assertEquals("India", dto.country());
        server.verify();
    }

    @Test
    void reverseGeocode_cityFallsBackToTownThenVillage() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(20.0, 75.0));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reverse")))
                .andRespond(withSuccess("""
                        {"display_name":"x","address":{"village":"Shelgaon"}}
                        """, MediaType.APPLICATION_JSON));

        AddressDto dto = service.reverseGeocode(MACHINE_ID, "en");
        assertEquals("Shelgaon", dto.city());
        server.verify();
    }

    @Test
    void reverseGeocode_noLocation_returnsNullWithoutProviderCall() {
        when(locationService.getCurrentLocation(MACHINE_ID)).thenReturn(null);

        assertNull(service.reverseGeocode(MACHINE_ID, "en"));
        server.verify(); // zero expectations — nothing was requested
    }

    @Test
    void reverseGeocode_invalidCoordinates_returnsNullWithoutProviderCall() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(Double.NaN, 73.0));
        assertNull(service.reverseGeocode(MACHINE_ID, "en"));

        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(91.0, 73.0));
        assertNull(service.reverseGeocode(MACHINE_ID, "en"));

        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(10.0, 181.0));
        assertNull(service.reverseGeocode(MACHINE_ID, "en"));
    }

    @Test
    void reverseGeocode_cacheHit_skipsProvider() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(18.5, 73.8));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reverse")))
                .andRespond(withSuccess("""
                        {"display_name":"x","address":{"city":"Pune"}}
                        """, MediaType.APPLICATION_JSON));

        AddressDto first = service.reverseGeocode(MACHINE_ID, "en");
        AddressDto second = service.reverseGeocode(MACHINE_ID, "en");

        assertSame(first, second);
        server.verify(); // exactly one provider call for two lookups
    }

    @Test
    void reverseGeocode_differentLanguages_cachedSeparately() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(18.5, 73.8));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reverse")))
                .andExpect(header("Accept-Language", "en"))
                .andRespond(withSuccess("{\"display_name\":\"a\",\"address\":{}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reverse")))
                .andExpect(header("Accept-Language", "hi"))
                .andRespond(withSuccess("{\"display_name\":\"b\",\"address\":{}}",
                        MediaType.APPLICATION_JSON));

        service.reverseGeocode(MACHINE_ID, "en");
        service.reverseGeocode(MACHINE_ID, "hi");
        server.verify();
    }

    @Test
    void reverseGeocode_providerError_throwsGeocodingProviderException() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(18.5, 73.8));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reverse")))
                .andRespond(withServerError());

        assertThrows(GeocodingProviderException.class,
                () -> service.reverseGeocode(MACHINE_ID, "en"));
    }

    @Test
    void reverseGeocode_disabled_throwsWithoutLookup() {
        GeocodingService disabled = new GeocodingService(
                locationService, restClientBuilder,
                "https://provider.test", "UA", false, 10, 1, 10_000, 4);

        assertThrows(GeocodingProviderException.class,
                () -> disabled.reverseGeocode(MACHINE_ID, "en"));
        verifyNoInteractions(locationService);
    }

    @Test
    void reverseGeocode_accessDenied_propagatesSecurityException() {
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenThrow(new SecurityException("Access denied"));

        assertThrows(SecurityException.class,
                () -> service.reverseGeocode(MACHINE_ID, "en"));
    }

    @Test
    void reverseGeocode_precision_isConfigurable() throws Exception {
        GeocodingService coarse = new GeocodingService(
                locationService, restClientBuilder,
                "https://provider.test", "UA", true, 10, 1, 10_000, 2);
        when(locationService.getCurrentLocation(MACHINE_ID))
                .thenReturn(location(18.52043, 73.85674));

        server.expect(requestTo("https://provider.test/reverse?format=jsonv2&lat=18.52&lon=73.86&addressdetails=1"))
                .andRespond(withSuccess("{\"display_name\":\"x\",\"address\":{}}",
                        MediaType.APPLICATION_JSON));

        coarse.reverseGeocode(MACHINE_ID, "en");
        server.verify();
    }
}
