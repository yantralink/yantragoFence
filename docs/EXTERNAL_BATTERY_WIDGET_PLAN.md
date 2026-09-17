# External Battery Widget — Implementation Plan

Status: **DRAFT — awaiting user review**
Created: 2026-09-17
Scope: Mobile app (primary), optional backend follow-up

---

## 1. Background

The BR05 tracker reports the **external power supply voltage** (the machine's
battery/alternator voltage) via the **0x94 Information Transmission packet**
(information type 0x00). This value already flows end-to-end:

```
Device (0x94 packet)
  → ConcoxV5ProtocolHandler.handleInfoPacket (parses volts, e.g. 12.22V)
  → GpsIngestService.forwardVoltage
  → TelemetryMessage.voltage (RabbitMQ)
  → TelemetryConsumer → devices.voltage (latest) + voltage_readings (history)
  → GET /api/v1/machines/{id}/telemetry/latest  (TelemetryLatestDto.voltage)
  → mobile MachineTelemetryGrid → VoltageWidget (shows raw volts today)
```

Today the user sees a **Voltage** tile with the raw reading ("12.22 V").
Raw volts are meaningless to most operators — they cannot tell whether
12.0 V means "fine" or "about to die".

> **Note:** this is a *different* battery from the existing "Battery" tile.
> The existing tile shows the tracker's **internal backup battery**, which the
> device reports only as a coarse 7-level enum (`BatteryLevelMapper`).
> The External Battery tile is derived from the **machine's actual battery
> voltage** and can show a real percentage. Both tiles will coexist.

## 2. Requirement (as stated by product owner)

Derive a battery percentage from the external voltage and display it in a new
**External Battery** widget:

| Voltage reading | Displayed as |
|---|---|
| ≥ 13.5 V | **100%** — "Battery full" |
| 10.5 V (critical zone) | **0–15%** — "Critical Low Battery" |
| between 10.5 V and 13.5 V | **Interpolated percentage** between the zones |
| charging flag present | Show "Battery is charging" indication |

## 3. Mapping specification (proposed)

### 3.1 Percentage formula

Linear interpolation across the defined window, clamped at both ends:

```
pct = (voltage - 10.5) / (13.5 - 10.5) * 100
pct = clamp(pct, 0, 100)
```

| Voltage | Computed % | Zone | Shown label |
|---|---|---|---|
| < 10.5 V (e.g. 9.8) | clamped 0% | CRITICAL | "Critical Low Battery" |
| exactly 10.5 V | 0% | CRITICAL | "Critical Low Battery" |
| 10.95 V | 15% | LOW | "Low Battery" |
| 12.0 V | 50% | NORMAL | "Battery level" |
| 13.05 V | 85% | NORMAL | "Battery level" |
| ≥ 13.5 V | clamped 100% | FULL | "Battery Full" |

**Zone boundaries (display labels only — the % stays continuous):**

- `pct < 15` → **Critical** (red / danger tone)
- `15 ≤ pct < 30` → **Low** (orange / warning tone)
- `30 ≤ pct < 95` → **Normal** (green when charging, neutral otherwise)
- `pct ≥ 95` → **Full** (green / success tone)

### 3.2 Charging indicator

The device reports external-power connection via **Terminal Info Bit 2**
(`charging` field, already end-to-end). Display rule:

- `charging == true` → status line "**Charging**" (green, charging icon),
  regardless of zone.
- `charging == false` → zone label ("Critical Low Battery" / "Low Battery" /
  "Battery level" / "Battery Full").
- `charging == null` → fall back to the zone label.

### 3.3 Unavailable state

`voltage == null` (no 0x94 packet received yet, or device model doesn't send
one) → tile shows **unavailable**: "No voltage report". Never fabricate 0% —
consistent with the app's "do not fabricate readings" design rule.

### 3.4 Implausible values (proposed guard rails)

- `voltage <= 0` → treat as **unavailable** (bad sensor frame, not a real
  reading).
- `voltage > 15.0` → clamp display to 100% but keep the raw voltage visible
  in the Voltage tile. (28 V systems would read ~27–28 V; see §7.3 — open
  question.)

## 4. Where the calculation lives — options

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A. Mobile-only (recommended)** | Pure Dart function `ExternalBatteryMapper.voltageToPercent(double? v)` in `mobile/lib/core/` or dashboard widgets | Smallest change; no API/DB impact; ships in one phase | Admin-web and future reports would need to reimplement |
| **B. Backend derived field** | Add `externalBatteryPct` to `TelemetryLatestDto`/`MachineDto`, computed in `MachineService` from `devices.voltage` | Single source of truth; admin-web can reuse; matches how battery% would be queried in reports | API change + backend deploy needed; DTO grows |
| **C. Persisted column** | Store pct in `devices` at ingest time | Queryable/sortable, works in dashboards | Schema change for a derived value — over-engineering today (violates YAGNI) |

**Recommendation: Option A now**, with the formula isolated in one small
well-tested class so it can be lifted to the backend later (Option B) when
admin-web or reports need it.

## 5. Implementation plan (Option A)

### Phase A1 — Mapper + unit tests (core logic)

- New file `mobile/lib/features/dashboard/widgets/external_battery_mapper.dart`
  (or `mobile/lib/core/utils/`): pure function(s):
  - `int? voltageToExternalBatteryPct(double? voltage)` — clamped linear
    interpolation per §3.1, returns null for null/≤0 input.
  - `BatteryZone externalBatteryZone(int pct)` — enum
    `{ critical, low, normal, full }` per §3.1 thresholds.
