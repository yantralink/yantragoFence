# Automatic Fence ON/OFF Scheduling — Setup & Verification Runbook

Companion to `docs/SCHEDULED_FENCE_ONOFF_IMPLEMENTATION.md`. Covers
deployment, configuration reference, the Phase-5 integration checklist with
concrete steps and SQL evidence queries, and rollback.

> **Scope note:** the live checklist requires the local stack
> (PostgreSQL + Redis + RabbitMQ via `infra/docker/docker-compose.dev.yml`),
> a running backend + gateway, and the fencing simulator. Execute it on a
> machine where those are available; nothing here deploys to any real
> environment without explicit approval.

---

## 1. What ships in this feature

| Component | Where | Applied by |
|---|---|---|
| `machine_schedules`, `schedule_runs`, `machine_commands.source/schedule_id`, RBAC seeding | `database/migrations/V55__machine_schedules.sql` | Flyway on backend start |
| Notification templates (en/hi/mr) + preference backfill | `database/migrations/V56__schedule_notification_templates.sql` | Flyway on backend start |
| Schedule CRUD API | `ScheduleController` — `/api/v1/machines/{id}/schedule[...]` | JWT + `schedule:read` / `schedule:write` |
| Scheduler engine | `ScheduleCommandScheduler` (30 s tick) + `ScheduleExecutionService` | `schedule.execution.enabled` |
| Notifications | existing outbox → inbox → push pipeline | `notification.event-families.*` |
| Mobile UI | `mobile/lib/features/schedules/` | released with the app |

## 2. Prerequisites

```powershell
# 1. Infra (PostgreSQL+PostGIS, Redis, RabbitMQ) — infra only, no app services
cd infra/docker
docker compose -f docker-compose.dev.yml up -d

# 2. Backend (applies V55 + V56 via Flyway on boot)
./gradlew :backend:bootRun

# 3. Device gateway
./gradlew :device-gateway:bootRun

# 4. Fencing simulator (port 5002) — one device
./gradlew :simulator:build -x test
java -jar simulator/build/libs/simulator-1.0.0.jar --simulator.enabled-protocols=fencing
```

Verify migrations applied:
```sql
SELECT version, description FROM flyway_schema_history
 WHERE version IN ('55','56') ORDER BY installed_rank;
```

## 3. Configuration reference (`backend/application.yml`)

| Key | Env var | Default | Meaning |
|---|---|---|---|
| `schedule.execution.enabled` | `SCHEDULE_EXECUTION_ENABLED` | `true` | **Kill switch** — disables all scheduled dispatch without a deploy |
| `schedule.tick-ms` | `SCHEDULE_TICK_MS` | `30000` | Scheduler tick interval |
| `schedule.max-attempts` | `SCHEDULE_MAX_ATTEMPTS` | `3` | Dispatch attempts per run (gateway itself has no retry) |
| `schedule.on-catchup-minutes` | `SCHEDULE_ON_CATCHUP_MINUTES` | `15` | Max lateness for a missed ON — older slots are MISSED, never fired (safety cap) |
| `schedule.retry-interval-minutes` | `SCHEDULE_RETRY_INTERVAL_MINUTES` | `5` | Re-dispatch spacing for failed runs |
| `schedule.dispatch-timeout-minutes` | `SCHEDULE_DISPATCH_TIMEOUT_MINUTES` | `5` | Stuck `DISPATCHED` runs → TIMEOUT via the reconciliation sweep |
| `schedule.runs-retention-days` | `SCHEDULE_RUNS_RETENTION_DAYS` | `90` | `schedule_runs` retention |
| `schedule.cleanup-cron` | `SCHEDULE_CLEANUP_CRON` | `0 0 3 * * *` | Daily cleanup time |

Other operational requirements: server clock NTP-synced; multi-instance
deployments need nothing extra — the `schedule_runs` unique constraint on
`(schedule_id, scheduled_slot, command_type)` is the cross-instance
claim.

## 4. Phase-5 integration checklist

Setup for every scenario: register the simulator device (IMEI) → bind to a
machine → create a schedule whose ON is 2–3 minutes ahead.

