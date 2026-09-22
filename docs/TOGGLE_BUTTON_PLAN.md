# Machine Details — Replace Turn ON / Turn OFF Buttons with Toggle Switch

## 1. Overview

Replace the two separate "Turn ON" (green) and "Turn OFF" (red) buttons on the
Machine Details screen with a single toggle switch that reflects the machine's
current fencing state and sends the appropriate command when toggled.

---

## 2. Current State

### 2.1 What exists today

| Component | File | Role |
|-----------|------|------|
| **OnOffButton** | `mobile/lib/features/machines/widgets/on_off_button.dart` | Two `AppActionButton` widgets in a `Row` — "Turn ON" (green, sends `FENCING_ON`) and "Turn OFF" (red, sends `FENCING_OFF`). Both disabled while `_sending` is true. |
| **MachineDetailPage** | `mobile/lib/features/machines/pages/machine_detail_page.dart` | Hosts `OnOffButton(machineId, imei)` between the telemetry grid and the location card. |
| **CommandProvider** | `mobile/lib/features/commands/providers/command_provider.dart` | `CommandNotifier.sendCommand(machineId, imei, commandType)` → POST `/api/v1/commands`. Tracks `CommandState(lastCommand, pending)`. |
| **CommandSocketProvider** | `mobile/lib/features/commands/providers/command_socket_provider.dart` | WebSocket `/topic/command/{machineId}` — pushes `CommandStatusUpdate` (PENDING → ACK → DONE/FAILED/TIMEOUT). |
| **TelemetrySocketProvider** | `mobile/lib/features/machines/providers/telemetry_socket_provider.dart` | WebSocket `/topic/telemetry/{machineId}` — pushes live telemetry including `ignitionOn` (ACC/engine state). |
| **MachineDetailProvider** | `mobile/lib/features/machines/providers/machine_provider.dart` | `FutureProvider.family` — REST `GET /api/v1/machines/{id}`. Does NOT auto-refresh when a command completes. |
| **Machine model** | `mobile/lib/models/machine.dart` | `status` field: `FENCING_ON`, `OFFLINE`, `FAULT`, `IN_STOCK`. `isFencingOn` getter: `status == 'FENCING_ON'`. `ignitionOn` field: ACC/engine state (separate from fencing). |
| **CommandStatusWidget** | `mobile/lib/features/commands/widgets/command_status_widget.dart` | Shows last command status (pending, acked, done, failed). Currently NOT shown on the detail page. |
| **AppActionButton** | `mobile/lib/core/widgets/app_action_button.dart` | Shared button widget with `on`/`off` styles. Will remain for other screens. |

### 2.2 Key distinction: `ignitionOn` vs `isFencingOn`

| Field | Meaning | Source | Updates via |
|-------|---------|--------|-------------|
| `machine.ignitionOn` | ACC / engine on-off | Device telemetry (BR05 ACC byte) | Telemetry WebSocket (live) |
| `machine.isFencingOn` | Relay / fencing on-off | Machine `status` == `FENCING_ON` | REST API only (no live push) |

**The toggle reflects `isFencingOn` (relay/fencing state), NOT `ignitionOn` (engine state).**
The "Machine Running" / "Machine Stopped" chip in the info card already shows
`ignitionOn` — that stays unchanged.

### 2.3 Command lifecycle

```
User taps button
  → POST /api/v1/commands { commandType: "FENCING_ON" | "FENCING_OFF" }
  → Command created (status: PENDING/QUEUED)
  → Gateway sends to device via TCP
  → Device ACKs → status: ACK
  → Device executes → status: DONE (success) or FAILED/TIMEOUT
  → WebSocket /topic/command/{machineId} pushes each transition
  → CommandProvider.applyStatusUpdate() updates state
```

Per AGENTS.md rule 5: **never assume a command succeeded until ACK is received.**
The toggle must not flip to the new state until the command reaches DONE.

---

## 3. Impact Assessment

### 3.1 What changes (mobile only — no backend changes)

| Area | Impact | Details |
|------|--------|---------|
| **Backend** | **None** | The API contract (`POST /api/v1/commands` with `FENCING_ON`/`FENCING_OFF`) is unchanged. Command lifecycle, WebSocket topics, and machine status updates all remain the same. |
| **`on_off_button.dart`** | **Replaced** | New `fencing_toggle.dart` widget replaces it. The old file is deleted. |
| **`machine_detail_page.dart`** | **Small edit** | Swap `OnOffButton` import/widget for `FencingToggle`. Pass `machine` (for `isFencingOn`) in addition to `machineId`/`imei`. |
| **`command_provider.dart`** | **No change** | `sendCommand()` and `applyStatusUpdate()` are reused as-is. |
| **`command_socket_provider.dart`** | **No change** | WebSocket subscription is reused as-is. |
| **`machine_provider.dart`** | **No change** | `machineDetailProvider` is invalidated by the toggle when a command reaches DONE (via `ref.invalidate`), which it already supports. |
| **Localization** | **Add strings** | New strings for toggle labels ("Fencing On", "Fencing Off", "Turning On…", "Turning Off…", "Command failed — try again"). Old `actionTurnOn`/`actionTurnOff` strings are kept (used nowhere else, but removing them is a separate cleanup). |
| **Tests** | **Update** | `machine_detail_page_test.dart` test "ON/OFF buttons are present and tappable" needs updating to find the toggle. New widget tests for the toggle itself. |
| **`AppActionButton`** | **No change** | The `on`/`off` styles remain for potential reuse elsewhere. |

