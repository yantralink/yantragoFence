# Expandable 5-Day Weather Forecast — Implementation Plan

Status: Planning only. No implementation is authorized by creation of this document.

Target: The existing YantraGO machine location map screen.

## 1. Approved scope

Add exactly one expandable forecast card immediately after the current weather strip and before the machine controls. Default to collapsed on opening the screen. Expand into up to five vertical daily rows inside the same card.

Required order:

1. Existing machine-name header.
2. Existing interactive map and overlays.
3. Existing current-weather summary.
4. New expandable forecast card.
5. Existing Machine ON/OFF, fence state, and Set Schedule controls.
6. Existing Location card.
7. Existing Theft Protection card.
8. Existing application bottom navigation.

Preserve the existing current-weather details sheet, command acknowledgement behavior, map consent, geofence rendering, navigation, and live location updates.

No separate forecast screen, route, compact summary, horizontal day columns, per-day cards, or extra forecast metrics. Forecast rows contain only day/date, condition icon/text, maximum temperature, and minimum temperature, in that order.

The supplied textual layout is the initial visual specification. An actual reference screenshot is not attached to this request; obtain it before final visual sign-off. Do not claim screenshot matching until the actual reference has been inspected.

## 2. Findings from the existing implementation

Paths below are relative to the repository root.

| Existing file | Finding and planned use |
|---|---|
| `mobile/lib/features/machines/pages/machine_location_map_page.dart` | Already a `ConsumerStatefulWidget`. Watches current weather and preserves a WebView controller. Its body is a `Column` with an `Expanded` map and a non-scrolling `MapInfoPanel`. Own the forecast expansion state here. |
| `mobile/lib/features/machines/widgets/map_info_panel.dart` | Renders `EnvironmentalStrip`, `MachineActionPills`, `MapLocationCard`, then `TheftProtectionCard`. Insert one forecast card between the first two. |
| `mobile/lib/features/machines/widgets/environmental_strip.dart` | Existing current-weather presentation; preserve its data and tap behavior. |
| `mobile/lib/features/machines/widgets/weather_details_sheet.dart` | Current-weather details sheet; retain it as-is. |
| `mobile/lib/core/services/weather_service.dart` | Shared authenticated Dio service; coalesces current-weather calls and cancels requests on account changes. Extend this service for forecast requests. |
| `mobile/lib/features/machines/providers/machine_weather_provider.dart` | Consent-gated Riverpod family depending on the machine location. Reuse these access and lifecycle patterns for forecasts. |
| `mobile/lib/models/weather.dart` | Current conditions only. Existing condition helpers group several conditions broadly; they do not distinguish all labels in the requested forecast examples. |
| `backend/src/main/java/com/yantrago/api/service/WeatherService.java` | Calls OpenWeatherMap `/data/2.5/weather`; it does not return forecasts. It already resolves coordinates server-side and caches current conditions. |
| `backend/src/main/java/com/yantrago/api/controller/WeatherController.java` | Existing current-weather endpoint uses `location:read` or `SUPER_ADMIN`, with machine access delegated through `LocationService`. |
| `backend/src/test/java/com/yantrago/api/service/WeatherServiceTest.java` | Existing mock HTTP service tests are a suitable home for forecast service cases. |
| `mobile/test/features/weather_map_widgets_test.dart` and `map_screen_widgets_test.dart` | Extend these for weather placement, expansion, map-panel layout, and preserved controls. |

The older weather plan describes current conditions and includes outdated scope decisions. This document governs the new forecast extension. Do not recreate the previously removed mock pages or forecast implementation.

## 3. Architecture and data decisions

### Provider and cost

Use the same OpenWeatherMap account and backend-only API key:

`GET https://api.openweathermap.org/data/2.5/forecast?lat=…&lon=…&units=metric&cnt=40&appid=…`

This is the classic 5-day forecast endpoint, previously verified accessible with the configured account. Do not subscribe to One Call or use `/forecast/daily`. Recheck current account limits before rollout; free access remains subject to provider quotas and licence/attribution requirements.

Forty forecasts spaced three hours apart cover approximately five days. This can touch six calendar dates. It is not a guarantee of five complete calendar days, nor seven days.

### Preserve current weather; add forecast capability

Keep `GET /api/v1/machines/{machineId}/weather` and its DTO unchanged. Add:

`GET /api/v1/machines/{machineId}/weather/forecast`

Reuse `WeatherController`, `WeatherService`, the existing authenticated mobile `WeatherService`, and the configured provider credentials. Extract a small shared backend provider client only where needed to keep files focused and prevent duplicate HTTP/throttle logic.

