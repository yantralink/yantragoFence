# Automatic Electric Fence Machine ON/OFF Scheduling — Implementation Document

Status: **APPROVED DESIGN — ready for phase-wise implementation**
Created: 2026-10-26
Supersedes: `docs/SCHEDULED_IGNITION_PLAN.md` (draft, 2026-09-25)
Scope: Database + Backend API + Scheduler Engine + Notifications + Mobile UI

---

## 1. Feature Review & Feasibility

### 1.1 What exists today (verified against the codebase)

The complete manual ON/OFF pipeline already exists and is reused unchanged:

```
User tap (Turn ON / Turn OFF)                       mobile: OnOffButton → commandProvider
  → POST /api/v1/commands                           CommandController (@PreAuthorize command:write)
  → CommandService.createCommand(...)               MachineAccessPolicy + TenantGuard + audit row
  → machine_commands (PENDING)                      rule 6: auditable
  → afterCommit → CommandProducer.sendCommand(...)  RabbitMQ COMMAND_EXCHANGE (rule 4)
  → device-gateway → TCP frame → BR05 relay
  → CommandResultConsumer                           QUEUED/SENT/ACK/DONE/FAILED/TIMEOUT
  → CommandStateMachine transitions + command_attempts
  → CommandBroadcastService (WebSocket) + event_outbox → notification pipeline (V36 templates)
```

Key existing components:

| Component | Location |
|---|---|
| Command API | `backend/.../controller/CommandController.java` |
| Command lifecycle | `backend/.../service/CommandService.java`, `CommandStateMachine.java` |
| RabbitMQ producer/consumer | `backend/.../queue/CommandProducer.java`, `CommandResultConsumer.java` |
| Audit tables | `machine_commands`, `command_attempts` (V8) |
| Notification pipeline | `event_outbox` → `OutboxPublisher` → `NotificationEventConsumer` → inbox + FCM push; templates en/hi/mr (V36, V48, V52) |
| Existing schedulers | `OfflineDetectionScheduler`, `PushDeliveryScheduler`, `ExpiryDetectionScheduler`, `OutboxPublisher` — `@EnableScheduling` already active |
| Mobile machine detail | `mobile/lib/features/machines/pages/machine_detail_page.dart` + `widgets/on_off_button.dart` |
| Mobile command provider | `mobile/lib/features/commands/providers/command_provider.dart` (+ `command_socket_provider.dart` for live status) |

### 1.2 Scheduling mechanism decision

**Backend server-side scheduler. Not device-side, not Flutter.**

- The BR05 command set (`docs/BR05_COMMANDS_EXTRACTED.md`) has **no hardware
  time-schedule / timer-relay command** — device-supported scheduling is not
  available. The device clock is irrelevant; the server decides when to fire.
