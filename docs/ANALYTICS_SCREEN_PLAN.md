# Machine Analytics Screen — Design & Implementation Plan

Status: **DRAFT — awaiting user review**
Created: 2026-09-26
Scope: Mobile (new 4th tab) + Backend (2 new endpoints + 1 reuse)

---

## 1. Requirement

A new **Analytics** tab in the bottom navigation (Machines | Alerts | **Analytics** | Profile) showing per-machine historical graphs:

1. **External Battery Health & status graph**
2. **Fence Fault graph with timing**
3. **Machine History — start/stop with graph**

---

## 2. Data availability — verified against production

All three graphs are backed by data that is **already collected today**.
Verified live on the demo machine (`8ff3b4f6…`, IMEI `866221070994202`):

| Graph | Data source | Reality check (last 24h) |
|---|---|---|
| External battery | `voltage_readings` (partitioned time-series) | 145 pts, 12.79–12.98 V |
| Internal battery | `battery_readings` | 316 pts, 100 % |
| GSM signal | `gsm_readings` | 316 pts |
| Fence fault timing | `alerts` (`FENCE_FAULT` only + `incident_state`, `triggered_at`, `resolved_at`) | schema confirmed |
| Machine sessions | `location_history.ignition_on` + `speed` | 97 `t` rows exist; 592 moving pts/24h |
| Manual ON/OFF | `machine_commands` (`command_type`, `status`, `created_at`, ack time) | rows confirmed |

### Existing API we reuse
- `GET /api/v1/telemetry/{machineId}?from&to` — **already returns**
  voltage + battery + GSM series (default last 24h). No backend work
  needed for graph 1.

### New API needed
- `GET /api/v1/machines/{machineId}/faults?from&to` — returns
  **`FENCE_FAULT` intervals only** (start, end, duration, state) from
  `alerts` where `alert_type='FENCE_FAULT'`. Other fault/alert types
  (EXTERNAL_POWER_*, GEOFENCE_BREACH, etc.) are intentionally excluded —
  user decision: show only the Fence Fault that matches the red bulb.
- `GET /api/v1/machines/{machineId}/sessions?from&to` — returns run
  sessions derived server-side from `location_history.ignition_on`
  transitions (+ speed>0 fallback), plus manual ON/OFF commands as
  markers. Deriving sessions on the server keeps the app thin and the
  logic testable.

---

## 3. Screen design

### 3.1 Navigation

4th tab between Alerts and Profile:

```
[ Machines ] [ Alerts ] [ Analytics ] [ Profile ]
                          icon: Icons.insights / bar_chart
```

- Route: `/app/analytics`, pushed inside the existing `ShellRoute`
- Label key `navAnalytics` in all 3 locales (en/hi/mr)

### 3.2 Layout — top to bottom

```
┌──────────────────────────────────────┐
│ Analytics                    🔄      │
│ ┌──────────────────────────────────┐ │
│ │ Machine:  Shubham Hedau       ▼  │ │  ← dropdown (customer's machines)
│ └──────────────────────────────────┘ │
│  [Today] [7 Days] [30 Days]          │  ← range chips (Today default)
│                                      │
│ ┌─ External Battery Health ────────┐ │
│ │  13.0┤      ╭─╮                  │ │
│ │  12.5┤  ╭───╯  ╰───╮             │ │  ← voltage line chart
│ │  12.0┼──╯          ╰────         │ │    (shaded healthy band)
│ │  11.5┤                           │ │
│ │  Now: 12.9 V  ● Healthy          │ │  ← status chip
│ └──────────────────────────────────┘ │
│ ┌─ Fence Fault ────────────────────┐ │
│ │ ▓▓ fault 19:02–19:14 (12 min)    │ │  ← timeline bars per
│ │      ▓ fault 07:40–07:46         │ │    alert interval
│ │ Total: 2 faults today · 18 min   │ │
│ └──────────────────────────────────┘ │
│ ┌─ Machine Activity ───────────────┐ │
│ │ ON  ━━━━     ━━━━━━━      ━━━    │ │  ← session bars
│ │ OFF ─    ────       ─────    ──  │ │
│ │ 06:00   10:00   14:00   18:00    │ │
│ │ Today: ON 3 sessions · 4h 12m    │ │
│ │ ⚡ 06:00 manual ON · 18:30 OFF   │ │  ← command markers
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

### 3.3 Widget structure (Riverpod, feature folder)

```
features/analytics/
  pages/analytics_page.dart           → screen shell + pull-to-refresh
  widgets/
    machine_selector.dart             → dropdown reusing dashboard provider
    range_chips.dart                  → Today / 7d / 30d
    battery_health_card.dart          → voltage line chart + health chip
    fault_timeline_card.dart          → fault intervals timeline
    activity_timeline_card.dart       → ON/OFF session bars + command markers
  providers/
    analytics_provider.dart           → family(machineId, range) for
                                        telemetry / faults / sessions
  models/
    fault_interval.dart, machine_session.dart
