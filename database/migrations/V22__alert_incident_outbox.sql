-- V22__alert_incident_outbox.sql
-- Phase 1 Notification: additive incident lifecycle, outbox, and idempotency schema.
-- Does not alter existing columns or drop anything. All changes are additive.

-- ===== 1. Extend alerts with incident lifecycle fields =====
-- Tracks one open incident per (organization_id, machine_id, alert_type) key.
-- State is OPEN -> RESOLVED. Acknowledgement is orthogonal to state.

ALTER TABLE alerts ADD COLUMN IF NOT EXISTS incident_state VARCHAR(20) DEFAULT 'OPEN'; -- OPEN | RESOLVED
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS incident_key VARCHAR(255);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS first_observed_at TIMESTAMPTZ;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS last_observed_at TIMESTAMPTZ;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS occurrence_count INTEGER NOT NULL DEFAULT 1;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS observed_value DOUBLE PRECISION;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS observed_unit VARCHAR(20);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS rule_version INTEGER;

COMMENT ON COLUMN alerts.incident_state IS 'OPEN | RESOLVED. Acknowledgement is orthogonal to state.';
COMMENT ON COLUMN alerts.incident_key IS 'Stable key: org_id:machine_id:alert_type. One open incident per key.';
COMMENT ON COLUMN alerts.occurrence_count IS 'Number of times this condition has been observed while open.';

-- Backfill incident_state and first_observed_at for existing rows
UPDATE alerts SET incident_state = 'OPEN' WHERE incident_state IS NULL;
UPDATE alerts SET first_observed_at = triggered_at WHERE first_observed_at IS NULL;
UPDATE alerts SET last_observed_at = triggered_at WHERE last_observed_at IS NULL;

ALTER TABLE alerts ALTER COLUMN incident_state SET NOT NULL;

-- Partial unique index: only one OPEN incident per key
CREATE UNIQUE INDEX IF NOT EXISTS idx_alerts_open_incident
    ON alerts (organization_id, machine_id, alert_type)
    WHERE incident_state = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_alerts_incident_state ON alerts (incident_state);
CREATE INDEX IF NOT EXISTS idx_alerts_last_observed_at ON alerts (last_observed_at);

-- ===== 2. Event outbox =====
-- Committed in the same DB transaction as the alert transition.
-- Publisher claims rows, publishes to RabbitMQ, marks published.

CREATE TABLE IF NOT EXISTS event_outbox (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    event_type          VARCHAR(50) NOT NULL,        -- ALERT_TRANSITION | NOTIFICATION_DISPATCH
    schema_version      INTEGER NOT NULL DEFAULT 1,
    aggregate_id        UUID NOT NULL,                -- alert_id or notification_id
    event_id            UUID NOT NULL,                -- stable idempotency key
    payload             JSONB NOT NULL,               -- serialized message contract
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    publish_attempts    INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 10,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    claim_lease_until   TIMESTAMPTZ,                  -- worker lease for claiming
    published_at        TIMESTAMPTZ,                  -- NULL = not yet published
    UNIQUE (event_id)
);

COMMENT ON TABLE event_outbox IS 'Transactional outbox for reliable event publication after DB commit.';

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON event_outbox (next_attempt_at)
    WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON event_outbox (aggregate_id);

-- ===== 3. Processed events (idempotency / replay protection) =====
-- Consumers record processed event_id to prevent duplicate processing on redelivery.

CREATE TABLE IF NOT EXISTS processed_events (
    event_id            UUID PRIMARY KEY,
    event_type          VARCHAR(50) NOT NULL,
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE processed_events IS 'Consumer-side idempotency log. Prevents duplicate processing of redelivered events.';

-- ===== 4. Alert rule states (restart-safe evaluation windows) =====
-- Durable per-machine/rule observation state for sustain/recovery windows.

CREATE TABLE IF NOT EXISTS alert_rule_states (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    rule_id             UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    machine_id          UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    violation_started_at TIMESTAMPTZ,                  -- when condition first observed
    last_evaluated_at   TIMESTAMPTZ,                   -- last evaluation time
    last_value          DOUBLE PRECISION,             -- last observed value
    is_violating        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (rule_id, machine_id)
);

COMMENT ON TABLE alert_rule_states IS 'Per-machine/rule observation state for restart-safe sustain/recovery windows.';

CREATE INDEX IF NOT EXISTS idx_rule_states_org_machine
    ON alert_rule_states (organization_id, machine_id);