No database migration, new permission, microservice, gateway modification, message contract, or new weather provider is needed.

### Timezone: follow the user's local calendar

The requirement explicitly says the user's local timezone. Therefore the backend should normalize UTC forecast slots, and the mobile forecast provider should aggregate those slots into local calendar days using the device timezone.

Do not aggregate by UTC date or silently substitute `city.timezone`: that is the machine/provider location offset and may differ from the user's timezone. Returning UTC slots also allows the backend cache to be shared independently of the user's timezone.

The provider owns the daily transformation; widgets only display prepared daily values. This is presentation-date grouping, not a direct third-party API call from a widget.

### Proposed API contract

Return a `WeatherForecastDto` containing:

| Field | Type / meaning |
|---|---|
| `fetchedAt` | ISO-8601 UTC instant when this forecast response was fetched; not a fabricated provider observation/model-run timestamp. |
| `slots` | Ordered list of normalized forecast samples, maximum 40. |
| `slots[].forecastAt` | ISO-8601 UTC instant converted from provider `list[].dt`. |
| `slots[].temperatureCelsius` | Finite numeric `list[].main.temp`, nullable when missing. |
| `slots[].conditionCode` | OpenWeatherMap `list[].weather[0].id`, nullable. |
| `slots[].conditionIcon` | Optional provider icon identifier; render bundled Material icons, not remote images. |

Only these fields are needed for this feature. Ignore unknown provider fields. Do not add wind, rain probability, humidity, or precipitation to the forecast UI.

Define immutable forecast models adjacent to the existing weather domain. Reuse condition codes, localization conventions, networking, and units. Do not force a list of future samples into the existing current-condition `Weather` object or break its consumers.

### Deterministic daily transformation

1. Parse UTC instants, validate timestamps and numeric values, sort ascending, and deduplicate identical forecast timestamps.
2. Convert each instant to the user's local timezone and group by year/month/day. Use calendar dates, not UTC-midnight conversions.
3. Drop dates earlier than the user's local today. Select the first five available dates within today through today + 4 calendar days. Never fill missing dates or use a sixth date to conceal a missing day.
4. Include today's remaining forecasts if present. Show fewer rows when fewer valid dates exist.
5. High = maximum valid sampled `main.temp`; low = minimum valid sampled `main.temp`. Do not initialize maximum with a positive minimum constant: all-negative temperatures must work.
6. These are extrema of the available 3-hour samples, not guaranteed full-day extremes. Retain a partial-coverage flag when fewer than eight distinct slots contribute to a day.
7. Choose the most frequent valid condition code for the day; break ties by the sample closest to local noon, then earlier timestamp. This is a representative condition, not a safety recommendation.
8. Omit a day with no usable temperature and no usable condition. Where only one type is missing, show a localized unknown condition or `—` temperature; never substitute zero.
9. Preserve the selected local date as a calendar date. Derive header range and Today highlighting from the same date basis.

Use localized labels that distinguish clear/sunny, partly cloudy, cloudy/overcast, light rain, rain, thunderstorm, drizzle, snow, and atmospheric conditions as supported by valid codes. Unknown codes use an explicit unknown-condition label. Prefer forecast-specific helpers when refining the existing broad helpers would change current-weather wording.

For partial days, add a discreet localized “Partial forecast” caption within the date cell and include it in accessibility semantics. This describes coverage without adding another weather metric. Do not display unavailable whole-day estimates.

## 4. UI and state contract

### Card structure

- Use existing surface, spacing, radius, and border tokens. Light mode uses the existing white surface; dark mode uses the existing dark surface.
- Full-width header: calendar icon, localized “5-Day Forecast”, actual date range aligned right, and chevron.
- Range examples are data-derived: “Today – Mon”, “Fri – Mon”, or one date for one available day. No fake range while data is loading or absent.
- Entire header has one tap action and a minimum 48 logical pixel touch target. No separate arrow-only hit area.
- Expanded content is a `Column` of at most five rows, with subtle separators and no internal scrolling.
- Row order: day/date left, condition icon and localized text middle, high then low right (`29° / 19°`). Semantics explicitly identify high/low and Celsius.
- Today's row uses the existing soft-blue semantic surface. Other rows use the card surface.
- Allow text wrapping under large fonts; compact does not mean fixed row heights that clip text. Do not truncate the header or important weather information to force a desktop-sized row onto small phones.

### Expansion and animation

Own `bool isForecastExpanded = false` in the existing map page state. Pass the value and a toggle callback through `MapInfoPanel` into the forecast card.

