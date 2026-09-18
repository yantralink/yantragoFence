# Charging / Fence Fault Bulb Widget — Implementation Plan

Status: **DRAFT — awaiting user review**
Created: 2026-09-17
Scope: Mobile app only (data already end-to-end; no backend changes)

---

## 1. Background — what data exists today

The tracker's **internal backup battery** is reported in every **heartbeat
(0x13)** and **alarm (0x26)** packet as a coarse **7-level enum** (the
protocol has no numeric percentage). The gateway already maps it to an
approximate percentage (`BatteryLevelMapper.java`):

| Raw enum | Displayed % | Device meaning |
|---|---|---|
| 0x00 | **0%** | No power (power off) |
| 0x01 | **10%** | Extremely low |
| 0x02 | **20%** | Very low (low battery alarm) |
| 0x03 | **40%** | Low battery (usable) |
| 0x04 | **60%** | Normal |
| 0x05 | **80%** | High |
| 0x06 | **100%** | Extremely high |

This percentage already flows end-to-end:

```
Device (0x13/0x26 Terminal Info → voltage level byte)
  → BatteryLevelMapper.toPercentage
  → TelemetryMessage.battery (RabbitMQ)
  → devices.battery_pct (latest) + battery_readings (history)
  → GET /api/v1/machines/{id}/telemetry/latest (batteryPct)
  → mobile: BatteryWidget (shows "Battery 60% Charging/On battery")
```

Independently, the device reports whether **external power is connected**
(Terminal Info **Bit 2**) — the `charging` field — which the current
Battery tile uses for its "Charging / On battery" status line.

> **Important distinction for review:** `charging` (power plugged in) and
> `batteryPct` (internal battery level) are **two independent signals**.
> The requirement below derives a bulb state from the *battery level*,
> which is a new interpretation on top of existing data.

## 2. Requirement (as stated by product owner)

A new widget showing a **bulb indicator** driven by the internal battery
percentage:

| Battery level | Bulb | Meaning shown |
|---|---|---|
| **20% – 60%** | 🟢 **Green bulb glows** | Status: "**Charging**" |
| **60% – 100%** | 🔴 **Red bulb glows** | Status: "**Fence Fault**" |

So the widget conveys two machine conditions at a glance:
- mid battery (20–60%) → machine is healthy and charging → green bulb
- high battery (60–100%) → fence fault condition → red bulb

## 3. Proposed mapping specification

Because the device only ever sends the 7 discrete values, the widget logic
is a lookup, not a continuous formula:

| batteryPct | Zone | Bulb | Status text | Tone |
|---|---|---|---|---|
| null | unknown | ⚫ off | "No report received" | unavailable state |
| 0, 10 | **DEPLETED** (proposed — not in requirement) | 🔴 red | "Battery Depleted" | danger |
| 20, 40 | **CHARGING** | 🟢 green | "Charging" | success |
| 60, 80, 100 | **FENCE_FAULT** | 🔴 red | "Fence Fault" | danger |

**Boundary decision needed (see §7):** the value **60%** falls in *both*
ranges as written (20–60 and 60–100). Proposal: **60% belongs to Fence
Fault** (i.e. Charging = strictly below 60: {20, 40}), because 60% is the
enum's "Normal" level and the fault range should own the top of the scale.

## 4. Widget design

- New file `mobile/lib/features/dashboard/widgets/charging_fault_widget.dart`
  following the `AppMetricCard` pattern used by every other tile:
  - **Icon:** `lightbulb` — filled/glowing green (`StatusTone.success`) for
    CHARGING, red (`StatusTone.danger`) for FENCE_FAULT, grey for unknown.
  - **Label:** "Fence Status" (proposal — see naming alternatives in §7).
  - **Value line:** the battery percentage (e.g. "40%").
  - **Status line:** "Charging" / "Fence Fault" / "No report received".
- Tile added to `MachineTelemetryGrid._buildTiles` next to the Battery tile
  (grid becomes 7 tiles; the `Wrap` layout handles it without changes).
- The **existing Battery tile stays** — it shows raw level + real charging
  flag; the new bulb widget is the operator-facing interpretation.

## 5. Implementation phases

### Phase A1 — Zone mapper + unit tests
- New pure function(s) in `mobile/lib/` (e.g.
  `features/dashboard/widgets/fence_status_mapper.dart`):
  - `FenceStatus? fenceStatusFromBatteryPct(int? pct)` →
    `depleted | charging | fenceFault | null(unknown)`
