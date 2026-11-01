package com.yantrago.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yantrago.api.dto.location.LocationDto;
import com.yantrago.api.dto.weather.WeatherDto;
import com.yantrago.api.exception.WeatherProviderException;
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
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SEC-15: server-side weather proxy, mirroring GeocodingService.
 *
 * The mobile app never sends machine coordinates to a third party
 * directly. This service:
 *  - validates tenant + customer machine access via LocationService
 *    (which delegates to MachineAccessPolicy),
 *  - resolves the machine's current coordinates server-side,
 *  - bounds outbound provider calls to a global rate,
 *  - rounds coordinates before transmission (default 2 decimals,
 *    ~1.1km — plenty for weather, maximizes cache sharing),
 *  - caches results in a bounded LRU keyed by rounded location +
 *    time bucket (default 30 min TTL),
 *  - fails closed when the provider key is absent,
 *  - never logs coordinates, provider payloads, or the API key.
 *
 * The provider is configurable via weather.provider.base-url /
 * weather.provider.api-key so a production-approved provider under a
 * data-processing agreement can be substituted without code changes.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final double MS_TO_KMH = 3.6;

    private final LocationService locationService;
    private final RestClient restClient;
    private final String apiKey;
    private final boolean enabled;
    private final long cacheTtlSeconds;
    private final int coordinatePrecision;
    private final Duration maxWait;
    private final Bucket providerBucket;
    private final Map<String, WeatherDto> cache;

    public WeatherService(
            LocationService locationService,
            RestClient.Builder restClientBuilder,
            @Value("${weather.provider.base-url:https://api.openweathermap.org}") String providerBaseUrl,
            @Value("${weather.provider.api-key:}") String apiKey,
            @Value("${weather.enabled:true}") boolean enabled,
            @Value("${weather.cache-ttl-seconds:1800}") long cacheTtlSeconds,
            @Value("${weather.cache-max-entries:1000}") int cacheMaxEntries,
            @Value("${weather.min-request-interval-ms:500}") long minRequestIntervalMs,
            @Value("${weather.max-wait-ms:15000}") long maxWaitMs,
            @Value("${weather.coordinate-precision:2}") int coordinatePrecision) {
        this.locationService = locationService;
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.cacheTtlSeconds = Math.max(60, cacheTtlSeconds);
        this.coordinatePrecision = Math.max(0, Math.min(6, coordinatePrecision));
        this.maxWait = Duration.ofMillis(maxWaitMs);
        this.restClient = restClientBuilder
                .baseUrl(providerBaseUrl)
                .defaultHeader("User-Agent", "YantraGO-Backend/1.0")
                .build();
        this.providerBucket = Bucket.builder()
                .addLimit(Bandwidth.simple(1, Duration.ofMillis(minRequestIntervalMs)))
                .build();
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
     * @throws IllegalArgumentException   when the machine does not exist
     * @throws WeatherProviderException   when disabled, unconfigured, the
     *         provider call fails, or the throttle cannot admit the
     *         request in time
     */
    public WeatherDto getCurrentWeather(UUID machineId) {
        if (!enabled) {
            throw new WeatherProviderException("Weather is disabled");
        }
        // Fail closed — never call the provider without credentials and
        // never emit the key to logs.
        if (apiKey == null || apiKey.isBlank()) {
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

        acquireProviderSlot();
        WeatherDto resolved = callProvider(roundedLat, roundedLon);
        if (resolved != null) {
            cache.put(cacheKey, resolved);
        }
        return resolved;
    }

    private void acquireProviderSlot() {
        try {
            boolean admitted = providerBucket.asBlocking().tryConsume(1, maxWait);
            if (!admitted) {
                log.warn("Weather provider throttle exhausted; request could not be admitted");
                throw new WeatherProviderException("Weather provider is busy");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherProviderException("Interrupted while waiting for weather provider", e);
        }
    }

    private WeatherDto callProvider(String lat, String lon) {
        try {
            ProviderResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/data/2.5/weather")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("appid", apiKey)
                            .queryParam("units", "metric")
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new WeatherProviderException(
                                "Weather provider returned " + res.getStatusCode().value());
                    })
                    .body(ProviderResponse.class);
            return response == null ? null : response.toWeatherDto();
        } catch (WeatherProviderException e) {
            throw e;
        } catch (RestClientException e) {
            // Deliberately no coordinates, URI (contains key), or payload.
            log.warn("Weather provider request failed");
            throw new WeatherProviderException("Weather provider request failed", e);
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

    /** OpenWeatherMap /data/2.5/weather shape; unknown fields ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProviderResponse(
            @JsonProperty("main") MainBlock main,
            @JsonProperty("weather") List<WeatherBlock> weather,
            @JsonProperty("wind") WindBlock wind,
            @JsonProperty("rain") PrecipBlock rain,
            @JsonProperty("snow") PrecipBlock snow,
            @JsonProperty("dt") Long dt) {

        WeatherDto toWeatherDto() {
            WeatherBlock w = (weather == null || weather.isEmpty()) ? null : weather.get(0);
            return new WeatherDto(
                    main == null ? null : main.temp(),
                    main == null ? null : main.feelsLike(),
                    main == null ? null : main.humidity(),
                    precipitationMm(rain, snow),
                    w == null ? null : w.id(),
                    w == null ? null : w.main(),
                    w == null ? null : w.icon(),
                    wind == null || wind.speed() == null ? null : wind.speed() * MS_TO_KMH,
                    wind == null ? null : wind.deg(),
                    wind == null || wind.gust() == null ? null : wind.gust() * MS_TO_KMH,
                    dt == null ? null : Instant.ofEpochSecond(dt).toString());
        }

        private static Double precipitationMm(PrecipBlock rain, PrecipBlock snow) {
            Double rainMm = rain == null ? null : rain.lastHourMm();
            Double snowMm = snow == null ? null : snow.lastHourMm();
            if (rainMm == null && snowMm == null) return null;
            return (rainMm == null ? 0 : rainMm) + (snowMm == null ? 0 : snowMm);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MainBlock(
            @JsonProperty("temp") Double temp,
            @JsonProperty("feels_like") Double feelsLike,
            @JsonProperty("humidity") Integer humidity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WeatherBlock(
            @JsonProperty("id") Integer id,
            @JsonProperty("main") String main,
            @JsonProperty("icon") String icon) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WindBlock(
            @JsonProperty("speed") Double speed,
            @JsonProperty("deg") Integer deg,
            @JsonProperty("gust") Double gust) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PrecipBlock(@JsonProperty("1h") Double lastHourMm) {}
}