Use Flutter's built-in height animation (`AnimatedSize` with clipping, or equivalent) and `AnimatedRotation`, with the same 275ms duration and easing. Respect reduced-animation accessibility settings.

Toggle only this bool. Do not fetch data, invalidate providers, recreate the map controller, navigate, send commands, or change the scroll position intentionally on a header tap.

Preserve expansion during ordinary weather/location rebuilds. Reset to collapsed for a new page instance, a changed machine identity, logout/account change, or revoked map consent. Remove hidden rows from semantics and hit testing after collapse.

### Loading, errors, and incomplete data

| Data state | Collapsed | Expanded |
|---|---|---|
| Loading | Header with a small progress indicator; no guessed range | Header plus compact localized loading content |
| Five available days | Header with actual range | Five rows |
| One to four available days | Header with actual range | Only available rows; localized available-day count |
| Empty, no usable location, or failure | Header with no range and compact “Weather forecast unavailable” status | Same message in the body; no invented rows |

The normal successful collapsed state is header-only. Status text for failures may wrap within the header; do not create a second summary card.

Use `.when()` for loading/error/data. Never show an old account's forecast during loading or failure. An initial failure must not remove the current-weather strip or block machine controls.

### Stationary map and scrollable lower panel

The existing unbounded info panel must not grow until the map collapses.

Use `LayoutBuilder` on the available body below the AppBar and above the existing navigation shell. Keep the map as a separate, non-scrolling sibling. Give the lower panel a bounded viewport with exactly one vertical scroll view around its contents.

Recommended initial sizing: allocate 45% of available body height to the map, clamped to 180–360 logical pixels where space allows; preserve at least half the body for the lower panel on very short viewports. Tune these design constants against actual screen captures. The allocation depends on viewport dimensions, never forecast expansion.

Expansion grows the scrollable content inside the lower panel; it does not resize/reinitialize the map. Keep map controls and attribution visible. Retain the same WebView/controller and the existing navigation shell.

Do not wrap the entire screen in a scroll view, add a nested forecast `ListView`, cover controls with a positioned overlay, or add another bottom navigation bar.

## 5. Execution rules

Read `AGENTS.md`, relevant sections of `docs/YANTRAGO_PROJECT_STRUCTURE.md`, `docs/YANTRAGO_IMPLEMENTATION_PLAN.md`, and the existing map UI conventions before implementation.

Run only the phase explicitly authorized. Every phase must compile, include meaningful checks for its changes, and end with:

“Phase N is ready for verification. Please verify these changes. Should I start Phase N+1?”

Do not proceed until the user confirms. Record actual commands/results and remaining limitations. Never mark a phase complete based only on writing code.

## Phase 1 — Backend forecast endpoint and shared provider handling

**Deliverable:** Independently testable additive endpoint; existing clients continue working.

**Implementation prompt:**

> Implement Phase 1 of this document only. Inspect the latest WeatherController, WeatherService, LocationService, WeatherDto, GlobalExceptionHandler, and existing weather tests. Add the normalized forecast endpoint and records specified in section 3. Keep the current-weather response compatible. Reuse the existing weather service and extract only the shared provider HTTP/configuration/throttle logic necessary to avoid duplication and excessive file sizes. Do not implement mobile changes.

Tasks and acceptance:

- Apply JWT and the existing `location:read` / `SUPER_ADMIN` rule. Validate UUID paths and enforce organization/customer machine access before returning any cached result.
- Resolve coordinates through `LocationService`; accept no coordinates or organization identifier from the client. Validate finite lat/lon and geographic bounds.
- Use the same server-held key and configurable HTTPS provider base URL. No credentials, request URIs, coordinates, or upstream bodies in logs or client error responses.
- Keep a bounded forecast cache separate from current weather: proposed TTL 3 hours, with expiry measured from fetch completion and an injectable clock.
- Share one provider request budget across current and forecast calls. Existing 500ms spacing can exceed the previously quoted 60/minute allowance; set a conservative configurable combined default, such as 1,100ms, and verify actual account limits.
- Coalesce concurrent requests for the same rounded-coordinate forecast key. Access validation still runs for every caller. For multiple backend replicas, use the existing Redis infrastructure for shared rate limits/coalescing or document and enforce a deployment-wide quota allocation; a per-process bucket is not a global account limit.
- Configure bounded connect/read timeouts, bounded waiting, and a short failure backoff. Honour provider 429 retry timing; do not retry indefinitely or cache errors as successful forecasts.
- Return 404 for no usable machine location; 200 with empty slots for an explicitly empty valid provider forecast; 502 for malformed/unusable provider responses or provider failure through `WeatherProviderException` and `GlobalExceptionHandler`. Keep authorization and authentication errors distinct.
- Reject invalid/absent structural fields; skip individually invalid samples, but do not turn an entirely malformed sample list into a successful empty forecast.
- Add `weather.forecast-cache-ttl-seconds` / `WEATHER_FORECAST_CACHE_TTL_SECONDS`, plus any necessary timeout settings following repository conventions. Validate configuration bounds.
- Include a configurable forecast-only enable switch for rollout/rollback; disabled returns the standard unavailable error without breaking current weather.

