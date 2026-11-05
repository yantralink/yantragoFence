package com.yantrago.api.service;

import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.dto.weather.ForecastSlotDto;
import com.yantrago.api.dto.weather.WeatherForecastDto;
import com.yantrago.api.exception.WeatherProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SEC-15: server-side weather forecast proxy for the machine's live
 * location. Mirrors WeatherService but serves normalized 3-hour
 * forecast slots with a longer cache TTL — forecast model output
 * changes slowly, and the provider's free forecast endpoint covers
 * ~5 days.
 *
 * Access validation, coordinate resolution/rounding, and provider
 * calls (shared budget, backoff, coalescing) are identical to current
 * weather. Never logs coordinates or provider payloads.
 */
@Service
public class WeatherForecastService {


    private final LocationService locationService;
    private final WeatherProviderClient providerClient;
    private final boolean enabled;
    private final boolean forecastEnabled;
    private final long cacheTtlSeconds;
    private final int coordinatePrecision;
    private final Map<String, WeatherForecastDto> cache;

    public WeatherForecastService(
            LocationService locationService,
            WeatherProviderClient providerClient,
            @Value("${weather.enabled:true}") boolean enabled,
            @Value("${weather.forecast-enabled:true}") boolean forecastEnabled,
            @Value("${weather.forecast-cache-ttl-seconds:10800}") long cacheTtlSeconds,
            @Value("${weather.cache-max-entries:1000}") int cacheMaxEntries,
            @Value("${weather.coordinate-precision:2}") int coordinatePrecision) {
        this.locationService = locationService;
        this.providerClient = providerClient;
        this.enabled = enabled;
        this.forecastEnabled = forecastEnabled;
        this.cacheTtlSeconds = Math.max(60, cacheTtlSeconds);
        this.coordinatePrecision = Math.max(0, Math.min(6, coordinatePrecision));
        this.cache = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, WeatherForecastDto> eldest) {
                        return size() > cacheMaxEntries;
                    }
                });
    }

    /**
     * Resolves the forecast for the machine's live location.
     *
     * @return forecast DTO (possibly with empty slots for a valid but
     *         empty provider forecast), or null when the machine has
     *         no usable location (controller maps to 404)
     * @throws SecurityException          when the caller lacks machine access
     * @throws WeatherProviderException   when disabled, unconfigured, the
     *         provider call fails, or the provider is cooling down
     */
    public WeatherForecastDto getForecast(UUID machineId) {
        if (!enabled || !forecastEnabled) {
            throw new WeatherProviderException("Weather forecast is disabled");
        }
        if (!providerClient.isConfigured()) {
            throw new WeatherProviderException("Weather provider is not configured");
        }

        LocationDto location = locationService.getCurrentLocation(machineId);
        Double lat = location == null ? null : location.getLatitude();
        Double lon = location == null ? null : location.getLongitude();
        if (!isValidLatitude(lat) || !isValidLongitude(lon)) {
            return null;
        }

        String roundedLat = round(lat);
        String roundedLon = round(lon);
        long timeBucket = Instant.now().getEpochSecond() / cacheTtlSeconds;
        String cacheKey = "forecast|" + roundedLat + "|" + roundedLon + "|" + timeBucket;

        WeatherForecastDto cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ForecastSlotDto> slots = providerClient.fetchForecastSlots(roundedLat, roundedLon);
        WeatherForecastDto resolved = new WeatherForecastDto(
                Instant.now().toString(),
                slots == null ? List.of() : slots);
        cache.put(cacheKey, resolved);
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
