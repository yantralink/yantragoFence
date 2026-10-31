# Devin Implementation Prompts

# YantraGO App — Machine Map Screen UI Redesign

**Purpose:** Redesign the existing machine map screen (`MachineLocationMapPage`) to match the approved mockup image — a map-first layout with overlay status chips, side control rails, gauges, an environmental strip, machine action pills, a location card, and a Theft Protection card. This is a UI replacement of the existing screen, not a new route or duplicate page.

**Source of truth:** the approved mockup screenshot (map screen showing "Shubham Hedau" with Moving chip, side control rails, 36 km/h gauge, 288 km odometer, weather strip, Machine ON / Fence OFF / Set Schedule pills, location card, and Theft Protection card).

**How to use this document:** Give the prompts to Devin in order. Prompt 1 is analysis-only. Implement phase by phase and STOP after each phase for user verification (AGENTS.md rule 20). A throwaway mockup implementation already exists for visual reference — see §5.

---

## 1. Current state (audited)

| Item | Current implementation |
|---|---|
| Route | `/app/machines/:id/location` inside `ShellRoute` → bottom nav preserved automatically |
| Page | `mobile/lib/features/machines/pages/machine_location_map_page.dart` |
| Layout | `Column`: WebView map (`Expanded flex:3`) + bottom panel (`Expanded flex:1`) with `_AddressBar` + `_TheftProtectionPanel` |
| Map engine | Bundled Leaflet in `assets/map/map.html` + `map.js`, rendered via `webview_flutter` `WebViewController` |
| JS bridge | Only `window.yantraMap.init(config)` — `{latitude, longitude, zoom, markerPopupLabel, geofence?}`; re-calling `init` re-centers. **No zoom/layer functions exposed** |
| Consent | SEC-15 gate (`mapConsentProvider`) — map + geocoding need affirmative consent; declined view shows raw coordinates |
| Providers | `machineDetailProvider` (name, isOnline, status, ignitionOn, fencingState, battery/gsm), `machineLocationProvider` (lat, lng, speed, course, recordedAt, updatedAt), `machineAddressProvider` (reverse geocoded address), `theftProtectionNotifierProvider` (protectionEnabled, geofence lat/lng/radius, enable/disable) |
| Commands | `commandProvider.sendCommand(machineId, imei, 'FENCING_ON'/'FENCING_OFF')` — auditable, ACK-confirmed (rules 5–6) |
| Elements already absent | No header action icons, no "Get Location", no "Stop Machine", no Home/Activity/Sensors row — nothing to remove there |
| Live location (Phase 1 correction) | `telemetrySocketProvider` carries NO location/speed (`Telemetry` = voltage/battery/gsm/ignition only). Backend `LocationBroadcastService` already pushes full `MachineLocation`-shaped payloads (lat, lng, **speed kph**, course, recordedAt) to STOMP `/topic/location/{machineId}` — mobile has no subscriber yet. Add `locationSocketProvider` mirroring `telemetrySocketProvider` for live chip/gauge/marker updates. Note: `AppConfig.wsTopicLocation` is stale (`'/topic/locations'` — backend topic is singular `/topic/location/{id}`); fix when wiring. REST `machineLocationProvider` catches all errors → `null` (offline shows the "no location" empty state, not an error). Use a small speed threshold (~1 km/h) for `Moving` to avoid GPS jitter. |

## 2. Target layout (from approved mockup)

```
┌────────────────────────────────────────────┐
│ ←  Shubham Hedau            (green title)  │
├────────────────────────────────────────────┤
│  ┌──────────────────────────────────────┐  │
│  │ [● Moving]                           │  │
│  │  (bell)                              │  │
│  │  (layers)                            │  │
│  │        ○ machine in                  │  │
│  │          geofence circle       [+]   │  │
│  │                                [−]   │  │
│  │ ◉36km/h                              │  │
│  └──────────────────────────────────────┘  │
│  ✓ Good operating │ ☀ 24°C Clear │ 🌀 11.9 │
│  [⏻ Machine ON][⚡ Fence OFF][📅 Schedule] │
│  ┌ LOCATION ────────────────────── (share)┐│
│  │ Mahatma Gandhi Road, Ashokanagar,      ││
│  │ Bengaluru — Updated 1s ago             ││
│  └────────────────────────────────────────┘│
│  ┌ 🛡 Theft Protection ─────────── [toggle]┐│
│  │ Get alerts for unauthorized movement   ││
│  └────────────────────────────────────────┘│
├────────────────────────────────────────────┤
│  Machines │ Notifications(99+) │ Analytics │ Profile │
└────────────────────────────────────────────┘
```

