# Charging / Fence Fault Bulb Widgets — Implemented Specification

Status: **IMPLEMENTED** (mobile bulbs + backend notifications)
Revised: 2026-11-25 — replaces the earlier draft range logic and the
interim exact-value (10% / 100%) logic.

---

## 1. Background — what data exists

The tracker's **internal backup battery** is reported in every **heartbeat
(0x13)** and **alarm (0x26)** packet as a coarse **7-level enum** (the
protocol has no numeric percentage). The gateway maps it to an approximate
percentage (`BatteryLevelMapper.java`):

| Raw enum | Displayed % | Device meaning |
|---|---|---|
| 0x00 | **0%** | No power (power off) |
| 0x01 | **10%** | Extremely low |
| 0x02 | **20%** | Very low (low battery alarm) |
| 0x03 | **40%** | Low battery (usable) |
| 0x04 | **60%** | Normal |
| 0x05 | **80%** | High |
| 0x06 | **100%** | Extremely high |

This percentage flows end-to-end:

```
Device (0x13/0x26 Terminal Info → voltage level byte)
  → BatteryLevelMapper.toPercentage
  → TelemetryMessage.battery (RabbitMQ)
  → devices.battery_pct (latest) + battery_readings (history)
  → GET /api/v1/machines/{id}/telemetry/latest (batteryPct)
  → mobile: BatteryWidget + FaultsWidget (bulb tiles)
```

Independently, the device reports whether **external power is connected**
(Terminal Info **Bit 2**) — the `charging` field — which is informational
only and does **not** drive either bulb.

> **Important distinction:** `charging` (power plugged in) and
> `batteryPct` (internal battery level) are **two independent signals**.
> Both bulbs derive solely from `batteryPct`.

## 2. Final bulb mapping (as implemented)

| Internal battery | Charging Status bulb | Fence Fault bulb | Notification |
|---|---|---|---|
| **0%** | 🟢 ON | 🔴 ON | FENCE_FAULT (WARNING) |
| 10% | off | off | none |
| **20% / 40% / 60%** | off | 🔴 ON | FENCE_FAULT (WARNING) |
| **80%** | 🟢 ON | off | none |
| **100%** | off | off | none |
| null (no report) | unavailable | unavailable | none |

Notes:

- **Both bulbs glow at 0%** — confirmed by product owner.
- **100% shows nothing** — the earlier "100% = fault" interpretation was
  reversed by product decision.
- The mapping is **not monotonic**: fault bulb is ON at low/mid levels
  (0, 20, 40, 60) and OFF at 80 and 100.

## 3. Where the logic lives

| Layer | File | Logic |
|---|---|---|
| Mobile — Charging bulb | `mobile/lib/features/dashboard/widgets/battery_widget.dart` | ON when `battery == 0 \|\| battery == 80` |
| Mobile — Fault bulb | `mobile/lib/features/dashboard/widgets/faults_widget.dart` | ON when `batteryPct ∈ {0, 20, 40, 60}` |
| Backend — alerts | `backend/.../service/BatteryStateAlertService.java` | FAULT state = `{0, 20, 40, 60}` → `FENCE_FAULT` (WARNING); all other values NORMAL |

## 4. Alert / notification semantics (backend)

- **Notifications fire only when the Fence Fault bulb is ON** — i.e.
  entering battery ∈ {0, 20, 40, 60}.
- **MACHINE_CHARGING is never raised** by this service. The charging bulb
  is display-only; stale MACHINE_CHARGING incidents are resolved silently
  on transitions as cleanup.
- **Transition-based, deduplicated:** repeated readings at the same state
  do nothing; the first-ever reading establishes a silent baseline; null
  readings (GPS frames) are ignored; resolves are always silent
  (`resolveIncidentSilently`).

## 5. Tests

- `BatteryStateAlertServiceTest` — covers baseline, dedup, fault entry at
  0/20/40/60, silent resolves, 10/80/100 staying silent, re-entry.
- `machine_list_dashboard_test.dart` — widget tests for all bulb levels.
- `machine_detail_page_test.dart` — REST-vs-live precedence tests use
  battery 40 (fault) vs 80 (charging) under the new thresholds.
