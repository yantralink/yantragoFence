# Scheduled Ignition ON/OFF — Implementation Plan

Status: **DRAFT — awaiting user review**
Created: 2026-09-25
Scope: Backend + Mobile (scheduler, API, notifications, UI)

---

## 1. Feasibility — yes, fully possible

Everything needed already exists end-to-end:

```
User tap (Turn ON / Turn OFF)
  → POST /api/v1/commands            CommandController
  → CommandService.createCommand     audit row in machine_commands
  → RabbitMQ (CommandProducer)       → device-gateway
  → TCP frame to device              → device executes relay
  → CommandResultConsumer            ACK/timeout → command_attempts
```

Scheduled ignition is **only a new trigger** on top of this pipeline. The
scheduler creates the same audited `machine_commands` row and dispatches
through `CommandProducer` — identical protocol, ACK handling, retry and
audit guarantees. No gateway or protocol changes required (rule 13, 16).

The device-side clock is irrelevant: the **server** decides when to fire;
the tracker just executes a normal ON/OFF command.

## 2. Requirement

Per machine, the user configures:

| Setting | Example |
|---|---|
| ON time | 06:00 |
| OFF time | 18:30 |
| Days | Daily / Mon–Fri / custom days |
| Enabled | on/off toggle |

At the scheduled time the backend issues `TURN_ON` / `TURN_OFF` to the
device automatically, with the same audit trail and notifications as a
manual command.

## 3. Design decisions (proposed — confirm before Phase 1)

| # | Decision | Proposal | Why |
|---|---|---|---|
| 1 | Schedules per machine | **One** active schedule per machine | Simpler UI, no overlapping-schedule conflicts. Can relax later. |
| 2 | Timezone | Per-schedule `timezone` column, default from org/customer | All logic evaluates in the machine's local wall-clock time (IST). Server cron runs in UTC. |
| 3 | Offline device at fire time | Dispatch anyway → command goes TIMEOUT → **retry up to 3 times over 15 min** → then FAILED + push | Device may reconnect moments later; silent retry avoids false failures. |
| 4 | Backend restart / missed window | On startup, fire any slot missed within last **15 min**; older → record `MISSED` | Covers deploy windows without surprise late commands. |
| 5 | Already in target state | Send the command anyway | `devices` state can be stale; device treats repeat ON as no-op. |
| 6 | Overnight ranges (ON 22:00 → OFF 06:00) | **Allowed** — times are independent events, not a duration | Each time is its own trigger; no range validation needed. |
| 7 | Command origin | `machine_commands.source = 'SCHEDULE'` (+ `schedule_id`) instead of user id | Audit stays intact and scheduled runs are distinguishable in Command History. |
| 8 | Notifications | Reuse outbox → inbox/push pipeline; new types `SCHEDULED_ON_EXECUTED`, `SCHEDULED_OFF_EXECUTED`, `SCHEDULED_COMMAND_FAILED` | Same as the battery feature: en/hi/mr templates + per-type toggles. |

## 4. Database (Flyway `V55__machine_schedules.sql`)

```sql
CREATE TABLE machine_schedules (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  uuid NOT NULL REFERENCES organizations(id),
    machine_id       uuid NOT NULL REFERENCES machines(id),
    on_time          time NOT NULL,            -- e.g. 06:00
    off_time         time NOT NULL,            -- e.g. 18:30
    days_of_week     smallint NOT NULL DEFAULT 127,  -- bitmask Mon=1..Sun=64; 127=daily
    timezone         varchar(50) NOT NULL DEFAULT 'Asia/Kolkata',
    enabled          boolean NOT NULL DEFAULT true,
    created_by       uuid REFERENCES users(id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_machine_schedules_machine
    ON machine_schedules(machine_id);          -- decision #1: one per machine

CREATE TABLE schedule_runs (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id      uuid NOT NULL REFERENCES machine_schedules(id) ON DELETE CASCADE,
    scheduled_slot   timestamptz NOT NULL,     -- the fire instant (UTC)
    command_type     varchar(10) NOT NULL,     -- TURN_ON | TURN_OFF
    command_id       uuid REFERENCES machine_commands(id),
    status           varchar(20) NOT NULL,     -- PENDING|DISPATCHED|ACKED|FAILED|MISSED
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (schedule_id, scheduled_slot, command_type)  -- idempotency guard
);
ALTER TABLE machine_commands ADD COLUMN source varchar(20) NOT NULL DEFAULT 'USER';
ALTER TABLE machine_commands ADD COLUMN schedule_id uuid REFERENCES machine_schedules(id);
```

