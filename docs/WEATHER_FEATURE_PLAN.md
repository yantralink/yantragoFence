Devin Implementation Prompts

YantraGO App — Machine Weather Conditions (GPS-derived)

Purpose: Show current weather conditions (temperature, humidity, wind, precipitation, condition) for each machine's GPS location in the mobile app. GPS devices only report coordinates — weather data is resolved server-side from a third-party weather provider, following the existing SEC-15 geocoding-proxy pattern.

Architecture summary:

```
GPS device → device_locations (lat/lon) → backend WeatherService → weather provider API → WeatherDto → mobile WeatherCard
```

- Backend resolves coordinates server-side via `LocationService.getCurrentLocation(machineId)` — this already enforces tenant isolation + machine access (SEC-02, MachineAccessPolicy). The client sends only `machineId`, never coordinates.
- Provider calls are rate-limited (Bucket4j), coordinates rounded, results cached in a bounded LRU keyed by rounded location + time bucket — mirroring `GeocodingService` exactly.
- Default provider: **OpenWeatherMap** (`https://api.openweathermap.org`) — free tier allows commercial use (60 calls/min, 1M calls/month; far above our cached-call volume). Requires an API key via `weather.provider.api-key` (env `WEATHER_API_KEY`, never in source). Base URL configurable via `weather.provider.base-url` so another provider under a data-processing agreement can be substituted.
- Mobile mirrors `ReverseGeocodingService` / `machineAddressProvider` / `LocationCard`: backend-proxied request, consent-gated, `.when()` for all AsyncValue states, localized labels.

How to use this document

Give the prompts to Devin in order. Prompt 1 is analysis-only; Prompt 2 is the backend phase; Prompt 3 is the mobile phase; Prompt 4 is final validation. STOP after each phase for user verification — do not proceed to the next phase until the user confirms (AGENTS.md rule 20).

---

Prompt 1 — Analyze the Existing Patterns (analysis only)

You are working on my existing YantraGO codebase (Spring Boot 3 backend + Flutter mobile + TCP device-gateway, connected via RabbitMQ).

I want to add a "current weather" feature: for a machine's GPS location, show temperature, humidity, wind speed/direction, precipitation, and a weather condition summary in the app.

Before modifying anything, analyze and report:

1. `backend/.../service/GeocodingService.java` and `controller/GeocodingController.java` — the SEC-15 server-side proxy pattern to mirror (machine access validation, coordinate rounding, provider rate limiting, bounded cache, RestClient usage, configurable base-url).
2. `backend/.../exception/GeocodingProviderException.java` and how `GlobalExceptionHandler` maps it to HTTP.
3. `LocationService.getCurrentLocation(machineId)` — how the machine's current lat/lon is resolved with tenant isolation.
4. The `geocoding:` block in `application.yml` — config conventions to copy for `weather:`.
5. `mobile/lib/core/services/reverse_geocoding_service.dart` — Dio client pattern (shared `apiClientProvider`, request coalescing, CancelToken, dispose-on-account-change).
6. `mobile/lib/features/machines/providers/machine_address_provider.dart` and `machine_location_provider.dart` — Riverpod provider patterns including consent gating (`mapConsentProvider`).
7. `mobile/lib/features/machines/widgets/location_card.dart` and `pages/machine_detail_page.dart` — where a weather card would slot in (around line 93, `LocationCard(machineId: m.id)`).
8. Existing backend tests for GeocodingService (`GeocodingServiceTest.java`) and mobile test conventions under `mobile/test/`.
9. l10n setup: `mobile/lib/l10n/app_en.arb`, `app_hi.arb`, `app_mr.arb` — how strings are added for English/Hindi/Marathi.

Do not modify code during this analysis.

Return: exact files to create/modify for backend and mobile, the weather provider request/response shape you will use, risks, and a concise file-level implementation plan.

Important:
- The GPS device does NOT measure weather. Weather comes from a third-party provider queried with the machine's coordinates.
- Do NOT let the mobile app call a weather API directly — all provider calls go through the backend (same as geocoding).
- Do NOT create a new microservice or change the database schema.
- Do NOT add coordinates to the request — only `machineId`.

---

Prompt 2 — Phase 1 (Backend): Weather proxy endpoint

Implement `GET /api/v1/machines/{machineId}/weather` in the backend, mirroring GeocodingService exactly.