- **AppBar:** back arrow + green machine name only. No trailing icons.
- **Map:** `Expanded` canvas between AppBar and info panel — fills all remaining space. OSM attribution must stay visible (panel sits *below* the map, never overlapping it).
- **Status chip:** top-left white pill — colored dot + `Moving`/`Stopped` (speed-derived).
- **Left rail:** white circular buttons — alerts (bell), map layers (OSM ↔ satellite).
- **Zoom pill:** vertical white `+` / `−` pill — bottom-right.
- **Speed gauge:** dark disc with green arc, `36 km/h` — bottom-left. Hidden when `speed` is null.
- **Machine marker:** custom machine `L.divIcon` inside the geofence circle.
- **Environmental strip:** 44px row, three items separated by hairline dividers — operating condition | weather | wind (static placeholder values until the weather feature lands).
- **Action pills:** three pills — `Machine ON` (green tone), `Fence OFF` (red tone), `Set Schedule` (neutral tone).
- **Location card:** pin icon, `LOCATION` caption, address (wraps), `Updated Xs ago`, share icon right.
- **Theft Protection card:** light-blue tinted card — shield icon, title, subtitle "Get alerts for unauthorized movement or tampering", toggle.
- **Bottom nav:** unchanged (Machines / Notifications+ / Analytics / Profile).

## 3. Data binding matrix — what is real vs what is missing

| Mockup element | Real source today | Status |
|---|---|---|
| Machine name (green title) | `machineDetailProvider → Machine.name` | ✅ real |
| Back arrow / bottom nav | Existing route + `_AppShell` | ✅ real |
| Map, marker, OSM attribution | Bundled Leaflet via WebView | ✅ real |
| Geofence circle | `TheftProtectionStatus.geofence*` | ✅ real |
| Status chip | `MachineLocation.speed > 0` → `Moving`/`Stopped` (decision §4.1) | ✅ real |
| Speed gauge | `MachineLocation.speed` (nullable — hide gauge if null) | ⚠️ real but often null |
| Alerts button | `context.push('/app/alerts')` | ✅ functional |
| Set Schedule pill | `context.push('/app/machines/:id/schedule')` | ✅ functional |
| Machine ON pill | `Machine.ignitionOn` + `FENCING_*` commands via `commandProvider` (ACK-confirmed, never optimistic) | ✅ real |
| Fence ON/OFF pill | `Machine.fencingState` + same command flow | ✅ real |
| Location card | `machineAddressProvider` + `loc.recordedAt`/`updatedAt` | ✅ real |
| Share icon | `url_launcher` → Google Maps link (decision §4.6) | ✅ real |
| Theft Protection card + toggle | `theftProtectionNotifierProvider` (non-optimistic) | ✅ real |
| `▶ duration` moving pill | **Dropped** (decision §4.2) | 🚫 removed |
| `288 km` odometer | **Dropped** (decision §4.2) | 🚫 removed |
| Environmental strip (weather/wind) | Static placeholder now; real data when `WEATHER_FEATURE_PLAN` lands (decision §4.3) | ⚠️ stub |
| Zoom `+/−` | Add `zoomIn`/`zoomOut` to `map.js` (decision — keep) | ⚠️ small JS addition |
| Map layers button | OSM ↔ satellite toggle — new tile layer + `setBaseLayer` + CSP `img-src` update + consent copy (decision §4.5) | ⚠️ JS + CSP |
| Machine marker | Custom machine `L.divIcon` marker (decision §4.7) | ⚠️ JS change |
| `P` / pencil / cloud / sound buttons | **Removed** (decision §4.4) | 🚫 removed |

