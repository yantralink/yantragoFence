# YantraGO Database ERD

Entity relationship documentation for the YantraGO platform database.
All tables use UUID primary keys. Tenant-scoped tables include `organization_id`.

## Tenant & Auth

```
organizations (1) ──< users (N)
organizations (1) ──< roles (N)
roles (N) >──< permissions (N)    [role_permissions]
users (N) >──< roles (N)          [user_roles]
users (1) ──< refresh_tokens (N)
users (1) ──< login_sessions (N)
```

- `users.organization_id` is NULL for `super_admin` (platform-level) users.
- System roles (`super_admin`, `org_admin`, `operator`, `viewer`) have NULL `organization_id`.

## Customers & Machines

```
organizations (1) ──< customers (N)
organizations (1) ──< machines (N)
customers (1) ──< machines (N)            [machines.customer_id FK]
machines (1) ──< devices (N)
machines (N) >──< customers (N)           [machine_assignments, history-aware]
```

## Device State & Location

```
devices (1) ──(1) device_states           [upsert, one row per device]
devices (1) ──(1) device_locations        [upsert, one row per device]
devices (1) ──< location_history (N)      [partitioned by month, PostGIS]
```

- `device_locations.location_geo` is auto-populated via trigger from lat/lng.
- `location_history.location_geo` has GiST spatial indexes per partition.

## Telemetry (Time-Series, Partitioned by Month)

```
devices (1) ──< voltage_readings (N)
devices (1) ──< battery_readings (N)
devices (1) ──< gsm_readings (N)
```

All three are partitioned by `recorded_at` (RANGE, monthly).
PK for each: `(id, recorded_at)` — partition key must be in PK.

## Commands (Audit Trail)

```
machines (1) ──< machine_commands (N)
users (1) ──< machine_commands (N)        [issued_by]
machine_commands (1) ──< command_attempts (N)
```

- `machine_commands.status`: PENDING → QUEUED → SENT → ACK → DONE (or FAILED)
- `command_attempts.status`: QUEUED | SENT | ACK | FAILED | TIMEOUT

## Alerts

```
organizations (1) ──< alert_rules (N)
machines (1) ──< alert_rules (N)          [NULL = org-wide rule]
alert_rules (1) ──< alerts (N)
machines (1) ──< alerts (N)
users (1) ──< alerts (N)                  [acknowledged_by]
```

## Notifications

```
users (1) ──< notifications (N)
alerts (1) ──< notifications (N)
users (1) ──< notification_preferences (N)
```

- `notifications.channel`: PUSH | EMAIL | SMS | WHATSAPP
- `notifications.status`: PENDING | SENT | DELIVERED | FAILED

## Settings

```
machines (1) ──< machine_settings (N)     [UNIQUE(machine_id, setting_key)]
organizations (1) ──< system_settings (N) [NULL org = global setting]
```

## Recharges

```
devices (1) ──< recharges (N)
organizations (1) ──< recharges (N)
```

## Audit Logs (Partitioned by Quarter)

```
organizations (1) ──< audit_logs (N)      [organization_id nullable]
users (1) ──< audit_logs (N)             [user_id nullable]
```

- Partitioned by `created_at` (RANGE, quarterly).
- PK: `(id, created_at)` — partition key must be in PK.

## Partitioning Summary

| Table | Partition Key | Strategy | Retention |
|-------|---------------|----------|-----------|
| `location_history` | `recorded_at` | Monthly RANGE | 3 months hot, then archive |
| `voltage_readings` | `recorded_at` | Monthly RANGE | 3 months hot, then archive |
| `battery_readings` | `recorded_at` | Monthly RANGE | 3 months hot, then archive |
| `gsm_readings` | `recorded_at` | Monthly RANGE | 3 months hot, then archive |
| `audit_logs` | `created_at` | Quarterly RANGE | 12 months hot, then archive |

Partition management:
- `database/partitions/create_monthly_partitions.sql` — `create_monthly_partitions()` function
- `database/partitions/archive_old_partitions.sql` — `detach_old_partition()`, `archive_partitions_older_than()`

## Multi-Tenancy

Every tenant-scoped table has `organization_id UUID NOT NULL REFERENCES organizations(id)`.
Exceptions (no `organization_id`):
- `organizations` (it IS the tenant)
- `permissions` (global platform-level definitions)
- System roles (NULL `organization_id`)
- `super_admin` users (NULL `organization_id`)
- `audit_logs.organization_id` is nullable (some platform-level events have no tenant)

**Rule:** `organization_id` is always resolved from the JWT on the backend, never from request bodies (see AGENTS.md rule 8).
