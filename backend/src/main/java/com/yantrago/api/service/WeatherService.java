package com.yantrago.api.service;

import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.dto.weather.WeatherDto;
import com.yantrago.api.exception.WeatherProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SEC-15: server-side weather proxy for current conditions.
 *
 * The mobile app never sends machine coordinates to a third party
 * directly. This service:
 *  - validates tenant + customer machine access via LocationService
 *    (which delegates to MachineAccessPolicy),
 *  - resolves the machine's current coordinates server-side,
 *  - rounds coordinates before transmission (default 2 decimals,
 *    ~1.1km — plenty for weather, maximizes cache sharing),
 *  - caches results in a bounded LRU keyed by rounded location +
 *    time bucket (default 30 min TTL),
 *  - delegates provider calls to WeatherProviderClient, which owns the
 *    shared request budget, failure backoff, and in-flight coalescing,
 *  - never logs coordinates or provider payloads.
 */
@Service
public class WeatherService {


    private final LocationService locationService;
    private final WeatherProviderClient providerClient;
    private final boolean enabled;
    private final long cacheTtlSeconds;
    private final int coordinatePrecision;
    private final Map<String, WeatherDto> cache;

    public WeatherService(
            LocationService locationService,
            WeatherProviderClient providerClient,
            @Value("${weather.enabled:true}") boolean enabled,
            @Value("${weather.cache-ttl-seconds:1800}") long cacheTtlSeconds,
            @Value("${weather.cache-max-entries:1000}") int cacheMaxEntries,
            @Value("${weather.coordinate-precision:2}") int coordinatePrecision) {
        this.locationService = locationService;
        this.providerClient = providerClient;
        this.enabled = enabled;
        this.cacheTtlSeconds = Math.max(60, cacheTtlSeconds);
        this.coordinatePrecision = Math.max(0, Math.min(6, coordinatePrecision));
        this.cache = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, WeatherDto> eldest) {
                        return size() > cacheMaxEntries;
                    }
                });
    }

    /**
     * Resolves current weather for the machine's live location.
     *
     * @return the weather, or null when the machine has no usable
     *         location (controller maps this to 404)
     * @throws SecurityException          when the caller lacks machine access
     * @throws WeatherProviderException   when disabled, unconfigured, the
     *         provider call fails, or the provider is cooling down
     */
    public WeatherDto getCurrentWeather(UUID machineId) {
        if (!enabled) {
            throw new WeatherProviderException("Weather is disabled");
        }
        // Fail closed — never call the provider without credentials.
        if (!providerClient.isConfigured()) {
            throw new WeatherProviderException("Weather provider is not configured");
        }

        // Validates tenant isolation + customer assignment (SEC-02) and
        // resolves the coordinates server-side — the client never supplies
        // coordinates, so this endpoint cannot be abused as an open
        // weather oracle for arbitrary locations.
        LocationDto location = locationService.getCurrentLocation(machineId);
        Double lat = location == null ? null : location.getLatitude();
        Double lon = location == null ? null : location.getLongitude();
        if (!isValidLatitude(lat) || !isValidLongitude(lon)) {
            return null;
        }

        String roundedLat = round(lat);
        String roundedLon = round(lon);
        long timeBucket = Instant.now().getEpochSecond() / cacheTtlSeconds;
        String cacheKey = roundedLat + "|" + roundedLon + "|" + timeBucket;

        WeatherDto cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        WeatherDto resolved = providerClient.fetchCurrentWeather(roundedLat, roundedLon);
        if (resolved != null) {
            cache.put(cacheKey, resolved);
        }
        return resolved;
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
}