- Flutter background timers are explicitly ruled out (requirement #5) — they
  do not run when the app is killed or the phone is offline.
- The backend already runs multiple `@Scheduled` jobs, so a new scheduler
  follows the established pattern. No new infrastructure (no Quartz, no
  microservice — rule 11).

Scheduled execution is **only a new trigger** in front of the existing audited
command pipeline. No gateway or TCP protocol changes (rules 13, 16).

### 1.3 Confirmed design decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Schedules per machine | **One** active schedule per machine (unique index). Extensible later. |
| 2 | Cross-midnight semantics | **Cycle semantics**: `days_of_week` applies to the **ON** event. The paired OFF fires at the next occurrence of `off_time` strictly after the ON instant — next calendar day when `off_time <= on_time`. ON Mon 18:00 / OFF 07:00 → OFF fires **Tuesday** 07:00 even if Tuesday is unselected. |
| 3 | Timezone | Per-schedule `timezone` column (IANA ID), default `Asia/Kolkata`. All slot math uses `ZoneId`/`ZonedDateTime`; DST-safe. Server/DB stay UTC. |
| 4 | Offline device at fire time | **Scheduler-owned retry.** VERIFIED: the gateway (`CommandDispatchService`) fails immediately when the device is offline and has NO retry loop; `max_attempts` is stored but unused. The scheduler re-dispatches a failed run every `schedule.retry-interval-minutes` (default 5) up to 3 attempts within the cycle — each retry is a NEW `machine_commands` row linked to the same `schedule_runs` row (full audit). After the final failure → run FAILED + failure push. |
| 4b | Stuck/lost results | VERIFIED: there is no backend command-timeout sweeper — only the mobile UI times out visually. The scheduler tick includes a reconciliation sweep: runs in DISPATCHED longer than `schedule.dispatch-timeout-minutes` (default 5) whose command never reached a terminal state are transitioned (command → TIMEOUT via `CommandStateMachine` where valid) and retried/failed per decision #4. |
| 5 | Missed window (server down) | **Fail-safe asymmetric catch-up**: missed **OFF** fires late up to the start of the next ON cycle (never leave a fence energized). Missed **ON** fires late only within **15 min**; older → recorded `MISSED`, never fired late (a surprise late ON is a safety hazard). |
| 6 | Already in target state | Always dispatch — device state can be stale; BR05 treats repeat ON/OFF as no-op. |
| 7 | Duplicate-execution guard | `schedule_runs` unique constraint `(schedule_id, scheduled_slot, command_type)` — DB-level claim; safe for 30s ticks, restarts, and overlapping instances. No ShedLock needed. |
| 8 | Command audit origin | `machine_commands.source = 'SCHEDULE'` + `schedule_id` FK. `issued_by` = user who created the schedule (audit traceability). |
| 9 | Disable mid-cycle | Disabling a schedule cancels **future** runs, but a pending OFF belonging to an already-executed ON cycle **still fires** (fail-safe toward de-energized). UI states this explicitly. |
| 10 | Manual override | Manual commands remain fully independent and always win *at that moment*; the scheduler still fires at its next slot (decision #6). UI note explains this. |
| 11 | Kill switch | `schedule.execution.enabled` config property (default `true`) disables the whole engine without a deploy; per-schedule `enabled` flag is the user-level switch. |
| 12 | Success definition | Reuses rule 5 verbatim: a run is successful only when the device ACK/DONE arrives via `CommandResultConsumer`. The scheduler never marks success itself. |

### 1.4 Safety requirements mapping (requirement #10)

| Requirement | How it is enforced |
|---|---|
| Authorization | `schedule:read` / `schedule:write` permissions (seeded like V45/V46); `@PreAuthorize` on every endpoint; CUSTOMER role gets both (same spirit as `command:write` in V18) |
| Tenant isolation | `organization_id` from JWT via `OwnerContextService` + `TenantGuard`; machine access via `MachineAccessPolicy.loadAndValidate` (SEC-02 pattern) — never from request body (rules 7, 8) |
| Safe state transitions | Reuses `CommandStateMachine`; `schedule_runs` has its own small state machine PENDING→DISPATCHED→ACKED/FAILED/MISSED |
| Connectivity awareness | Dispatch + retry + TIMEOUT via existing attempt machinery; failure push tells the user the fence did NOT change state |
| Fail-safe behavior | Asymmetric catch-up (decision 5), fail-safe OFF on disable (decision 9), kill switch (decision 11) |
| No false success | Success is only reported from device ACK/DONE (decision 12) |

---

## 2. Database Design

Two migrations, continuing from the current head (V54):

### `database/migrations/V55__machine_schedules.sql`

```sql
-- V55__machine_schedules.sql
-- Automatic fence ON/OFF scheduling.
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only. UUIDs PKs (rule 19).

CREATE TABLE IF NOT EXISTS machine_schedules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id       UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    on_time          TIME NOT NULL,                      -- local wall clock, e.g. 18:00
    off_time         TIME NOT NULL,                      -- e.g. 07:00 (may be <= on_time → next day)
    days_of_week     SMALLINT NOT NULL DEFAULT 127,      -- bitmask Mon=1 ... Sun=64; 127 = daily; applies to ON
    timezone         VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_machine_schedules_machine ON machine_schedules(machine_id);
CREATE INDEX IF NOT EXISTS idx_machine_schedules_org ON machine_schedules(organization_id);
CREATE INDEX IF NOT EXISTS idx_machine_schedules_enabled ON machine_schedules(enabled) WHERE enabled;

CREATE TABLE IF NOT EXISTS schedule_runs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id      UUID NOT NULL REFERENCES machine_schedules(id) ON DELETE CASCADE,
    scheduled_slot   TIMESTAMPTZ NOT NULL,               -- the absolute fire instant (UTC)
    command_type     VARCHAR(10) NOT NULL,               -- ON | OFF
    command_id       UUID REFERENCES machine_commands(id) ON DELETE SET NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|DISPATCHED|ACKED|FAILED|MISSED
    error            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempt_count    SMALLINT NOT NULL DEFAULT 0,        -- scheduler-owned retries (decision #4)
    UNIQUE (schedule_id, scheduled_slot, command_type)   -- idempotency / duplicate-execution guard
);

CREATE INDEX IF NOT EXISTS idx_schedule_runs_schedule ON schedule_runs(schedule_id, scheduled_slot DESC);
CREATE INDEX IF NOT EXISTS idx_schedule_runs_status ON schedule_runs(status) WHERE status IN ('PENDING','DISPATCHED');

ALTER TABLE machine_commands ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE machine_commands ADD COLUMN IF NOT EXISTS schedule_id UUID REFERENCES machine_schedules(id) ON DELETE SET NULL;
COMMENT ON COLUMN machine_commands.source IS 'USER | SCHEDULE';

-- Permissions (same pattern as V45 geofence permissions)
INSERT INTO permissions (name, description) VALUES
('schedule:read',  'View machine ON/OFF schedules'),
('schedule:write', 'Create, update, enable/disable machine ON/OFF schedules')
ON CONFLICT (name) DO NOTHING;

-- Grant to roles that already hold command:write/command:read (ADMIN, OPERATOR, CUSTOMER — mirror V18/V46)
-- (exact INSERT..SELECT joins against roles/permissions as in V46)
```

### `database/migrations/V56__schedule_notification_templates.sql`

Seeds `notification_templates` for en/hi/mr (same `ON CONFLICT DO NOTHING`
pattern as V36/V40/V54) for:

| alert_type | incident_state | Example (en) |
|---|---|---|
| `SCHEDULED_ON_EXECUTED` | DONE | "Machine {machineName} was turned ON automatically by schedule." |
| `SCHEDULED_OFF_EXECUTED` | DONE | "Machine {machineName} was turned OFF automatically by schedule." |
| `SCHEDULED_COMMAND_FAILED` | FAILED (+TIMEOUT defensive) | "Scheduled {observedUnit} failed on machine {machineName} — device unreachable. Fence state is UNCHANGED. Please check." |
| `SCHEDULED_COMMAND_MISSED` | MISSED | "The scheduled {observedUnit} for machine {machineName} was missed and was NOT executed for safety." |

> Implementation note: `AlertTransitionMessage.observedValue` is `Double`, so
> the command direction ("ON"/"OFF") is carried in `observedUnit` — the only
> free-form String slot — and templates interpolate it as `{observedUnit}`.
> Terminal run failures always emit state `FAILED` (a command-level TIMEOUT is
> an internal step toward it); the TIMEOUT-state rows exist only as a
> localized fallback for future emitters.

Plus default `notification_preferences` rows (pattern of V38).

---

## 3. Backend API Design

Base path (RESTful, versioned, rule: API design):

```
GET    /api/v1/machines/{machineId}/schedule          → 200 ScheduleDto | 204 (no schedule — GeofenceController convention)
PUT    /api/v1/machines/{machineId}/schedule          → upsert (create or full update)
PATCH  /api/v1/machines/{machineId}/schedule/enabled  → { "enabled": true|false }
DELETE /api/v1/machines/{machineId}/schedule          → delete schedule
GET    /api/v1/machines/{machineId}/schedule/runs     → paged run history (audit/log view)
```

### DTOs (Java records, like other DTO packages)

```java
// ScheduleRequest — @Valid bean validation
public record ScheduleRequest(
    @NotNull LocalTime onTime,
    @NotNull LocalTime offTime,
    @Min(1) @Max(127) int daysOfWeek,      // bitmask, Mon=1..Sun=64
    @NotBlank String timezone,             // validated against ZoneId.getAvailableZoneIds()
    boolean enabled
) {}

// ScheduleDto — response; includes server-computed preview (UI stays "dumb")
public record ScheduleDto(
    UUID id, UUID machineId,
    LocalTime onTime, LocalTime offTime, int daysOfWeek,
    String timezone, boolean enabled,
    Instant nextOnAt, Instant nextOffAt,          // computed by ScheduleTimeService
    ScheduleRunDto lastRun,                        // latest run summary for status display
    Instant updatedAt
) {}

// ScheduleRunDto
public record ScheduleRunDto(
    UUID id, Instant scheduledSlot, String commandType,
    String status, UUID commandId, String error, Instant updatedAt
) {}
```

### Validation rules

- `onTime != offTime`
- `daysOfWeek` in 1..127
- `timezone` must be a valid IANA `ZoneId`
- Machine must exist, belong to the caller's org, and (for customers) be
  assigned — all via `MachineAccessPolicy.loadAndValidate` (SEC-02)
- Machine must have a bound device with IMEI (same check as
  `CommandService.createCommand`) — reject schedule creation otherwise with a
  clear error ("No device bound to this machine")
- Errors via `GlobalExceptionHandler`, standard `{error,message,timestamp,status}` shape

### Core services

```
ScheduleTimeService          — pure, stateless slot math (unit-test heavy):
  nextOnInstant(schedule, from)     → next ON fire instant (ZonedDateTime math, DST-safe)
  pairedOffInstant(schedule, onAt)  → OFF for a given ON (cycle semantics, decision #2)
  dueSlots(schedule, window)        → slots due in [windowStart, windowEnd]

ScheduleService              — CRUD + tenant/RBAC guards + preview computation

ScheduleExecutionService     — claim + dispatch one run (transactional)

ScheduleCommandScheduler     — @Scheduled(fixedDelayString = "${schedule.tick-ms:30000}") tick loop
```

### Dispatch path — reuse, don't fork (rule: suggestion 6 of draft)

Extract the internals of `CommandService.createCommand` into a package-private
`dispatch(Machine, Device, String commandType, UUID issuedBy, String source, UUID scheduleId)`
used by **both** the controller path (`source='USER'`) and the scheduler
(`source='SCHEDULE'`). The scheduler path skips the HTTP-security context
(`ownerContextService`) and instead takes `organization_id` from the schedule
row — which was tenant-validated at creation time. Everything downstream
(RabbitMQ publish-after-commit, state machine, attempts, broadcast,
notifications) is identical.

### Scheduler tick algorithm

```
every 30s (skipped entirely if schedule.execution.enabled=false):
  for each schedule (enabled or disabled — see decision #9):
    compute due slots in window:
       ON  slots: [now - 15min, now]            (decision #5 — short ON catch-up)
                  skipped entirely when the schedule is disabled
       OFF slots: [cycle start, now]            (fire any missed OFF up to next ON;
                  runs even for disabled schedules when its ON cycle was claimed)
    for each due slot:
       INSERT INTO schedule_runs ... ON CONFLICT DO NOTHING   -- atomic claim
       if claimed (insert succeeded):
          if ON slot older than 15 min → status=MISSED + MISSED outbox notification; continue
          dispatch via CommandService.dispatch(...) → run.status=DISPATCHED, run.command_id=..., attempt_count=1
  retry pass (scheduler-owned — the gateway has NO retry, verified):
    runs with status=DISPATCHED whose linked command is terminal FAILED/TIMEOUT
    and attempt_count < 3 and still inside the cycle (before paired OFF for ON runs)
      → re-dispatch a NEW machine_command linked to the same run, attempt_count++
    attempt_count == 3 and terminal failure → run.status=FAILED + failure push
  reconciliation sweep (no backend command-timeout exists — verified):
    runs DISPATCHED > schedule.dispatch-timeout-minutes with a non-terminal command
      → transition command to TIMEOUT (where CommandStateMachine allows) → handled by retry pass
  retention: delete schedule_runs older than schedule.runs-retention-days (default 90)
```

`CommandResultConsumer` hook: on terminal state of a `source='SCHEDULE'`
command — DONE → run ACKED; FAILED/TIMEOUT → leave DISPATCHED for the retry
pass (or FAILED if attempts exhausted).

`CommandResultConsumer` gets a small addition: after transitioning the
command, if `command.source == 'SCHEDULE'`, update the linked `schedule_runs`
row (DONE → ACKED; FAILED/TIMEOUT → deferred to the scheduler retry pass) and
emit the `SCHEDULED_*` notification instead of the generic `MACHINE_ON/OFF`
one — success push on ACKED, failure push only after retries are exhausted
(so users can tell scheduled from manual pushes, and per-type notification
toggles work).

`CommandResponse` additionally exposes `source` (and `scheduleId`) so the
mobile Command History can show a **"Scheduled" badge** on scheduler-issued
commands.

---

## 4. Mobile UI Design (Flutter / Riverpod)

Follows `features/<feature>/pages|providers|widgets|models` conventions.

```
mobile/lib/features/schedules/
  models/machine_schedule.dart          — immutable, fromJson/toJson, const ctor
  models/schedule_run.dart
  providers/schedule_provider.dart      — machineScheduleProvider(machineId): AsyncValue<MachineSchedule?>
                                          scheduleActionsProvider — save/enable/disable/delete via Dio ApiClient
  providers/schedule_runs_provider.dart — paged run history
  pages/schedule_edit_page.dart         — route /app/machines/:id/schedule (context.push drill-down)
  widgets/schedule_card.dart            — summary card on Machine Detail
  widgets/day_of_week_selector.dart     — Mon–Sun chips (bitmask)
  widgets/schedule_run_tile.dart
```

### Machine Detail page addition

`ScheduleCard` inserted below `OnOffButton` in `machine_detail_page.dart`:

- **No schedule** → "Set up automatic ON/OFF" button → push edit page
- **Schedule exists** →
  - Enable/Disable `Switch` (PATCH enabled)
  - "ON 6:00 PM → OFF 7:00 AM · Mon Tue Wed Thu Fri"
  - Status line from backend-computed fields: "Next: ON today 6:00 PM" and
    last run result ("Last: ON executed ✓ 6:00 PM" / "Last: OFF failed — device offline")
  - Edit button → push edit page
- All three `AsyncValue` states handled with `.when()`; loading skeleton;
  error panel; never a blank area

### Schedule Edit page

- Two `showTimePicker` fields (ON time, OFF time) — display "+1 day" chip next
  to OFF when `offTime <= onTime` (cross-midnight indicator)
- `DayOfWeekSelector` chips; validation: at least one day
- Timezone shown read-only (default Asia/Kolkata) — editable only later if needed
- Save → PUT; user-friendly errors from `GlobalExceptionHandler` payloads,
  never raw exceptions
- Safety note text: "Scheduled commands are executed by the server and
  confirmed by the device. If the device is unreachable the fence state does
  not change and you will be notified."
- Disabling note: "Disabling cancels future runs; an OFF belonging to an
  already-started cycle will still run for safety."

### Localization

All strings added to `app_en.arb`, `app_hi.arb`, `app_mr.arb` (follow
`l10n/GLOSSARY.md`).

### Live status

Scheduled command status arrives through the existing command WebSocket
(`command_socket_provider.dart`) and push notifications — no new socket work;
the runs list refreshes on pull like other lists.

---

## 5. Implementation Phases & Prompts

Per AGENTS.md rule 20: implement **one phase at a time**, STOP after each
phase, and ask the user to verify before continuing. Each phase below is
self-contained, deployable, and testable, and includes a copy-paste
implementation prompt.

### Phase 1 — Database schema + Schedule CRUD API (Backend)

**Deliverables**
- `V55__machine_schedules.sql` (section 2) incl. permission seeding
- `MachineSchedule`, `ScheduleRun` entities + repositories
- `ScheduleTimeService` (pure slot math) — written first, TDD
- `ScheduleService` (CRUD, guards, preview) + `ScheduleController`
- DTOs (records) + Bean Validation + `GlobalExceptionHandler` mappings
- Tests: `ScheduleTimeServiceTest` (cross-midnight, day bitmask, DST,
  timezone), `ScheduleServiceTest` (validation, tenant isolation, upsert
  idempotency, device-not-bound rejection), controller RBAC test

**Verification**: `./gradlew :backend:test` green; manual: create schedule via
curl/Postman for a test machine, confirm GET returns computed `nextOnAt/nextOffAt`.

**Implementation prompt**

> Implement Phase 1 of `docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md` (schema +
> CRUD API). Create Flyway migration `database/migrations/V55__machine_schedules.sql`
> exactly as specified in section 2, including `schedule:read`/`schedule:write`
> permission seeding following the V45/V46 pattern (grant to every role that
> currently has `command:write`). Create JPA entities `MachineSchedule` and
> `ScheduleRun` (extend `BaseEntity` where appropriate, matching
> `MachineCommand`/`CommandAttempt` style), repositories, and a pure
> `ScheduleTimeService` implementing `nextOnInstant`, `pairedOffInstant`, and
> `dueSlots` using `ZoneId`/`ZonedDateTime` with cycle semantics: the
> days-of-week bitmask (Mon=1..Sun=64) applies to the ON event; the paired OFF
> is the next occurrence of offTime strictly after the ON instant. Write
> `ScheduleTimeServiceTest` FIRST covering: same-day schedule, cross-midnight
> (ON 18:00/OFF 07:00 → OFF next day), single-day selection (ON Mon → OFF Tue),
> daily bitmask 127, a non-IST timezone, and a DST transition. Then add
> `ScheduleService` + `ScheduleController` with endpoints
> GET/PUT/PATCH-enabled/DELETE `/api/v1/machines/{machineId}/schedule` and GET
> `/schedule/runs` (paged), DTOs as records in `dto/schedule/`, Bean Validation
> (`onTime != offTime`, bitmask 1..127, valid ZoneId), `@PreAuthorize` with
> `schedule:read`/`schedule:write`, tenant isolation via
> `MachineAccessPolicy.loadAndValidate` + `OwnerContextService` (never trust
> request-body org ids), and reject creation when no device with IMEI is bound
> to the machine (same check as `CommandService.createCommand`). Map errors via
> the existing `GlobalExceptionHandler`. Do NOT build the scheduler engine yet.
> Add service and controller tests following `CommandServiceTest` style. Run
> `./gradlew :backend:test` and fix failures. STOP after this phase for user
> verification.

---

### Phase 2 — Scheduler engine (Backend core)

**Deliverables**
- `CommandService.dispatch(...)` extraction; controller path unchanged
  (`source='USER'`); regression tests prove manual commands still work
- `ScheduleExecutionService` — transactional claim (`ON CONFLICT DO NOTHING`)
  + dispatch; MISSED handling for stale ON slots
- `ScheduleCommandScheduler` — `@Scheduled(fixedDelayString="${schedule.tick-ms:30000}")`,
  guarded by `schedule.execution.enabled` (default true) kill switch
- `CommandResultConsumer` hook: on terminal state of a `source='SCHEDULE'`
  command, update the linked `schedule_runs` row (DONE→ACKED; FAILED/TIMEOUT →
  retry pass or FAILED when attempts exhausted)
- **Scheduler-owned retry pass** (the gateway has no retry — verified): re-dispatch
  failed runs every `schedule.retry-interval-minutes` up to 3 attempts within the
  cycle; each retry = new `machine_commands` row linked to the same run
- **Reconciliation sweep** (no backend command timeout exists — verified): runs
  DISPATCHED > `schedule.dispatch-timeout-minutes` with a non-terminal command →
  TIMEOUT → retry/fail
- `schedule_runs` retention cleanup (`schedule.runs-retention-days`, default 90)
- Startup catch-up (asymmetric: ON ≤ 15 min, OFF until next cycle)
- `source`/`scheduleId` exposed in `CommandResponse`
- Config in `application.yml` + logging with scheduleId/machineId/IMEI context

**Verification**: unit tests for double-tick idempotency, missed-window ON vs
OFF asymmetry, disabled-schedule skip, kill switch; integration: set a schedule
2 minutes ahead on a test device/simulator and watch
`machine_commands`/`schedule_runs`/logs.

**Implementation prompt**

> Implement Phase 2 of `docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md`
> (scheduler engine). First refactor `CommandService`: extract the body of
> `createCommand` into a reusable `dispatch(Machine machine, String commandType,
> UUID issuedBy, String source, UUID scheduleId)` that writes the
> `machine_commands` row (with new `source` and `schedule_id` columns) and
> registers the after-commit `CommandProducer.sendCommand` publish. The HTTP
> path keeps its security-context lookups and calls `dispatch` with
> source='USER'; existing tests must stay green. Then add
> `ScheduleExecutionService`: claims a slot by inserting into `schedule_runs`
> relying on the unique constraint (use `ON CONFLICT DO NOTHING` via native
> query or catch the constraint violation — skipping silently when already
> claimed), marks ON slots older than 15 minutes as MISSED without dispatching
> (write a MISSED outbox notification event), otherwise dispatches via
> `CommandService.dispatch` with source='SCHEDULE' and the schedule creator as
> issuedBy, setting run status=DISPATCHED and command_id. Add
> `ScheduleCommandScheduler` with `@Scheduled(fixedDelayString =
> "${schedule.tick-ms:30000}")`, short-circuiting when config
> `schedule.execution.enabled=false`; each tick loads enabled schedules and
> computes due slots via `ScheduleTimeService.dueSlots` with the asymmetric
> windows from section 3 (ON: now-15min..now; OFF: fire any missed OFF up to
> the next ON cycle start). Never let the scheduler publish to RabbitMQ
> directly — only via CommandService.dispatch (AGENTS.md rules 4/5/6). In
> `CommandResultConsumer`, after `transitionCommand`, if the command's source
> is SCHEDULE, update the linked `schedule_runs` row (DONE→ACKED;
> FAILED/TIMEOUT → leave for the retry pass) in a way that never breaks the
> main consumer flow (log-and-continue like the existing secondary operations).
> IMPORTANT (verified): the gateway `CommandDispatchService` fails immediately
> for offline devices and has NO retry, and no backend component times out
> stuck commands — implement BOTH in the scheduler tick: (a) a retry pass that
> re-dispatches runs whose linked command terminally failed, every
> `schedule.retry-interval-minutes` (default 5) up to run.attempt_count=3 and
> only while still inside the cycle, each retry creating a NEW machine_commands
> row linked to the same run; after the 3rd failure set run FAILED; (b) a
> reconciliation sweep that transitions commands of runs stuck DISPATCHED
> longer than `schedule.dispatch-timeout-minutes` (default 5) to TIMEOUT via
> the state machine, feeding them into the retry pass. Also add a daily
> retention cleanup deleting schedule_runs older than
> `schedule.runs-retention-days` (default 90), and expose `source` and
> `scheduleId` in `CommandResponse`.
> Add tests: double-tick does not double-dispatch (unique-constraint claim),
> MISSED vs late-OFF asymmetry, disabled schedule and kill switch skip,
> retry pass increments attempts and stops at 3, reconciliation sweep times out
> stuck runs, dispatch failure marks the run for retry. SLF4J logging with
> scheduleId, machineId, commandId, slot. Run `./gradlew :backend:test`. STOP
> after this phase for user verification.

---

### Phase 3 — Notifications (Backend)

**Deliverables**
- `V56__schedule_notification_templates.sql` — en/hi/mr templates +
  default preferences for `SCHEDULED_ON_EXECUTED`, `SCHEDULED_OFF_EXECUTED`,
  `SCHEDULED_COMMAND_FAILED`, `SCHEDULED_COMMAND_MISSED` (patterns of V36/V38/V40)
- `CommandResultConsumer.generateCommandNotification` routes `source='SCHEDULE'`
  commands to the `SCHEDULED_*` alert types (DONE→EXECUTED by command type,
  FAILED/TIMEOUT→SCHEDULED_COMMAND_FAILED); MISSED event emitted from
  `ScheduleExecutionService` through the `event_outbox`
- Register new types in `NotificationFeatureSwitches` / event catalog so
  per-type user toggles work

**Verification**: trigger a scheduled run on staging/simulator and confirm
inbox items + FCM pushes in all three locales; failure path by disconnecting
the test device.

**Implementation prompt**

> Implement Phase 3 of `docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md`
> (notifications). Create `database/migrations/V56__schedule_notification_templates.sql`
> seeding `notification_templates` for alert types SCHEDULED_ON_EXECUTED (DONE),
> SCHEDULED_OFF_EXECUTED (DONE), SCHEDULED_COMMAND_FAILED (FAILED and TIMEOUT
> incident states — check how V48 handled TIMEOUT) and SCHEDULED_COMMAND_MISSED
> (MISSED), in en, hi, and mr following the wording style and placeholder
> conventions of V36/V40/V52 ({machineName}, {observedValue}); failure/missed
> bodies MUST state that the fence state did not change. Seed default
> notification preferences per the V38 pattern. In `CommandResultConsumer`,
> when the command's source is SCHEDULE, map DONE to
> SCHEDULED_ON_EXECUTED/SCHEDULED_OFF_EXECUTED by command type and
> FAILED/TIMEOUT to SCHEDULED_COMMAND_FAILED instead of the generic
> MACHINE_ON/MACHINE_OFF/COMMAND_FAILED types, keeping the existing
> AlertTransitionMessage/event_outbox mechanism. Emit SCHEDULED_COMMAND_MISSED
> from `ScheduleExecutionService` when marking a run MISSED, via the same
> outbox insert pattern. Register all four types in
> `NotificationFeatureSwitches` and any event/alert-type catalog so per-type
> toggles work. Add unit tests for the consumer routing (scheduled vs manual
> source) mirroring `CommandResultConsumerTest`. Run `./gradlew :backend:test`.
> STOP after this phase for user verification.

---

### Phase 4 — Mobile UI (Flutter)

**Deliverables**
- `features/schedules/` feature folder (section 4): models, providers
  (Riverpod, Dio via existing `ApiClient` — never from widgets), pages, widgets
- `ScheduleCard` on Machine Detail; `ScheduleEditPage` at
  `/app/machines/:id/schedule` registered in `app_router.dart` with
  `context.push` drill-down
- Enable/Disable switch, edit, status display (next run + last run result),
  run history list (paged, loading/empty/error states)
- l10n strings en/hi/mr; "+1 day" cross-midnight chip; safety notes
- Command History: "Scheduled" badge on commands with `source == 'SCHEDULE'`
- Tests: model json round-trip, provider unit tests, widget tests for
  ScheduleCard states and edit-page validation

**Verification**: `flutter analyze` + `flutter test`; manual flow on a device
against staging: create → see preview → disable → edit → delete; confirm push
arrives when the schedule fires.

**Implementation prompt**

> Implement Phase 4 of `docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md` (mobile
> UI) in `mobile/`. Create `lib/features/schedules/` with: immutable
> `MachineSchedule` and `ScheduleRun` models (fromJson/toJson, const
> constructors) matching the backend `ScheduleDto`/`ScheduleRunDto` shapes; a
> `machineScheduleProvider(machineId)` family returning
> `AsyncValue<MachineSchedule?>` (404 → null = no schedule) and action methods
> (save/upsert via PUT, toggle enabled via PATCH, delete) using the existing
> Dio `ApiClient` pattern from
> `lib/features/commands/providers/command_provider.dart` — no API calls from
> widgets; a `scheduleRunsProvider(machineId)` for paged run history. Add
> `ScheduleCard` to `machine_detail_page.dart` below `OnOffButton`: when no
> schedule exists show a "Set up automatic ON/OFF" action; when one exists show
> the enable/disable Switch, a summary line ("ON 6:00 PM → OFF 7:00 AM ·
> selected days"), server-computed "Next: ..." and last-run status, and an Edit
> action. Handle all AsyncValue states with `.when()` (loading skeleton, error
> panel, data) — never blank. Create `ScheduleEditPage` routed at
> `/app/machines/:id/schedule` (register in `lib/routing/app_router.dart`,
> navigate with `context.push`): two time pickers, a reusable
> `DayOfWeekSelector` chip row (bitmask Mon=1..Sun=64, at least one required),
> a "+1 day" chip beside OFF time when offTime <= onTime, save/delete with
> confirmation dialog for delete, user-friendly error snackbars (never raw
> exceptions), and the safety/disable notes from section 4 of the doc. Add all
> strings to `app_en.arb`, `app_hi.arb`, `app_mr.arb` per `l10n/GLOSSARY.md`.
> Do not modify the existing manual `OnOffButton` behavior. Add model
> round-trip tests, provider tests, and widget tests for the card's
> no-schedule/enabled/disabled/error states and edit-page validation. Run
> `flutter analyze` and `flutter test` in `mobile/` and fix issues. STOP after
> this phase for user verification.

---

### Phase 5 — Integration verification & deployment

**Deliverables**
- End-to-end verification checklist executed on staging with a real/simulated
  BR05 device (the repo's `simulator/` can be used)
- Deployment notes (below) confirmed; feature flag toggled on

**Checklist**

1. Flyway migrates V55/V56 cleanly on a copy of production data
2. Manual ON/OFF still works exactly as before (regression)
3. Schedule ON at `now+2min`, OFF at `now+4min` → both fire once; rows in
   `machine_commands` (`source='SCHEDULE'`), `command_attempts`,
   `schedule_runs` (ACKED); pushes received (en + hi/mr user)
4. Cross-midnight: ON 18:00 / OFF 07:00 Mon-only → verify computed
   `nextOffAt` is Tuesday 07:00
5. Device offline at fire time → retries → FAILED → failure push; fence
   state unchanged
6. Restart backend across a slot → OFF catch-up fires, stale ON becomes
   MISSED + missed push
7. Two backend ticks in quick succession (or restart mid-tick) → no duplicate
   command rows (unique-constraint claim)
8. Disable mid-cycle → pending OFF still fires; no further ON
9. Kill switch `schedule.execution.enabled=false` → no scheduled dispatches
10. Tenant isolation: user from org B cannot read/write org A's schedule;
    customer cannot schedule an unassigned machine

**Implementation prompt**

> Execute Phase 5 of `docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md`. Run the
> full backend and mobile test suites, then walk through the 10-point
> integration checklist in section 5 Phase 5 against the local stack
> (infra docker-compose: PostgreSQL+PostGIS, Redis, RabbitMQ) using the
> device simulator in `simulator/`. For each checklist item record the
> result (SQL evidence from machine_commands/schedule_runs where relevant)
> and report failures with root cause before fixing. Do not deploy to any
> environment without explicit user approval.

---

## 6. Configuration & Setup Instructions

```yaml
# backend application.yml additions
schedule:
  execution:
    enabled: true              # kill switch — disables the engine without deploy
  tick-ms: 30000               # scheduler tick interval
  on-catchup-minutes: 15       # max lateness for a missed ON (safety)
  retry-interval-minutes: 5    # scheduler-owned retry spacing (gateway has no retry)
  dispatch-timeout-minutes: 5  # reconciliation sweep for stuck DISPATCHED runs
  runs-retention-days: 90      # schedule_runs cleanup
```

- No new infrastructure: uses the existing PostgreSQL, RabbitMQ and the
  in-process Spring scheduler (`@EnableScheduling` already active).
- Migrations auto-apply via Flyway on backend start (`database/migrations/`).
- Server clock must be NTP-synced (it already must be for telemetry).
- If the backend is ever scaled to multiple instances, the
  `schedule_runs` unique constraint already prevents double execution; no
  ShedLock required.

## 7. Risks & Edge Cases (summary)

| Risk | Mitigation |
|---|---|
| Double-fire (tick overlap, restart, multi-instance) | DB unique-constraint claim on `schedule_runs` |
| Device offline at fire time | Scheduler-owned retries (3 × 5 min) → FAILED + push; state never assumed changed |
| Command result message lost / run stuck DISPATCHED | Reconciliation sweep → TIMEOUT → retry/fail + push |
| `schedule_runs` unbounded growth | Daily retention cleanup (90 days) |
| Server down across a slot | Asymmetric catch-up: late OFF fires, stale ON → MISSED + push |
| Late ON energizing fence unexpectedly | 15-min ON catch-up cap; MISSED never fired late |
| User edits schedule mid-window | Runs keyed by absolute slot; next tick recomputes from updated row |
| Wrong/ambiguous timezone | Per-schedule IANA `ZoneId`; `ZonedDateTime` math; DST unit tests |
| User disables while fence is ON | Pending cycle OFF still fires (fail-safe); UI states this |
| Stale device state ("already ON") | Always dispatch; BR05 treats repeats as no-op |
| Whole feature misbehaving in prod | `schedule.execution.enabled=false` kill switch, per-schedule `enabled` |

## 8. Open Questions

1. **admin-web**: should org admins also manage schedules from the web panel?
   Out of scope for v1 (mobile-only); the API is web-ready if needed later.
2. **Automated integration tests**: Phase 5 is a manual checklist against the
   local stack + simulator. If Testcontainers (PostgreSQL + RabbitMQ) is
   acceptable as a new test dependency, the double-tick, retry, and
   catch-up scenarios should additionally be covered by `@SpringBootTest`
   integration tests — confirm before adding the dependency.

## 9. Effort Estimate

| Phase | Size |
|---|---|
| 1 — Schema + CRUD API | S/M |
| 2 — Scheduler engine | M |
| 3 — Notifications | S |
| 4 — Mobile UI | M |
| 5 — Integration & deploy | S |