## 4. Decisions (confirmed by user)

1. **Status chip:** `Moving` / `Stopped`, derived from `MachineLocation.speed` — `speed > threshold → Moving`, otherwise `Stopped`. Null speed → `Stopped`.
2. **Odometer + moving-duration pill:** **dropped** — do not render. Bottom overlays are speed gauge (left) and zoom pill (right) only.
3. **Environmental strip:** **ship now with clearly-marked static placeholder values**; replace with the real `weatherProvider` when `WEATHER_FEATURE_PLAN` is implemented. All strip values must live behind a single stub so swapping is a one-line change.
4. **Right-rail buttons (P, pencil, cloud, sound):** **removed entirely** — no right rail in the final UI.
5. **Map layers button:** **implement OSM ↔ satellite switching.** Add a second tile layer (Esri World Imagery), `setBaseLayer` in `map.js`, CSP `img-src` update in `map.html`, and consent copy mentioning the additional provider.
6. **Share icon:** `url_launcher` → Google Maps link (existing pattern, no new dependency).
7. **Marker:** **custom machine-icon marker** (`L.divIcon` with inline SVG — no asset files needed).

## 5. Work already done (mockup scaffolding)

A dummy implementation of this exact UI exists for visual reference — convert it to real bindings, do not re-design:

| File | Contents |
|---|---|
| `lib/features/machines/pages/machine_map_mockup_page.dart` | Full layout: app bar, map stack, all overlay positions, panel composition |
| `lib/features/machines/widgets/map_mockup_overlays.dart` | Status chip, round buttons, zoom pill, moving pill, `_OverlaySurface` |
| `lib/features/machines/widgets/map_mockup_gauges.dart` | Speed + odometer circular gauges |
| `lib/features/machines/widgets/map_mockup_rails.dart` | Left/right control rails |
| `lib/features/machines/widgets/map_mockup_sheet.dart` | `MapMockupInfoPanel` — strip + pills + cards composition |
| `lib/features/machines/widgets/map_mockup_cards.dart` | Location card + Theft Protection card |
| `lib/features/machines/widgets/map_mockup_strip.dart` | Environmental strip |
| `lib/features/machines/widgets/map_mockup_placeholder.dart` | Web/desktop map stand-in (not for production) |
| `lib/dev/map_mockup_preview_main.dart` | Standalone preview entry |
| `lib/routing/app_router.dart` | Temp route `/app/map-mockup` |
| `lib/features/machines/widgets/location_card.dart` | Temp nav redirect to mockup (revert) |

Rename these to production names (drop the `Mockup` prefix) as they are converted.

## 6. All changes required

### `assets/map/map.js` + `map.html` (contained changes)
- Add `window.yantraMap.zoomIn()`, `zoomOut()`, and `recenter()` (delegating to Leaflet `map.zoomIn()/zoomOut()/setView`); keep `init(config)` contract unchanged.
- Add a second tile layer — Esri World Imagery satellite — with `window.yantraMap.setBaseLayer('osm' | 'satellite')` and its own attribution.
- Update `map.html` CSP `img-src` to allow the satellite tile host; update consent copy to name the additional provider.
- Replace the default pin with a custom machine marker (`L.divIcon` + inline SVG).

### `machine_location_map_page.dart` — restructure in place
- Keep: route, consent gate, `_ensureMapController`, `NavigationDelegate`, `_updateMap`/`_flushMapConfig`, coordinate validation, `MachineLocation` contract.
- Replace body layout: `Column` → `Expanded(Stack)` map + `InfoPanel`.
- Add overlay `Positioned`s: status chip (top-left), left rail (alerts, layers), zoom pill (bottom-right), speed gauge (bottom-left, hidden when speed is null).
- Compose `InfoPanel`: environmental strip (static placeholder stub), action pills row, location card, theft card.
- Bind every value to providers; hide widgets when their data is null.
- Add `location_socket_provider.dart` (mirrors `telemetrySocketProvider`) subscribing to `/topic/location/{machineId}` and feeding `machineLocationProvider` state for live chip/gauge/marker updates; fix `AppConfig.wsTopicLocation`.