```bash
# Create/update a schedule (upsert): ON 18:00 / OFF 07:00 daily
curl -X PUT "$API/api/v1/machines/{id}/schedule" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"onTime":"18:00","offTime":"07:00","daysOfWeek":127,"timezone":"Asia/Kolkata","enabled":true}'
```

For each scenario, evidence lives in `schedule_runs` and `machine_commands`:

```sql
SELECT scheduled_slot, command_type, status, attempt_count, error
  FROM schedule_runs WHERE schedule_id = :sid ORDER BY scheduled_slot DESC;

SELECT id, command_type, status, source, attempt_count, last_error
  FROM machine_commands WHERE schedule_id = :sid ORDER BY created_at DESC;
```

| # | Scenario | How to reproduce | Expected result |
|---|---|---|---|
| 1 | Happy path | Schedule ON 2 min ahead; simulator online | At slot: run `DISPATCHED` → command `SENT/ACK/DONE` → run `ACKED`; `SCHEDULED_ON_EXECUTED` push + inbox |
| 2 | Cross-midnight semantics | ON Mon 18:00 / OFF 07:00, days={Mon}; check Tue | Tuesday has **no** ON; `nextOffAt` is Tue 07:00 — the OFF fires anyway (cycle semantics) |
| 3 | Response shape | `GET …/schedule` | `nextOnAt`/`nextOffAt` are server-computed UTC; `lastRun` populated |
| 4 | Device offline at fire time | Stop the simulator before the ON slot | Retries each `retry-interval-minutes` (max `max-attempts`) → run `FAILED` → `SCHEDULED_COMMAND_FAILED` push; **fence state never assumed changed** |
| 5 | Restart across a slot | Kill the backend over an ON slot; restart >15 min later | Stale ON → `MISSED` + `SCHEDULED_COMMAND_MISSED` push, **not** fired late |
| 6 | Restart across an OFF | Same, over an OFF slot whose ON was claimed | OFF fires late on restart (fail-safe toward de-energized) |
| 7 | No double execution | Restart mid-tick / observe two ticks at a slot | Exactly one `schedule_runs` row per `(schedule, slot, type)` — unique constraint; at most one dispatch chain |
| 8 | Disable mid-cycle | Fence ON; disable schedule between ON and OFF | The already-running OFF still fires (verified in `tick_disabledStillFiresPendingOff`); no further ON claims and no MISSED noise (`tick_disabledSkipsOn`) |
| 9 | Kill switch | `SCHEDULE_EXECUTION_ENABLED=false` + restart | Zero dispatches; CRUD API still works; `schedule_runs` untouched |
| 10 | Tenant isolation | Org B JWT on Org A's machine id | `403`/`404` on all five endpoints; unassigned machine → same |

Manual-command regression (guard): tap ON/OFF in the app → command works
with `source='USER'`; scheduled rows show `source='SCHEDULE'` + a
"Scheduled" badge in command history.

## 5. Rollback

- **Whole feature off (fastest):** set `SCHEDULE_EXECUTION_ENABLED=false`,
  restart. No new runs are claimed; manual control unaffected.
- **One machine:** `PATCH …/schedule/enabled {"enabled":false}` or delete
  the schedule.
- **Revert migrations:** V55/V56 are additive (new tables, columns,
  template/preference rows) — no destructive reversal needed to roll back
  code; the tables are simply unused if the feature ships disabled.
  Destructive drops would require explicit approval anyway (rule 14).

## 6. Mobile verification

```powershell
cd mobile
flutter gen-l10n          # regenerates l10n getters incl. schedule keys
flutter analyze lib/features/schedules lib/models/command.dart
flutter test test/features/schedules_test.dart
flutter test test/features/machine_detail_page_test.dart  # may need a machineScheduleProvider override in existing tests
```

In-app smoke: machine detail → "Set up automatic ON/OFF" → set times →
save → card shows times/days/toggle → Edit → change days → save →
history "Scheduled" badge after first run.

## 7. Known limitations / open questions (unchanged from the design doc)

- admin-web schedule management: out of scope (API is web-ready).
- Testcontainers-based integration tests for the multi-instance/restart
  scenarios: pending dependency approval (doc §8).
- Push deep-linking from `SCHEDULED_*` notifications to the machine page:
  `pushData.machineId` is already in the payload; wiring is a follow-up.
