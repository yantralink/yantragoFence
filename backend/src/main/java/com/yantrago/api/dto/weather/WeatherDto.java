package com.yantrago.api.dto.weather;

/**
 * Current weather conditions for a machine's location, resolved
 * server-side from the weather provider (SEC-15 — the client never
 * supplies coordinates).
 *
 * @param temperatureCelsius        current temperature
 * @param apparentTemperatureCelsius "feels like" temperature
 * @param humidityPercent           relative humidity 0–100
 * @param precipitationMm           rain+snow volume over the last hour,
 *                                  null when the provider reports none
 * @param conditionCode             provider condition id (OWM weather id,
 *                                  e.g. 800) — mobile localizes from this,
 *                                  never from conditionText
 * @param conditionText             provider's English condition summary
 *                                  (e.g. "Clear") — informational only
 * @param conditionIcon             provider icon code (e.g. "03d") — the
 *                                  trailing d/n encodes day/night
 * @param windSpeedKph              wind speed converted m/s → km/h
 * @param windDirectionDegrees      wind direction (meteorological degrees)
 * @param windGustsKph              gust speed converted m/s → km/h
 * @param observedAt                provider observation time, ISO-8601
 */
public record WeatherDto(
        Double temperatureCelsius,
        Double apparentTemperatureCelsius,
        Integer humidityPercent,
        Double precipitationMm,
        Integer conditionCode,
        String conditionText,
        String conditionIcon,
        Double windSpeedKph,
        Integer windDirectionDegrees,
        Double windGustsKph,
        String observedAt) {
}
