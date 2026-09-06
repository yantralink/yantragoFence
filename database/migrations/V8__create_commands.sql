-- V8__create_commands.sql
-- machine_commands: command lifecycle records (audit trail of every command issued).
-- command_attempts: per-attempt tracking (retry, timeout, ack).
-- Per AGENTS.md rule 6: all machine commands must be auditable via these two tables.

CREATE TABLE IF NOT EXISTS machine_commands (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id        UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    device_id         UUID REFERENCES devices(id) ON DELETE SET NULL,
    issued_by         UUID REFERENCES users(id) ON DELETE SET NULL,
    command_type      VARCHAR(20) NOT NULL, -- ON | OFF
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | QUEUED | SENT | ACK | DONE | FAILED
    attempt_count     INTEGER NOT NULL DEFAULT 0,
    max_attempts      INTEGER NOT NULL DEFAULT 3,
    last_error        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ
);

COMMENT ON TABLE machine_commands IS 'Command lifecycle records. Audit trail of every command issued to a machine.';
COMMENT ON COLUMN machine_commands.status IS 'PENDING | QUEUED | SENT | ACK | DONE | FAILED';
COMMENT ON COLUMN machine_commands.command_type IS 'ON | OFF';

CREATE TABLE IF NOT EXISTS command_attempts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id        UUID NOT NULL REFERENCES machine_commands(id) ON DELETE CASCADE,
    attempt_number    INTEGER NOT NULL,
    status            VARCHAR(20) NOT NULL, -- QUEUED | SENT | ACK | FAILED | TIMEOUT
    error             TEXT,
    sent_at           TIMESTAMPTZ,
    acked_at          TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (command_id, attempt_number)
);

COMMENT ON TABLE command_attempts IS 'Per-attempt tracking for machine commands (retry, timeout, ack).';
COMMENT ON COLUMN command_attempts.status IS 'QUEUED | SENT | ACK | FAILED | TIMEOUT';