Provider (default: OpenWeatherMap):
- Base URL `https://api.openweathermap.org`, configurable via `weather.provider.base-url`.
- API key injected as `appid` query param from `weather.provider.api-key` (env `WEATHER_API_KEY`). When the key is absent/blank, the service throws `WeatherProviderException` — fail closed, never call without credentials. Never log the key or the full URI.
- Request: `GET /data/2.5/weather?lat={lat}&lon={lon}&appid={key}&units=metric`
- Parse with `@JsonIgnoreProperties(ignoreUnknown = true)` record(s), same style as `GeocodingService.ProviderResponse`. Response fields used: `main.temp`, `main.feels_like`, `main.humidity`, `weather[0].id`, `weather[0].main`, `weather[0].icon`, `wind.speed`, `wind.deg`, `wind.gust`, `rain["1h"]`/`snow["1h"]`, `dt`.
- Unit conversions: `wind.speed`/`wind.gust` arrive in m/s with `units=metric` — convert to km/h (×3.6). Precipitation arrives as mm (rain.1h or snow.1h).

Create `WeatherDto` in `com.yantrago.api.dto.weather` — a Java record with camelCase fields:
- `temperatureCelsius` (Double), `apparentTemperatureCelsius` (Double), `humidityPercent` (Integer), `precipitationMm` (Double, rain+snow last hour, null when absent), `conditionCode` (Integer, OWM weather id e.g. 800), `conditionText` (String, e.g. "Clouds"), `conditionIcon` (String, e.g. "03d"), `windSpeedKph` (Double), `windDirectionDegrees` (Integer), `windGustsKph` (Double), `observedAt` (String, ISO instant from `dt`).

Create `WeatherProviderException` in `com.yantrago.api.exception` — mirror `GeocodingProviderException`; add a `@ExceptionHandler` in `GlobalExceptionHandler` mapping it to HTTP 502 with the existing error format `{ error, message, timestamp, status }`. Do not leak provider internals.

Create `WeatherService` in `com.yantrago.api.service`, mirroring `GeocodingService`:
- `getCurrentWeather(UUID machineId)` returns `WeatherDto` or null when the machine has no usable location.
- Resolve coordinates via `locationService.getCurrentLocation(machineId)` — this enforces SEC-02 machine access. The client never supplies coordinates; the endpoint cannot be abused as an open weather oracle.
- Validate lat/lon bounds before calling the provider.
- Round coordinates to `weather.coordinate-precision` decimals (default 2 ≈ 1.1km — plenty for weather, maximizes cache sharing across nearby machines).
- Bounded LRU cache keyed by `roundedLat|roundedLon|timeBucket` where timeBucket = `Instant.now().getEpochSecond() / cacheTtlSeconds` (default `weather.cache-ttl-seconds: 1800` = 30 min). Old buckets age out via the LRU bound.
- Bucket4j global throttle on provider calls (`weather.min-request-interval-ms`, default 500; `weather.max-wait-ms` 15000) with the same interruptible `tryConsume` pattern.
- Never log coordinates or provider payloads — same as geocoding.

Create `WeatherController` in `com.yantrago.api.controller`:
- `GET /api/v1/machines/{machineId}/weather`
- `@PreAuthorize("hasAuthority('location:read') or hasRole('SUPER_ADMIN')")` — weather derives from the location resource; reuse the location permission rather than inventing a new one.
- 404 when service returns null (no location / no bound device).

Add a `weather:` block to `application.yml` after the `geocoding:` block, same env-var style (`WEATHER_ENABLED`, `WEATHER_PROVIDER_URL`, `WEATHER_API_KEY`, `WEATHER_CACHE_TTL_SECONDS`, `WEATHER_CACHE_MAX_ENTRIES`, `WEATHER_MIN_REQUEST_INTERVAL_MS`, `WEATHER_MAX_WAIT_MS`, `WEATHER_COORDINATE_PRECISION`). Default `${WEATHER_API_KEY:}` empty — the key value is supplied at runtime, never committed. For local development the OpenWeatherMap key lives in the gitignored `.env` at repo root (already created); for staging/production inject it via the deployment environment or secret manager.