Tests: field mapping; UTC timestamps; missing optional condition; missing temperatures; malformed payload; invalid coordinates; no location; missing credentials; provider timeout/401/429/500; cache expiry; concurrent same-key requests; moved machine; access denied even on warm cache; controller authentication/RBAC/tenant isolation. Extend existing test homes; add dedicated classes only when necessary.

Run focused weather tests and `./gradlew :backend:build --no-daemon`. Report failures honestly and fix new failures. Stop for verification.

## Phase 2 — Mobile forecast data and local-calendar grouping

**Deliverable:** Tested forecast provider and models, buildable without changing the rendered map screen.

**Implementation prompt:**

> Implement Phase 2 only using the verified Phase 1 contract. Extend mobile/lib/core/services/weather_service.dart with getMachineForecast; reuse apiClientProvider and account-scoped cancellation. Add immutable forecast slot/response and daily presentation models under features/machines/models/, following the current weather domain conventions. Implement the daily transformation from section 3 as a pure function invoked by a Riverpod forecast provider. Do not add a screen, route, mock production data, or forecast UI in this phase.

Tasks and acceptance:

- Add `machineWeatherForecastProvider` in the existing machines providers folder. Gate on authenticated identity, consent, and usable location, including loading/error consent states.
- Keep one stable provider subscription per visible machine map screen in the next phase, regardless of expansion.
- Coalescing identity includes account lifecycle, machine ID, and the location snapshot. Cancel/ignore obsolete responses when identity, consent, or location changes.
- Keep forecast and current-weather pending requests distinct. Do not convert 401/403 into fabricated empty data; retain the existing auth interceptor behavior.
- No permanent mobile cache keyed only by machine ID. Bound any retention; preserve fetchedAt so refresh decisions are explicit.
- Do not attach forecast network calls to every telemetry tick, widget build, or expansion. Use the existing location snapshot refresh flow; backend rounding/cache handles nearby coordinates.
- On app resume, re-evaluate local dates/timezone without fetching if cached forecast data remains fresh. Re-fetch only when missing/expired or after an actual location refresh. For a screen open across local midnight, re-group held samples without a provider request; cancel lifecycle timers/listeners on disposal.
- Parsing must reject invalid dates; never silently convert them to epoch or normalize malformed day numbers into plausible dates.

Tests: UTC-to-local date boundaries, half-hour zones, positive/negative offsets, DST using an injectable local-date conversion in unit tests, today + four-day selection, six source dates, missing dates, duplicate/unsorted timestamps, all-negative and zero temperatures, partial days, unknown codes, deterministic condition ties, account/consent changes, stale asynchronous responses, and coalesced request counts.

Run targeted Flutter model/provider tests, focused analysis, and an Android debug build. Stop for verification.

## Phase 3 — Expandable card integrated into the existing map

**Deliverable:** Working user-facing feature with real forecast data and no new route.

**Implementation prompt:**

> Implement Phase 3 only. Add one expandable WeatherForecastCard to MapInfoPanel immediately after EnvironmentalStrip and before MachineActionPills. Connect the verified forecast provider from the existing MachineLocationMapPage. Follow every behavior, data-state, layout, animation, and accessibility rule in section 4. Preserve current weather and all existing controls. Implement no backend changes or unrelated redesign.

Expected files:

- Update `mobile/lib/features/machines/pages/machine_location_map_page.dart`.
- Update `mobile/lib/features/machines/widgets/map_info_panel.dart`.
- Add `mobile/lib/features/machines/widgets/weather_forecast_card.dart`; extract a row widget only if needed for readability/file limits.
- Add English/Hindi/Marathi strings to `mobile/lib/l10n/app_en.arb`, `app_hi.arb`, and `app_mr.arb`; regenerate localizations with the existing command.
- Extend relevant map/weather widget tests. Keep any fixture data in tests only.

Detailed acceptance:

