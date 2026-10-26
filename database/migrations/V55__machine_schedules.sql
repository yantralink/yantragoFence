-- V55__machine_schedules.sql
-- Automatic fence ON/OFF scheduling (Scheduled Ignition feature).
--
-- machine_schedules: one schedule per machine (ON time, OFF time, days, timezone).
-- schedule_runs: per-slot execution records; the unique constraint on
--   (schedule_id, scheduled_slot, command_type) is the idempotency guard that
--   lets the scheduler tick every 30s safely across restarts and instances.
-- machine_commands gains source ('USER' | 'SCHEDULE') + schedule_id so
--   scheduled commands share the existing audit pipeline (rules 5, 6) and
--   remain distinguishable in command history.
--
-- Cycle semantics: days_of_week applies to the ON event; the paired OFF is
-- the next occurrence of off_time strictly after the ON instant (next
-- calendar day when off_time <= on_time).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Per AGENTS.md rule 19: UUIDs as primary keys.

CREATE TABLE IF NOT EXISTS machine_schedules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id       UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    on_time          TIME NOT NULL,                      -- local wall clock, e.g. 18:00
    off_time         TIME NOT NULL,                      -- e.g. 07:00 (may be <= on_time -> next day)
    days_of_week     SMALLINT NOT NULL DEFAULT 127,      -- bitmask Mon=1 ... Sun=64; 127 = daily; applies to ON
    timezone         VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Decision: one active schedule per machine.
CREATE UNIQUE INDEX IF NOT EXISTS uq_machine_schedules_machine ON machine_schedules(machine_id);
CREATE INDEX IF NOT EXISTS idx_machine_schedules_org ON machine_schedules(organization_id);
CREATE INDEX IF NOT EXISTS idx_machine_schedules_enabled ON machine_schedules(enabled) WHERE enabled;

COMMENT ON TABLE machine_schedules IS 'Automatic ON/OFF schedules for fencing machines (one per machine).';
COMMENT ON COLUMN machine_schedules.days_of_week IS 'Bitmask Mon=1 ... Sun=64; 127 = daily. Applies to the ON event; OFF is paired cyclically.';
COMMENT ON COLUMN machine_schedules.timezone IS 'IANA timezone id for wall-clock times (default Asia/Kolkata).';

CREATE TABLE IF NOT EXISTS schedule_runs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id      UUID NOT NULL REFERENCES machine_schedules(id) ON DELETE CASCADE,
    scheduled_slot   TIMESTAMPTZ NOT NULL,               -- the absolute fire instant (UTC)
    command_type     VARCHAR(10) NOT NULL,               -- ON | OFF
    command_id       UUID REFERENCES machine_commands(id) ON DELETE SET NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|DISPATCHED|ACKED|FAILED|MISSED
    error            TEXT,
    attempt_count    SMALLINT NOT NULL DEFAULT 0,        -- scheduler-owned retries (gateway has no retry)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (schedule_id, scheduled_slot, command_type)   -- idempotency / duplicate-execution guard
);

CREATE INDEX IF NOT EXISTS idx_schedule_runs_schedule ON schedule_runs(schedule_id, scheduled_slot DESC);
CREATE INDEX IF NOT EXISTS idx_schedule_runs_status ON schedule_runs(status) WHERE status IN ('PENDING','DISPATCHED');

COMMENT ON TABLE schedule_runs IS 'Execution records for scheduled ON/OFF slots. Unique (schedule_id, scheduled_slot, command_type) claims each slot once.';
COMMENT ON COLUMN schedule_runs.status IS 'PENDING | DISPATCHED | ACKED | FAILED | MISSED';

ALTER TABLE machine_commands ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE machine_commands ADD COLUMN IF NOT EXISTS schedule_id UUID REFERENCES machine_schedules(id) ON DELETE SET NULL;
COMMENT ON COLUMN machine_commands.source IS 'USER | SCHEDULE';

-- Permissions: granted dynamically to every role that already holds
-- command:read / command:write (admin, org_admin, super_admin, customer).
INSERT INTO permissions (name, description) VALUES
('schedule:read',  'View machine ON/OFF schedules'),
('schedule:write', 'Create, update, enable/disable machine ON/OFF schedules')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions cp ON cp.id = rp.permission_id AND cp.name = 'command:read'
JOIN permissions p ON p.name = 'schedule:read'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions cp ON cp.id = rp.permission_id AND cp.name = 'command:write'
JOIN permissions p ON p.name = 'schedule:write'
ON CONFLICT DO NOTHING;
