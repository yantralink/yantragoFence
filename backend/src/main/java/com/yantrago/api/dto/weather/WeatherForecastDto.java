package com.yantrago.api.dto.weather;

import java.util.List;

/**
 * Normalized weather forecast for a machine's location, resolved
 * server-side from the provider's 3-hour forecast slots
 * (SEC-15 — the client never supplies coordinates).
 *
 * @param fetchedAt ISO-8601 UTC instant when the provider response was
 *                  fetched — used by clients for freshness decisions;
 *                  not a provider model-run timestamp
 * @param slots     ordered forecast samples (forecast endpoint returns
 *                  ~40 slots over ~5 days); empty when the provider
 *                  reports a valid but empty forecast
 */
public record WeatherForecastDto(String fetchedAt, List<ForecastSlotDto> slots) {
}