### 3.2 Risk assessment

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Toggle shows wrong state after command DONE (stale REST data) | Medium | Invalidate `machineDetailProvider` when command reaches DONE — the REST refetch updates `machine.status`. |
| User taps toggle rapidly (double-send) | Low | Disable the toggle while `commandProvider.pending` is true — same pattern as current `_sending` flag. |
| Command fails / times out — toggle stuck in loading | Medium | On FAILED/TIMEOUT, re-enable the toggle and show the original state + error snackbar. The toggle reverts to the machine's actual `isFencingOn` state. |
| Machine offline — user can still send command | Low (existing behavior) | The toggle is enabled regardless of online status (same as current buttons). The backend will queue the command and it will fail/timeout if the device is offline. No change needed. |

---

## 4. Toggle UX Specification

### 4.1 States

| State | Toggle visual | Label | Action |
|-------|--------------|-------|--------|
| **Fencing ON** (confirmed) | Green, thumb on right | "Fencing On" | Tap → send `FENCING_OFF` |
| **Fencing OFF** (confirmed) | Grey/red, thumb on left | "Fencing Off" | Tap → send `FENCING_ON` |
| **Turning On…** (command pending) | Amber, loading spinner, disabled | "Turning On…" | Disabled |
| **Turning Off…** (command pending) | Amber, loading spinner, disabled | "Turning Off…" | Disabled |
| **Command Failed** | Reverts to actual state, red border pulse | "Fencing On/Off" + "Command failed — try again" subtext | Tap to retry |
| **Unknown** (status not FENCING_ON/OFF/FAULT/IN_STOCK) | Grey, disabled | "Unavailable" | Disabled |

### 4.2 State derivation logic

```
effectiveState:
  if commandProvider.pending:
    if lastCommand.commandType == "FENCING_ON":  → TURNING_ON
    if lastCommand.commandType == "FENCING_OFF": → TURNING_OFF
  else if lastCommand.isFailed or lastCommand.isTimeout:
    → FAILED (revert to machine.isFencingOn for visual)
  else if machine.isFencingOn:
    → ON
  else:
    → OFF
```

### 4.3 Post-command refresh

When `commandProvider` transitions to `DONE`:
1. Show success snackbar ("Fencing turned on" / "Fencing turned off")
2. `ref.invalidate(machineDetailProvider(machineId))` — refetch machine from REST
3. Toggle settles to the confirmed state once the refetched machine arrives

When `commandProvider` transitions to `FAILED` or `TIMEOUT`:
1. Show error snackbar ("Command failed. Please try again.")
2. Toggle reverts to the machine's actual `isFencingOn` state
3. Toggle is re-enabled for retry

---

## 5. Implementation Phases

### Phase 1 — Create the FencingToggle widget

**Files:**
- `mobile/lib/features/machines/widgets/fencing_toggle.dart` (new)

**Prompt:**
> Create a new `FencingToggle` widget in `mobile/lib/features/machines/widgets/fencing_toggle.dart`.
> It is a `ConsumerStatefulWidget` that takes `machineId`, `imei`, and `isFencingOn` (bool).
>
> It watches `commandProvider` to determine the toggle state:
> - If `commandProvider.pending` is true, show a loading state with the appropriate label ("Turning On…" / "Turning Off…") based on `lastCommand.commandType`.
> - If the last command for this machine is `FAILED` or `TIMEOUT`, show the error state — revert to `isFencingOn` and show "Command failed — try again" subtext.
> - Otherwise, show the confirmed state based on `isFencingOn`.
>
> Tapping the toggle calls `commandProvider.notifier.sendCommand()` with `FENCING_ON` if currently off, or `FENCING_OFF` if currently on. Show a snackbar after sending ("Command sent. Waiting for device acknowledgement…").
> On error, show an error snackbar.
>
> Use a Material `Switch` widget styled with the app's semantic colors (green for ON, grey for OFF, amber for pending). Include a label row beside the switch showing the current state text. Disable the switch while a command is pending.
>
> When the command transitions to DONE, call `ref.invalidate(machineDetailProvider(machineId))` to refresh the machine status. When it transitions to FAILED/TIMEOUT, show an error snackbar and re-enable the toggle.
>
> Per AGENTS.md rule 5: never assume the command succeeded until DONE. The toggle must not flip to the new state until the command reaches DONE and the machine detail is refreshed.

