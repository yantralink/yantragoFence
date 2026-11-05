package com.yantrago.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yantrago.api.dto.weather.ForecastSlotDto;
import com.yantrago.api.dto.weather.WeatherDto;
import com.yantrago.api.exception.WeatherProviderException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shared OpenWeatherMap provider client — the single outbound path
 * for both current weather and forecast calls (SEC-15).
 *
 * Owns the credentials, the one global request budget (Bucket4j —
 * current and forecast share it so the account's calls/minute limit
 * is never exceeded), a short failure backoff so a struggling provider
 * is not hammered, bounded connect/read timeouts, and in-flight
 * coalescing so concurrent lookups for the same rounded coordinates
 * share one provider call.
 *
 * Never logs coordinates, the API key, request URIs, or payloads.
 */
@Component
public class WeatherProviderClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherProviderClient.class);

    private static final double MS_TO_KMH = 3.6;
    private static final int FORECAST_SLOTS = 40; // 5 days × 8 slots

    private final RestClient restClient;
    private final String apiKey;
    private final Duration maxWait;
    private final long backoffMillis;
    private final Bucket providerBucket;
    private final Map<String, CompletableFuture<WeatherDto>> currentInFlight =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<ForecastSlotDto>>> forecastInFlight =
            new ConcurrentHashMap<>();
    private volatile long backoffUntilEpochMilli;

    public WeatherProviderClient(
            RestClient.Builder restClientBuilder,
            @Value("${weather.provider.base-url:https://api.openweathermap.org}") String providerBaseUrl,
            @Value("${weather.provider.api-key:}") String apiKey,
            @Value("${weather.min-request-interval-ms:1100}") long minRequestIntervalMs,
            @Value("${weather.max-wait-ms:15000}") long maxWaitMs,
            @Value("${weather.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${weather.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${weather.failure-backoff-seconds:30}") long backoffSeconds) {
        this(restClientBuilder, providerBaseUrl, apiKey, minRequestIntervalMs,
                maxWaitMs, connectTimeoutMs, readTimeoutMs, backoffSeconds, true);
    }

    /**
     * Test-visible overload — {@code applyRequestFactory=false} skips the
     * timeout-configured request factory so MockRestServiceServer can
     * intercept calls (a custom factory would bypass the mock).
     */
    WeatherProviderClient(
            RestClient.Builder restClientBuilder,
            String providerBaseUrl, String apiKey,
            long minRequestIntervalMs, long maxWaitMs,
            long connectTimeoutMs, long readTimeoutMs,
            long backoffSeconds, boolean applyRequestFactory) {
        this.apiKey = apiKey;
        this.maxWait = Duration.ofMillis(maxWaitMs);
        this.backoffMillis = backoffSeconds * 1000;
        RestClient.Builder builder = restClientBuilder
                .baseUrl(providerBaseUrl)
                .defaultHeader("User-Agent", "YantraGO-Backend/1.0");
        if (applyRequestFactory) {
            SimpleClientHttpRequestFactory requestFactory =
                    new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout((int) connectTimeoutMs);
            requestFactory.setReadTimeout((int) readTimeoutMs);
            builder = builder.requestFactory(requestFactory);
        }
        this.restClient = builder.build();
        this.providerBucket = Bucket.builder()
                .addLimit(Bandwidth.simple(1, Duration.ofMillis(minRequestIntervalMs)))
                .build();
    }

    /** True when credentials are present — callers fail closed on false. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Current conditions for rounded coordinates. Concurrent calls for
     * the same key share one provider request.
     */
    public WeatherDto fetchCurrentWeather(String lat, String lon) {
        return coalesce(currentInFlight, "current|" + lat + "|" + lon,
                () -> doFetchCurrent(lat, lon));
    }

    /**
     * Normalized 3-hour forecast slots for rounded coordinates.
     * Concurrent calls for the same key share one provider request.
     */
    public List<ForecastSlotDto> fetchForecastSlots(String lat, String lon) {
        return coalesce(forecastInFlight, "forecast|" + lat + "|" + lon,
                () -> doFetchForecast(lat, lon));
    }

    private <T> T coalesce(Map<String, CompletableFuture<T>> inFlight,
                           String key, Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture<T> existing = inFlight.putIfAbsent(key, future);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                throw unwrap(e);
            }
        }
        try {
            future.complete(work.get());
            return future.join();
        } catch (Throwable t) {
            future.completeExceptionally(t);
            throw unwrap(t);
        } finally {
            inFlight.remove(key);
        }
    }

    private WeatherProviderException unwrap(Throwable t) {
        if (t instanceof CompletionException ce && ce.getCause() != null) {
            t = ce.getCause();
        }
        return t instanceof WeatherProviderException wpe
                ? wpe
                : new WeatherProviderException("Weather provider request failed", t);
    }

    private WeatherDto doFetchCurrent(String lat, String lon) {
        ProviderResponse response = exchange(ProviderResponse.class,
                uriBuilder -> uriBuilder
                        .path("/data/2.5/weather")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .build());
        return response == null ? null : response.toWeatherDto();
    }

    private List<ForecastSlotDto> doFetchForecast(String lat, String lon) {
        ForecastResponse response = exchange(ForecastResponse.class,
                uriBuilder -> uriBuilder
                        .path("/data/2.5/forecast")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .queryParam("cnt", FORECAST_SLOTS)
                        .build());
        List<ForecastSlotDto> slots = new ArrayList<>();
        if (response != null && response.list() != null) {
            for (ForecastSlot slot : response.list()) {
                if (slot.dt() == null) continue; // skip invalid samples
                slots.add(new ForecastSlotDto(
                        Instant.ofEpochSecond(slot.dt()).toString(),
                        slot.main() == null ? null : slot.main().temp(),
                        slot.condition() == null ? null : slot.condition().id(),
                        slot.condition() == null ? null : slot.condition().icon()));
            }
        }
        return slots;
    }

    private <T> T exchange(Class<T> type,
                           java.util.function.Function<
                                   org.springframework.web.util.UriBuilder, java.net.URI> uriFn) {
        checkBackoff();
        acquireProviderSlot();
        try {
            return restClient.get()
                    .uri(uriFn)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new WeatherProviderException(
                                "Weather provider returned " + res.getStatusCode().value());
                    })
                    .body(type);
        } catch (WeatherProviderException e) {
            enterBackoff();
            throw e;
        } catch (RestClientException e) {
            enterBackoff();
            // Deliberately no coordinates, URI (contains key), or payload.
            log.warn("Weather provider request failed");
            throw new WeatherProviderException("Weather provider request failed", e);
        }
    }

    private void checkBackoff() {
        if (backoffMillis > 0 && System.currentTimeMillis() < backoffUntilEpochMilli) {
            throw new WeatherProviderException("Weather provider is cooling down");
        }
    }

    private void enterBackoff() {
        if (backoffMillis > 0) {
            backoffUntilEpochMilli = System.currentTimeMillis() + backoffMillis;
        }
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

    /** OpenWeatherMap /data/2.5/forecast shape; unknown fields ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ForecastResponse(@JsonProperty("list") List<ForecastSlot> list) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ForecastSlot(
            @JsonProperty("dt") Long dt,
            @JsonProperty("main") ForecastMain main,
            @JsonProperty("weather") List<ForecastWeather> weather) {

        ForecastWeather condition() {
            return (weather == null || weather.isEmpty()) ? null : weather.get(0);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ForecastMain(@JsonProperty("temp") Double temp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ForecastWeather(
            @JsonProperty("id") Integer id,
            @JsonProperty("icon") String icon) {}

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