- Unit tests covering all 7 enum values + null + out-of-range values.

### Phase A2 — Widget + grid wiring
- `charging_fault_widget.dart` per §4.
- Insert tile into `MachineTelemetryGrid` (7 tiles).
- Update `machine_detail_page_test.dart` (tile-count / text assertions change
  again, same as the Ignition/External-Battery changes).

### Phase A3 — Verify
- `flutter analyze`, full `flutter test`.
- Manual QA via the `simulator` module (heartbeats with each enum level).

**Estimated footprint:** 2 new files, 1 modified, 2 test files. No backend,
no migration, no API change. (Data is already in the mobile models:
`Telemetry.battery`, `Machine.batteryPct`.)

## 6. Interaction with the External Battery plan

This plan is independent of the External Battery widget plan
(`EXTERNAL_BATTERY_WIDGET_PLAN.md`) — different battery (internal backup vs
machine bank), different data source. If both are approved, the details grid
will show 7 tiles:

```
Ignition · External Battery · Battery · Fence Status · Faults · GSM · Voltage
```

## 7. Open questions for review (decisions needed)

1. **⚠️ The logic looks inverted — please confirm.** As written, *more*
   battery = *fault* (60–100% → red "Fence Fault") and *less* battery =
   *healthy/charging* (20–60% → green). That's unusual: normally low battery
   is the fault condition. Possible explanations we considered:
   - (a) On these solar fence energizers, a full internal battery while the
     fence is energized genuinely indicates a fault condition (e.g. the
     fence circuit is drawing no load / is broken / is disconnected, so the
     battery is not discharging) — i.e. red bulb is *correct*.
   - (b) The ranges were swapped by mistake and the intent is
     60–100% → green "Charging/OK", 20–60% → red "Fence Fault".
   - (c) The intent is to mirror a physical bulb on the machine itself
     (the device's own indicator lamp behavior), whatever its logic is.
   **Please confirm which is intended before implementation.**
2. **Boundary at exactly 60%** — Charging range is 20–60 and Fault range is
   60–100, so 60 appears in both. Proposal: 60 → **Fence Fault**. Confirm.
3. **0–20% (values 0 and 10)** — not covered by the requirement. Proposal:
   red bulb, "Battery Depleted" (a dead battery is certainly not "charging").
   Confirm, or specify different behavior.
4. **Should the real `charging` flag play any role?** The device has a
   separate "external power connected" bit. Options:
   - (a) Ignore it — bulb depends purely on battery level (as stated).
   - (b) Show "Charging (external power)" as a suffix when the flag is true,
     so operators can distinguish "charging by solar" from "plugged in".
5. **Widget naming** — proposal: label "Fence Status". Alternatives:
   "Charging / Fence Fault", "Machine Status", "Bulb Indicator".

## 8. My suggestions

1. **Resolve §7.1 first — everything else is mechanical.** If the ranges are
   actually swapped (§7.1b), implementing as-written would show a healthy
   machine as faulty and a faulty one as healthy — the worst possible UX for
   a safety-ish indicator.
2. **Show the battery % on the tile** even though the bulb is the headline —
   operators will ask "why red?" and the number answers it.
3. **Make Fence Fault an alert later.** A persistent fence fault is exactly
   what the existing alert/notification engine (`AlertGenerationService`,
   BR05 alarm mapping in `DeviceEventConsumer`) is for — a future phase
   could push-notify on fence-fault entry instead of requiring the user to
   open the screen. Not in this plan's scope; noted for the roadmap.
4. **Keep the existing Battery tile.** The bulb is an interpretation; the
   Battery tile remains the raw source of truth. If screen space becomes a
   concern we can merge them later, but premature merging loses information.
5. **Name the zones after machine meaning, not battery meaning.** "Charging"
   and "Fence Fault" describe the machine; keeping "Battery 40%" on the
   separate tile keeps evidence separate from conclusion.

---

**Review checklist for product owner:**
- [ ] Confirm the range logic is NOT inverted (§7.1) — or approve as-written
- [ ] Decide boundary ownership of 60% (§7.2)
- [ ] Decide behavior for 0–20% (§7.3)
- [ ] Decide whether the external-power charging flag is shown (§7.4)
- [ ] Approve widget label "Fence Status" (§7.5)
- [ ] Approve mobile-only scope, tile order (§6)