```

Rules respected: no API calls in widgets, `AsyncValue.when()` for all 3
states (loading / error / data), localized strings, `context.go()` tab
nav (rule 22).

---

## 4. Graph details

### 4.1 External Battery Health
- Line chart of `voltage_readings.voltage` over the range
- **Health band: 11.8–14.0 V confirmed** — shade the healthy zone;
  excursions outside it are immediately visible
- Below chart: `Now: X.X V` + status chip (Healthy / Low / Critical)
- Optional second series: internal `battery_pct` on a twin axis
  (toggleable legend — keep default = external only)

### 4.2 Fence Fault timeline — `FENCE_FAULT` alerts only
- Horizontal timeline: one bar per FENCE_FAULT alert —
  `triggered_at → resolved_at` (still-open faults render to "now"
  with a pulsing edge)
- **Scope locked**: only `alert_type='FENCE_FAULT'` — the same flag
  that drives the red bulb (battery_pct=100 heuristic). Other fault
  types are not shown here (user decision — may revisit later)
- Duration label on each bar ("12 min")
- Footer: `N faults · total M min` for the range
- Empty state: "No faults in this period" + green shield icon

### 4.3 Machine Activity (start/stop) — **ignition only**
- Gantt-style strip: ON segments where `ignition_on == true`
  (gap-tolerant: consecutive points <10 min apart merge into one
  session — heartbeat gaps shouldn't split a session)
- **Scope locked**: sessions = ignition ON only; movement (speed>0)
  is NOT shown (user decision)
- Command markers (⚡) on the strip at `machine_commands` timestamps,
  colored by ON/OFF — lets the user see manual commands vs. actual
  engine state side by side
- Footer: session count + total ON time for the range

---

## 5. My suggestions — extras worth adding

Ordered by value/effort:

1. **Daily summary header** — at a glance: `ON time 4h12m · 0 faults ·
   Battery 12.9V · last seen 2m ago`. Cheap (already-fetched data),
   high perceived value.
2. ~~**GSM signal sparkline**~~ — **REJECTED by user**: keep only the
   3 requested cards. (Data remains available if revisited later.)
3. **Distance + max speed per session** (activity card) — computable
   from `location_history` GPS points; turns sessions into a mini
   trip log. Slightly heavier query — phase 2.
4. **Tap a fault bar → alert detail** — reuse existing alerts detail;
   drill-down via `context.push`.
7. **Wider fault coverage later** — if wanted, the faults endpoint can
   include other fault-type alerts (EXTERNAL_POWER_CUT, DEVICE_OFFLINE,
   GSM_SIGNAL_LOW…) as differently-colored bars on the same timeline.
   Currently excluded per user decision — Fence Fault only.
5. **"No data" education** — for new devices (<24h old) show "Data
   starts appearing after the device reports" instead of empty axes.
6. **Per-30d downsampling** — the raw series is ~10 pts/hr (fine);
   for 30-day range aggregate server-side to hourly avg/min/max to
   keep the response small.

### Explicitly NOT recommended
- **Real-time updates** — polling/`ref.invalidate` on pull-to-refresh
  is enough; live socket for analytics adds complexity for near-zero
  value (historical view).
- **Chart interactions beyond tap** — pinch-zoom etc. is scope creep;
   range chips cover 99% of use.

---

## 6. Technical notes

| Area | Decision |
|---|---|
| Chart lib | **`fl_chart`** — add dependency (stable, widely used; no chart lib exists today) |
| Backend | Reuse `TelemetryController` for graph 1; new `AnalyticsController` (`faults`, `sessions`) with `machine:read` + tenant guard |
| Sessions logic | Server-side SQL/window fn over `location_history` — keeps client thin, unit-testable |
| Time ranges | `from`/`to` params, server computes; app sends range |
| Localization | New ARB keys en/hi/mr: `navAnalytics`, card titles, statuses, empty states |
| Testing | `AnalyticsServiceTest` (session derivation, fault intervals, tenant isolation); widget tests for all 3 cards |
| Permissions | `telemetry:read` for telemetry; `machine:read` for faults/sessions — customer role already holds both |

---

## 7. Phases

| Phase | Content | Deployable? |
|---|---|---|
| **1** | Backend: `GET /machines/{id}/faults` + `GET /machines/{id}/sessions` + tests | ✅ API-only |
| **2** | Mobile: Analytics tab + machine selector + range chips + Battery Health card | ✅ ships partial screen |
| **3** | Mobile: Fence Fault + Activity timeline cards | ✅ full feature |
| **4** | Extras from §5 (as approved) | ✅ |

Each phase self-contained per rule 20 — stop for verification between.

---

## 8. Decisions — confirmed by user (2026-09-26)

| # | Question | Decision |
|---|---|---|
| 1 | Healthy voltage band | **11.8–14.0 V** |
| 2 | Time ranges | **Chips only**: Today / 7d / 30d (no custom picker) |
| 3 | Sessions | **Ignition ON only** — no movement bar |
| 4 | GSM sparkline | **No** — 3 cards only |
| 5 | Default range | **Today** |
