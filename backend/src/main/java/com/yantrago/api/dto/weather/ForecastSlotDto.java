package com.yantrago.api.dto.weather;

/**
 * One normalized 3-hour forecast sample. All fields except
 * {@code forecastAt} are nullable — individually invalid provider
 * samples are skipped or carried with nulls, never fabricated.
 *
 * @param forecastAt       ISO-8601 UTC instant of the forecast sample
 * @param temperatureCelsius sampled temperature, nullable
 * @param conditionCode    provider condition id (OWM weather id)
 * @param conditionIcon    provider icon code (e.g. "03d") — trailing
 *                         d/n encodes day/night; may be null
 */
public record ForecastSlotDto(
        String forecastAt,
        Double temperatureCelsius,
        Integer conditionCode,
        String conditionIcon) {
}