### Phase 2 — Wire the toggle into MachineDetailPage

**Files:**
- `mobile/lib/features/machines/pages/machine_detail_page.dart` (edit)

**Prompt:**
> In `machine_detail_page.dart`, replace the `OnOffButton` widget with the new `FencingToggle`.
> Remove the import of `on_off_button.dart` and add the import for `fencing_toggle.dart`.
> Pass `machineId: m.id`, `imei: m.imei ?? ''`, and `isFencingOn: m.isFencingOn` to the `FencingToggle`.
> Keep the same position in the layout (after the telemetry grid, before the location card).
> Keep the "Machine state changes only after device confirmation." footnote below the location card.

### Phase 3 — Add localization strings

**Files:**
- `mobile/lib/l10n/app_en.arb` (edit)
- `mobile/lib/l10n/app_hi.arb` (edit)
- `mobile/lib/l10n/app_mr.arb` (edit)
- Regenerate via `flutter gen-l10n`

**Prompt:**
> Add the following new localization strings to all three ARB files (en, hi, mr):
> - `fencingOn`: "Fencing On" / "फेंसिंग ऑन" / "फेन्सिंग ऑन"
> - `fencingOff`: "Fencing Off" / "फेंसिंग ऑफ" / "फेन्सिंग ऑफ"
> - `turningOn`: "Turning On…" / "चालू हो रहा है…" / "चालू होत आहे…"
> - `turningOff`: "Turning Off…" / "बंद हो रहा है…" / "बंद होत आहे…"
> - `fencingTurnedOn`: "Fencing turned on" / "फेंसिंग चालू हो गई" / "फेन्सिंग चालू झाली"
> - `fencingTurnedOff`: "Fencing turned off" / "फेंसिंग बंद हो गई" / "फेन्सिंग बंद झाली"
> - `commandFailedRetry`: "Command failed. Please try again." / "कमांड विफल। कृपया पुनः प्रयास करें।" / "कमांड अयशस्वी. कृपया पुन्हा प्रयत्न करा."
> - `fencingUnavailable`: "Unavailable" / "अनुपलब्ध" / "अनुपलब्ध"
>
> Run `flutter gen-l10n` to regenerate the Dart localization files.

### Phase 4 — Delete old OnOffButton and update tests

**Files:**
- `mobile/lib/features/machines/widgets/on_off_button.dart` (delete)
- `mobile/test/features/machine_detail_page_test.dart` (edit)

**Prompt:**
> Delete `mobile/lib/features/machines/widgets/on_off_button.dart` — it is no longer used.
>
> In `mobile/test/features/machine_detail_page_test.dart`, update the test "ON/OFF buttons are present and tappable":
> - Replace `find.text('Turn ON')` and `find.text('Turn OFF')` with `find.text('Fencing Off')` (the default state for an offline machine).
> - Verify the toggle switch widget is present (`find.byType(Switch)`).
> - Verify tapping the switch triggers the command (the fake command notifier's `sendCommand` is called).
>
> Add a new test: "toggle shows 'Turning On…' when command is pending" — set the fake command notifier to pending state with `lastCommand.commandType = 'FENCING_ON'` and verify the label shows "Turning On…".
>
> Add a new test: "toggle shows 'Fencing On' when machine status is FENCING_ON" — use a machine with `status: 'FENCING_ON'` and verify the label shows "Fencing On".

### Phase 5 — Verify and build

**Prompt:**
> Run `flutter analyze` on all changed files. Run `flutter test test/features/machine_detail_page_test.dart`. Fix any issues. Run `flutter test` for the full test suite to confirm no regressions.

---

## 6. Dummy Screen Mockup

A standalone Flutter widget that renders the Machine Details screen with the
toggle switch is in:

`mobile/lib/features/machines/widgets/fencing_toggle_mockup.dart`

Run it with:
```bash
cd mobile && flutter run lib/features/machines/widgets/fencing_toggle_mockup.dart
```

---

## 7. Files Summary

| File | Action | Phase |
|------|--------|-------|
| `mobile/lib/features/machines/widgets/fencing_toggle.dart` | **Create** | 1 |
| `mobile/lib/features/machines/pages/machine_detail_page.dart` | **Edit** | 2 |
| `mobile/lib/l10n/app_en.arb` | **Edit** | 3 |
| `mobile/lib/l10n/app_hi.arb` | **Edit** | 3 |
| `mobile/lib/l10n/app_mr.arb` | **Edit** | 3 |
| `mobile/lib/l10n/generated/*` | **Regenerate** | 3 |
| `mobile/lib/features/machines/widgets/on_off_button.dart` | **Delete** | 4 |
| `mobile/test/features/machine_detail_page_test.dart` | **Edit** | 4 |
| `mobile/lib/features/machines/widgets/fencing_toggle_mockup.dart` | **Create** | (with this doc) |

**No backend changes. No new dependencies. No database migrations.**