- Unit tests `mobile/test/unit/external_battery_mapper_test.dart`:
  - boundaries: 10.4, 10.5, 10.95, 12.0, 13.5, 13.6, 0, null
  - midpoint correctness (12.0 → 50)
  - rounding rule (floor to int)

### Phase A2 — Widget

- New file `mobile/lib/features/dashboard/widgets/external_battery_widget.dart`
  mirroring `battery_widget.dart` / `ignition_widget.dart` patterns:
  - inputs: `double? voltage`, `bool? charging`
  - unavailable state per §3.3
  - icon: `battery_charging_full` when charging, else `battery_std` / `battery_alert` for critical zone
  - value: interpolated `%`, status line per §3.2
  - tone: zone color per §3.1 (danger / warning / neutral / success)
- Add the tile to `MachineTelemetryGrid._buildTiles` (grid becomes 6 tiles —
  layout already wraps via `Wrap`, no grid surgery needed).

### Phase A3 — Tests & verification

- Update `machine_detail_page_test.dart` grid test (tile count / unavailable
  texts change again, same as the Ignition change).
- `flutter analyze` + full `flutter test`.
- Manual QA with the simulator (`simulator` module) sending 0x94 packets at
  boundary voltages.

**Estimated footprint:** 2 new files, 1 modified file (grid), 2 test files.
No backend, no migration, no API change.

### Optional Phase B (later, only if needed)

Mirror the same formula in `MachineService` and expose
`externalBatteryPct` on `TelemetryLatestDto`/`MachineDto` when admin-web or
reports need battery percentage. Lift the Dart tests as JUnit tests.

## 6. Relationship to existing widgets

- **Voltage tile stays** — operators and technicians still need raw volts for
  diagnostics. The External Battery tile sits next to it (human-readable form
  of the same signal).
- **Battery tile (internal) stays** — different physical battery (tracker
  backup), different data source (7-level enum).
- Tile order proposal (6 tiles): Ignition · External Battery · Battery ·
  Faults · GSM · Voltage — the two "battery-ish" tiles adjacent so the
  internal/external distinction is visually obvious.

## 7. Open questions for review (decisions needed)

1. **Value at exactly 10.5 V** — formula above gives 0%. Your requirement
   said "0% to 15%" for the critical zone. Alternatives:
   - (a) 0% at 10.5 V, critical label below 15% (proposed — simple, matches
     "linear in between")
   - (b) Map 10.5 V → 15% and interpolate 15→100 across the window
   - (c) A dedicated sub-window, e.g. 10.5–11.0 V maps to 0–15%
2. **Below 10.5 V** — clamp to 0% and show "Critical Low Battery" (proposed).
   Real 12 V lead-acid at 10.5 V is fully discharged; lower means deep
   discharge damage territory.
3. **24 V / 36 V systems** — if any machines use 24 V battery banks, the
   10.5/13.5 window is wrong for them (they'd always read 100%). Proposal:
   keep 12 V-only now, add a per-machine `batteryBankVoltage` attribute later
   if such machines exist. **Do you have 24 V machines?**
4. **"Full" semantics while engine is running** — with the engine on, the
   alternator pushes 13.8–14.4 V, so the widget will legitimately read 100%
   + "Charging". When the engine is off, the resting voltage reflects the
   true state of charge. This is physically correct behavior — confirming
   you're happy with it (the charging badge disambiguates).
5. **Rounding** — floor vs round for the integer display (proposal: floor,
   so we never overstate charge).

## 8. My additional suggestions

1. **Keep the raw Voltage tile.** Removing it would hide the one signal
   technicians use to diagnose charging-system faults (e.g. alternator
   output stuck at 12.4 V while running = broken alternator that the %
   widget alone would mask as ~63%).
2. **Consider a piecewise (non-linear) curve later.** A real 12 V lead-acid
   battery's state-of-charge vs voltage is not linear: 12.0 V resting ≈ 50%,
   but 11.8 V ≈ 25% — voltage falls off a cliff near empty. The linear
   window is fine for v1; if operators report "%" feels wrong at the low
   end, switch the mapper to a lookup curve
   (10.5→0, 11.6→25, 12.1→50, 12.5→80, 12.7→100) with no other code changes.
3. **Connect it to the alert engine later.** We already have
   `AlertGenerationService` with telemetry rule evaluation. A future
   "External battery low" alert type (e.g. pct < 20 for 10 minutes) would
   make this proactive instead of dashboard-only. Separate feature — not in
   this plan's scope.
4. **Don't reuse the internal-battery thresholds.** The internal battery tile
   uses >60/>20 thresholds on a coarse enum; the external widget has precise
   voltage — keep the zone thresholds independent as specified.
5. **Persist nothing.** The % is derived; the raw voltage is already in
   `voltage_readings` history. If reports need historical %, derive it at
   query time from stored volts (Option B later) — never store a value you
   can compute.

---

**Review checklist for product owner:**
- [ ] Approve formula / zone table (§3.1) or pick alternative in §7.1
- [ ] Confirm no 24 V machines exist (§7.3)
- [ ] Confirm charging-badge behavior while engine running (§7.4)
- [ ] Approve tile order (§6)
- [ ] Approve Option A (mobile-only, no backend change)