Tests (`WeatherServiceTest`, JUnit 5, mirroring `GeocodingServiceTest`):
- Happy path: provider response maps to WeatherDto correctly, including m/s→km/h wind conversion.
- Missing/blank API key → WeatherProviderException before any provider call.
- Machine with no location → null.
- Machine without access → SecurityException propagates.
- Provider error / non-2xx → WeatherProviderException.
- Cache hit on second call within the same time bucket (verify provider called once — mock RestClient or use a stubbed exchange).
- Coordinate rounding applied to the provider URI.
- Disabled flag → WeatherProviderException.

Requirements:
- Java 17 records for DTOs. No Lombok — match existing style.
- No schema changes, no new tables. No changes to device-gateway or shared/ module.
- Run `./gradlew test --no-daemon` — all tests must pass. Report actual results.

STOP after this phase for user verification.

---

Prompt 3 — Phase 2 (Mobile): Weather on the map screen (no new screen, no new card on machine detail)

Surface weather **on the machine location map screen** — the environmental strip below the map already exists with stub values (`_stubConditionLabel`/`_stubWeatherLabel`/`_stubWindLabel` in `machine_location_map_page.dart`, rendered by `environmental_strip.dart`). Replace the stubs with real weather data. Do NOT add a card to `machine_detail_page.dart` and do NOT create a new screen — the user rejected both. Do NOT call any weather API from the app directly — use the backend proxy endpoint built in Phase 1.

Backend contract (Phase 1): `GET /api/v1/machines/{machineId}/weather` → `WeatherDto` (camelCase JSON) or 404 (no location) / 502 (provider down) / 403 (no access).

Create `mobile/lib/models/weather.dart` — immutable `Weather` model, const constructor, `fromJson` for the WeatherDto shape (temperatureCelsius, apparentTemperatureCelsius, humidityPercent, precipitationMm, conditionCode, conditionText, conditionIcon, windSpeedKph, windDirectionDegrees, windGustsKph, observedAt).

Add a `weatherConditionLabel(l10n, int code)` mapping OWM weather ids → localized condition text. OWM id groups: 2xx thunderstorm, 3xx drizzle, 5xx rain, 6xx snow, 7xx atmosphere (fog/mist/haze/dust), 800 clear, 80x clouds. Add all labels to `app_en.arb`, `app_hi.arb`, `app_mr.arb` (follow existing l10n conventions — grep how `noLocationData` / `resolvingAddress` are declared). Do NOT display raw `conditionText` — it is English-only from the provider; always localize from `conditionCode`. `conditionIcon` (e.g. "03d") also encodes day/night (`d`/`n` suffix) — use it to pick the weather glyph variant.

Create `mobile/lib/core/services/weather_service.dart` (follow `reverse_geocoding_service.dart`):
- `WeatherService` wrapping the shared `apiClientProvider` Dio (auth interceptor included).
- `Future<Weather?> getMachineWeather(String machineId, {String? locationKey})` — coalesce identical in-flight requests per machine+locationKey; return null on 404/502/network errors (UI shows fallback).
- `weatherProvider` — recreate on account/org change like `reverseGeocodingProvider`, `ref.onDispose(service.dispose)`, CancelToken.

Create `mobile/lib/features/machines/providers/machine_weather_provider.dart`, mirroring `machine_address_provider.dart`:
- `FutureProvider.autoDispose.family<Weather?, String>`.
- Return null when `currentUserProvider` is null.
- Gate on `mapConsentProvider` like the address provider — weather lookup forwards (rounded) farmer-linked coordinates to a third party, so it requires the same opt-in. SEC-15: the consent disclosure currently names OpenStreetMap + Esri only — bump `_disclosureVersion` to `v3` and update `mapConsentMessage` (en/hi/mr) to name the weather provider (OpenWeatherMap) so existing consents re-prompt once.
- Watch `machineLocationProvider(machineId)` and pass a `locationKey` so a moved machine re-fetches weather; return null when there is no location.

Update `mobile/lib/features/machines/widgets/environmental_strip.dart`:
- `EnvironmentalStrip` stays a pure display widget, but takes a `Weather?` (and a `isDay` hint from `conditionIcon`) instead of three hardcoded labels.
- Segment 1 — condition icon + localized `weatherConditionLabel` (icon from conditionCode + `d`/`n` suffix).
- Segment 2 — `temperatureCelsius` rounded + "°C" (optionally feels-like appended compactly).
- Segment 3 — `windSpeedKph` + compass abbreviation from `windDirectionDegrees`.
- When weather is null (loading, unavailable, no consent): show a single honest "Weather unavailable"/loading label — never fabricated values, never a raw exception. While loading, keep the strip row with a subtle shimmer/three-dot — never blank.