- Default header-only state; opening, closing, and repeated toggles use the same already-requested data.
- Max/high temperature appears before min/low; both use Celsius. The header range matches actual visible rows.
- Use localized weekday/month/range formatting, loading/unavailable/partial/count messages, and condition labels.
- Screen reader announces one header button, its expanded/collapsed state, and expand/collapse action. The chevron conveys the same action without duplicate focus targets.
- Row semantics read full date, condition, high, low, units, and partial status. Hidden rows are not accessible.
- Preserve visible focus, contrast, large text, dark theme, reduced motion, and tappable header dimensions.
- Keep the map stationary and interactive during scroll and animation. No `Expanded` children inside an unbounded scroll view.
- Forecast errors remain confined to the forecast card.

Tests: collapsed by default; toggle via text and arrow area; five rows inside one card; fewer rows; no-data/loading/error; Today highlighting; actual date range; high/low order; repeated toggles; semantic expanded state; hidden-row semantics; no route changes; unchanged service invocation count after toggles; persistent WebView/controller identity; scroll reachability of schedule/location/theft controls.

Run targeted map/weather widget tests and focused analysis. Build Android debug APK. Capture collapsed and expanded renders using real fonts/icons; unreadable square-glyph golden images are not acceptable review artifacts. Stop for verification.

## Phase 4 — Integration, device verification, and release evidence

**Deliverable:** Reviewable end-to-end verification and deployment instructions.

**Implementation prompt:**

> Validate the completed feature without adding scope. Exercise the real forecast endpoint and existing map screen in an authorized development/staging environment. Fix only defects in this feature. Document actual results, screenshots, quota behavior, and remaining limits. Do not deploy to production or start another feature automatically.

Verification matrix:

| Area | Required evidence |
|---|---|
| Real data | Normalized API slots correspond to local-date rows; screenshots labelled with locale/timezone and test date. |
| Request counts | One initial forecast load per stable screen/provider lifecycle; ten expand/collapse cycles add zero requests. Separate backend cache hits from outbound provider calls. |
| Forecast window | Five rows when present in the selected window; no sixth day or fabricated missing days; partial coverage labelled. |
| Layout | Small/large Android screens, landscape, 200% font scale, English/Hindi/Marathi, light/dark. |
| Map | Pan, pinch zoom, zoom controls, layer toggle, marker/geofence updates, attribution remain functional; scroll does not move the map off-screen. |
| Controls | Existing machine/fence command flow and ACK-backed state, Set Schedule, location sharing, theft protection, and bottom navigation preserve behavior. Test commands with a simulator/test device, not a production machine. |
| Failures | Provider outage/429, missing location, denied consent, no network, partial data, unknown condition, and token/account changes. |
| Lifecycle | Leave/reopen defaults to collapsed; ordinary refresh retains expansion; identity/consent changes clear prior data; midnight/resume re-groups dates correctly. |
| Accessibility | TalkBack header and row order, state announcements, no hidden-row focus, contrast and reduced motion. |

Run the relevant backend build/tests and mobile regression suite per repository CI conventions. Record pre-existing failures separately; do not claim a clean result when gates failed. Build the Android artifact required by the repository release process.

Rollout: backend additive endpoint first, then mobile build. Retain current-weather availability if forecasts are disabled. Verify provider attribution and deployment-wide quotas. A three-hour forecast cache with 50 continuously requested coordinate cells is roughly 400 forecast calls/day, plus current-weather traffic; actual volume depends on cells, expiry, retries, and replicas.

Final handoff:

1. Modified files and component responsibilities.
2. Expansion state/animation and fixed-map scrolling explanation.
3. Provider field mapping, timezone grouping, and partial-day limitations.
4. Measured request-count evidence for toggling and cache/coalescing.
5. Readable collapsed/expanded screenshots, including a small-device view.
6. Test/build commands and actual results.
7. Reference screenshot comparison, or explicitly outstanding visual sign-off.
8. Configuration, rollout/rollback instructions, and remaining limitations.

## 6. Completion checklist

- [ ] Phase 1 backend endpoint verified and user-approved.
- [ ] Phase 2 mobile data layer verified and user-approved.
- [ ] Phase 3 integrated UI verified and user-approved.
- [ ] Phase 4 end-to-end/device verification completed.
- [ ] Exactly one forecast card appears in the required position.
- [ ] Expansion adds no API calls and creates no route.
- [ ] Rows use actual data, local dates, and honest coverage.
- [ ] Map, controls, current weather, and navigation remain functional.
- [ ] Final visual acceptance is based on readable screenshots.
