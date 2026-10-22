package com.yantrago.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yantrago.api.dto.geocoding.AddressDto;
import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.exception.GeocodingProviderException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SEC-15: server-side reverse-geocoding proxy.
 *
 * The mobile app never sends machine coordinates to a third party
 * directly. This service:
 *  - validates tenant + customer machine access via LocationService
 *    (which delegates to MachineAccessPolicy),
 *  - resolves the machine's current coordinates server-side,
 *  - bounds outbound provider calls to a global rate (default ~1 req/sec,
 *    matching the Nominatim usage policy),
 *  - rounds coordinates before transmission (default 4 decimals, ~11m)
 *    so the provider never receives full-precision farm coordinates,
 *  - caches resolved addresses in a bounded LRU map,
 *  - never logs coordinates or provider payloads.
 *
 * The provider is configurable via geocoding.provider.base-url so a
 * production-approved provider (self-hosted Nominatim, or a commercial
 * provider under a data-processing agreement) can be substituted without
 * code changes.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private final LocationService locationService;
    private final RestClient restClient;
    private final boolean enabled;
    private final int coordinatePrecision;
    private final Duration maxWait;
    private final Bucket providerBucket;
    private final Map<String, AddressDto> cache;

    public GeocodingService(
            LocationService locationService,
            RestClient.Builder restClientBuilder,
            @Value("${geocoding.provider.base-url:https://nominatim.openstreetmap.org}") String providerBaseUrl,
            @Value("${geocoding.user-agent:YantraGO-Backend/1.0}") String userAgent,
            @Value("${geocoding.enabled:true}") boolean enabled,
            @Value("${geocoding.cache-max-entries:500}") int cacheMaxEntries,
            @Value("${geocoding.min-request-interval-ms:1100}") long minRequestIntervalMs,
            @Value("${geocoding.max-wait-ms:15000}") long maxWaitMs,
            @Value("${geocoding.coordinate-precision:4}") int coordinatePrecision) {
        this.locationService = locationService;
        this.enabled = enabled;
        this.coordinatePrecision = Math.max(0, Math.min(6, coordinatePrecision));
        this.maxWait = Duration.ofMillis(maxWaitMs);
        this.restClient = restClientBuilder
                .baseUrl(providerBaseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.providerBucket = Bucket.builder()
                .addLimit(Bandwidth.simple(1, Duration.ofMillis(minRequestIntervalMs)))
                .build();
        this.cache = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, AddressDto> eldest) {
                        return size() > cacheMaxEntries;
                    }
                });
    }

    /**
     * Resolves the current location of {@code machineId} to an address.
     *
     * @return the resolved address, or null when the machine has no usable
     *         location (controller maps this to 404)
     * @throws SecurityException        when the caller lacks machine access
     * @throws IllegalArgumentException when the machine does not exist
     * @throws GeocodingProviderException when the provider call fails or
     *         the throttle cannot admit the request in time
     */
    public AddressDto reverseGeocode(UUID machineId, String languageCode) {
        if (!enabled) {
            throw new GeocodingProviderException("Geocoding is disabled");
        }

        // Validates tenant isolation + customer assignment (SEC-02) and
        // resolves the coordinates server-side — the client never supplies
        // coordinates, so this endpoint cannot be abused as an open
        // geocoding oracle for arbitrary locations.
        LocationDto location = locationService.getCurrentLocation(machineId);
        Double lat = location == null ? null : location.getLatitude();
        Double lon = location == null ? null : location.getLongitude();
        if (!isValidLatitude(lat) || !isValidLongitude(lon)) {
            return null;
        }

        String roundedLat = round(lat);
        String roundedLon = round(lon);
        String cacheKey = languageCode + "|" + roundedLat + "," + roundedLon;

        AddressDto cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        acquireProviderSlot();
        AddressDto resolved = callProvider(roundedLat, roundedLon, languageCode);
        if (resolved != null) {
            cache.put(cacheKey, resolved);
        }
        return resolved;
    }

    private void acquireProviderSlot() {
        try {
            boolean admitted = providerBucket.asBlocking().tryConsume(1, maxWait);
            if (!admitted) {
                log.warn("Geocoding provider throttle exhausted; request could not be admitted");
                throw new GeocodingProviderException("Geocoding provider is busy");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodingProviderException("Interrupted while waiting for geocoding provider", e);
        }
    }

    private AddressDto callProvider(String lat, String lon, String languageCode) {
        try {
            ProviderResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("addressdetails", 1)
                            .build())
                    .header("Accept-Language", languageCode)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new GeocodingProviderException(
                                "Geocoding provider returned " + res.getStatusCode().value());
                    })
                    .body(ProviderResponse.class);
            return response == null ? null : response.toAddressDto();
        } catch (GeocodingProviderException e) {
            throw e;
        } catch (RestClientException e) {
            // Deliberately no coordinates or provider payload in the log.
            log.warn("Geocoding provider request failed: {}", e.getMessage());
            throw new GeocodingProviderException("Geocoding provider request failed", e);
        }
    }

    private boolean isValidLatitude(Double v) {
        return v != null && !v.isNaN() && !v.isInfinite() && Math.abs(v) <= 90;
    }

    private boolean isValidLongitude(Double v) {
        return v != null && !v.isNaN() && !v.isInfinite() && Math.abs(v) <= 180;
    }

    private String round(double value) {
        return String.format(Locale.ROOT, "%." + coordinatePrecision + "f", value);
    }

    /** Nominatim jsonv2 response shape; unknown fields ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProviderResponse(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("address") ProviderAddress address) {

        AddressDto toAddressDto() {
            ProviderAddress a = address;
            String city = a == null ? null
                    : firstNonNull(a.city(), a.town(), a.village());
            return new AddressDto(
                    displayName,
                    a == null ? null : a.road(),
                    a == null ? null : a.neighbourhood(),
                    a == null ? null : a.suburb(),
                    city,
                    a == null ? null : a.county(),
                    a == null ? null : a.state(),
                    a == null ? null : a.postcode(),
                    a == null ? null : a.country());
        }

        private static String firstNonNull(String... values) {
            for (String v : values) {
                if (v != null && !v.isBlank()) return v;
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProviderAddress(
            String road,
            String neighbourhood,
            String suburb,
            String city,
            String town,
            String village,
            String county,
            String state,
            String postcode,
            String country) {}
}