Create `mobile/lib/features/machines/widgets/weather_details_sheet.dart`:
- `showModalBottomSheet` launched by tapping the strip — exposes the full WeatherDto without a new screen: localized condition + icon, temperature, feels-like, humidity %, wind kph + compass direction, gusts kph when present, precipitation mm when present, and "Updated X ago" from `observedAt` via existing `relativeTime`.
- Same `AppSurfaceCard`-consistent styling, safe-area padded, closes on scrim/dismiss.

Update `mobile/lib/features/machines/pages/machine_location_map_page.dart`:
- `ref.watch(machineWeatherProvider(widget.machineId))`; pass `weather.valueOrNull` + loading flag to `MapInfoPanel` → `EnvironmentalStrip`; delete `_stubConditionLabel`/`_stubWeatherLabel`/`_stubWindLabel`.
- Strip tap → `WeatherDetailsSheet`.

Tests (mirroring `mobile/test/features/machine_address_provider_test.dart` conventions):
- Weather model fromJson round-trip.
- Provider: returns null without consent, null without location, fetches on location change (new locationKey).
- Widget test: all three AsyncValue states, empty state, 320px width, long condition labels, en/hi/mr strings present.
- OWM id → label mapping covers every group (2xx–8xx).

Requirements:
- No direct third-party calls from the app. No new dependencies — Dio/Riverpod already exist.
- `flutter test` and `flutter analyze --no-pub` — report actual results. No new warnings.

STOP after this phase for user verification.

---

Prompt 4 — Final validation and report

Validate the complete feature end to end.

Backend validation:
1. `GET /api/v1/machines/{id}/weather` returns weather for a machine with GPS location.
2. 404 for machine with no device/location; 403 without `location:read`; 502 when provider is unreachable.
3. Second request within the TTL bucket does not re-call the provider (cache works — check logs or mock).
4. Tenant isolation: a user from org A cannot fetch weather for org B's machine.
5. No coordinates in logs.

Mobile validation:
1. Environmental strip on the map screen shows real condition icon + label, °C temperature, and wind kph — no stub values remain.
2. Strip tap opens the weather details bottom sheet with the full metric set.
3. Loading, data, and unavailable states all render correctly; consent not granted → strip shows unavailable (no provider call).
4. Machine moved → weather refreshes for new location.
5. en/hi/mr labels render; no overflow at 320px / 200% text scale.

Regression:
- `./gradlew test --no-daemon` passes; `flutter test` passes; `flutter analyze` clean.
- No schema changes, no gateway/shared changes, no new permissions or endpoints beyond the one route.
- Geocoding, location card, map page, and all existing detail-page behavior unchanged.

Provide: modified/created file list, tests executed with actual results, screenshots of the card (light + dark if goldens exist), config keys added, and remaining issues (e.g., provider licensing for production, forecast vs current-only).

Never claim a test passed unless it was actually executed.

---

Final Acceptance Criteria

- `GET /api/v1/machines/{machineId}/weather` exists, is tenant-isolated, access-validated, rate-limited, coordinate-rounded, and cached; provider failures map to 502; the API key is never logged or exposed.
- Mobile weather card shows localized condition, temperature, feels-like, humidity, wind, gusts, and precipitation for the machine's current location; handles loading/unavailable states; gated by the same consent as geocoding.
- The app never calls a weather provider directly; coordinates are never sent by the client.
- No DB schema change, no new microservice, no protocol/gateway changes.
- All backend and mobile tests pass; builds succeed.

Production notes (for later, not this work):
- OpenWeatherMap free tier (60 calls/min, 1M calls/month, commercial use allowed) comfortably covers projected volume with caching. Rotate `WEATHER_API_KEY` via env/secret manager — never commit it. One Call API (alerts, richer forecast) is pay-per-call — keep a spending cap in the OWM dashboard if ever enabled.
- Optional future extension: forecast data (5-day/3-hour endpoint is on the same free tier) or weather-based alerts (e.g., rain before a schedule run) — deliberately out of scope (YAGNI).