The `schedule_runs` unique key makes the scheduler safe to run every
30 s and safe across restarts — a slot can only be claimed once.

## 5. Backend work

### Phase 1 — CRUD API (half day)
- `MachineSchedule` entity + repository + `ScheduleService`
- `ScheduleController` — `GET/PUT /api/v1/machines/{id}/schedule` (upsert), `DELETE` to disable
- Validation: `on_time != off_time`, valid TZ, bitmask non-zero; RBAC same permission as issuing commands; tenant guard via JWT org
- `ScheduleService.nextRuns()` helper — computes next ON/OFF instants (used by UI preview and tests)
- Tests: validation, tenant isolation, upsert idempotency

### Phase 2 — Scheduler engine (core)
- `ScheduleCommandScheduler` — `@Scheduled(fixedDelay=30_000)`
  1. Load `enabled` schedules; compute slots due in `[now-15min, now+30s]` per schedule TZ
  2. Insert `schedule_runs` row (unique constraint = claim; `ON CONFLICT` → already handled, skip)
  3. Dispatch via the **existing** command path (`CommandService` internals extracted to a shared `dispatch(...)` used by both the controller and scheduler — no parallel implementation)
  4. `source='SCHEDULE'`, `schedule_id` linked
- Missed-window catch-up on startup (decision #4)
- Retry policy for offline/timeout (decision #3) — reuse `max_attempts`/`recordAttempt` machinery
- Tests: slot computation across midnight/days, idempotent double-tick, missed-window, retry

### Phase 3 — Notifications
- On ACK → `SCHEDULED_*_EXECUTED` outbox event → push "Fence turned ON (scheduled 6:00 AM)" en/hi/mr
- On final failure → `SCHEDULED_COMMAND_FAILED` → "Scheduled OFF failed — device unreachable"
- Migration seeds templates + preferences (same pattern as V54); register in `NotificationFeatureSwitches` + event catalog

### Phase 4 — Mobile UI
- Machine Detail → "Auto Schedule" card: ON time, OFF time, day chips, enable switch, "Next: ON at 6:00 AM" preview, localized en/hi/mr
- Command History shows scheduled runs already (badge "Scheduled" via `source`)

### Phase 5 — Deploy + verify
- Flyway, tests, staging deploy, live verification with a real schedule on a test device

## 6. Suggestions

1. **One schedule per machine for now** — daily recurring. Per-day schedules (different times on weekends) is a v2; the schema already supports it via `days_of_week`.
2. **Keep the UI dumb** — the backend owns "next run" computation; the app only displays it. Avoids clock/timezone bugs on the phone.
3. **Push the result, not just the failure** — users distrust silent automation; an "executed" push builds confidence it actually happened.
4. **Don't skip-if-already-on** — stale device state has caused us pain before (the heartbeat lost-update bug); the device treats a repeated command as a no-op, so always dispatch.
5. **Kill switch** — the `enabled` flag doubles as instant off; also add `schedule.execution.enabled` config flag so the whole feature can be disabled without a deploy.
6. **Reuse, don't fork** — scheduled commands MUST go through `machine_commands`/`command_attempts` (rule 6: auditable) and `CommandProducer`. Never let the scheduler talk to RabbitMQ directly.

## 7. Risks / edge cases

| Risk | Mitigation |
|---|---|
| Scheduler double-fires on deploy overlap | `schedule_runs` unique constraint claims the slot |
| Device offline at fire time | 3 retries over 15 min, then FAILED push |
| Server down through the slot | 15-min catch-up on startup, else MISSED record |
| Wrong timezone | Store TZ per schedule; test IST + non-IST orgs |
| User edits schedule mid-window | Recompute next slot on update; runs table keyed by absolute slot |
| DST | Not relevant for IST, but `ZoneId`-based slot math handles it correctly anyway |

## 8. Effort estimate

| Phase | Size |
|---|---|
| 1 — CRUD + schema | S |
| 2 — Scheduler engine | M |
| 3 — Notifications | S |
| 4 — Mobile UI | M |
| 5 — Deploy/verify | S |

Say the word and I'll start Phase 1.