### New widgets (extracted from mockup files, renamed)
`map_status_chip.dart`, `map_control_rail.dart`, `map_zoom_pill.dart`, `map_gauges.dart`, `machine_action_pills.dart`, `environmental_strip.dart`, `map_location_card.dart`, `theft_protection_card.dart`.

### l10n — new keys in `app_en.arb`, `app_hi.arb`, `app_mr.arb`
`statusMoving`, `statusStopped`, `mapMachineOn`/`mapMachineOff`, `mapFenceOn`/`mapFenceOff`, `setSchedule`, `locationLabel`, `theftProtectionSubtitle`, `movingDuration` (`{duration}`), `speedKmh` (`{speed}`), `odometerKm` (`{distance}`), weather keys if strip ships.

### Commands & safety (rules 5–6)
- Machine ON / Fence OFF pills trigger `commandProvider.sendCommand` and show "awaiting ACK" state — never optimistically flip state.

### Cleanup (final phase)
- Delete: mockup page, all `map_mockup_*` files, `lib/dev/map_mockup_preview_main.dart`, `/app/map-mockup` route, and revert `location_card.dart` navigation to `/app/machines/$machineId/location`.

## 7. Implementation prompts (phase-wise — STOP after each)

**Prompt 1 — Audit (analysis only).** Verify §1 against the real code; confirm provider fields, map.js bridge, consent gate, and report any drift. Do not edit.

**Prompt 2 — Layout skeleton.** Restructure `machine_location_map_page.dart` to the map-first `Column(Expanded(Stack) + InfoPanel)` layout. Extract overlay + panel widgets from the `map_mockup_*` files into production-named widgets. Keep consent gate, WebView plumbing, and all existing behavior. Render overlays/panels with real providers; hide anything without data.

**Prompt 3 — Map controls + marker.** Add `zoomIn/zoomOut/recenter` and `setBaseLayer` (OSM ↔ Esri satellite) to `map.js`; update `map.html` CSP `img-src` and consent copy; wire zoom pill + layers button. Replace default pin with custom machine `divIcon` marker.

**Prompt 4 — Live overlays.** Bind status chip (`Moving`/`Stopped` from `speed`) and speed gauge to real data with null-hide behavior. Wire Alerts + Set Schedule navigation.

**Prompt 5 — Below-map panel.** Bind action pills to `ignitionOn`/`fencingState` + command flow (ACK-aware), location card to address + timestamps + share, theft card to `theftProtectionNotifierProvider`. Environmental strip only if its data exists.

**Prompt 6 — Removals + cleanup.** Remove anything the decisions in §4 dropped; delete all mockup scaffolding (§5 cleanup list).

**Prompt 7 — Validation.** Widget tests for new components (states: loading/null/enabled-disabled); verify small-screen fit, consent gate, offline/error states, attribution visibility, and the acceptance checklist.

## 8. Acceptance checklist

- [ ] Same route `/app/machines/:id/location` — no duplicate page, bottom nav intact
- [ ] AppBar: back + green machine name only
- [ ] Map is the dominant canvas; OSM attribution never covered
- [ ] Status chip, left rail, zoom pill, speed gauge positioned per mockup
- [ ] Environmental strip values clearly behind a single stub — no scattered hardcodes
- [ ] Every visible control performs its action; no inert/decorative buttons
- [ ] Action pills show real `ignitionOn`/`fencingState`; commands are ACK-aware
- [ ] Location card shows real address + timestamp + working share
- [ ] Theft Protection card shows confirmed state; toggle not optimistic
- [ ] SEC-15 consent gate unchanged
- [ ] All mockup scaffolding deleted; temp route + nav redirect removed
- [ ] New l10n keys added for en/hi/mr
- [ ] `flutter analyze` clean; widget tests added and passing (report actual output)
