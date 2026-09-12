-- V33: Add battery/charging/GSM state columns to devices table.
-- These columns store the LATEST known state from heartbeat/alarm packets.
-- Time-series history remains in battery_readings, voltage_readings, gsm_readings (V7).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Per AGENTS.md rule 19: UUIDs as primary keys (unchanged here — just adding columns).

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS battery_pct       DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS charging          BOOLEAN,
    ADD COLUMN IF NOT EXISTS gsm_signal        INTEGER,
    ADD COLUMN IF NOT EXISTS last_telemetry_at TIMESTAMPTZ;

COMMENT ON COLUMN devices.battery_pct IS 'Latest internal battery percentage (0-100) from heartbeat/alarm voltage level byte.';
COMMENT ON COLUMN devices.charging IS 'True when external power is connected (Terminal Info Bit2).';
COMMENT ON COLUMN devices.gsm_signal IS 'Latest GSM signal level (0-4) from heartbeat/alarm.';
COMMENT ON COLUMN devices.last_telemetry_at IS 'When the last telemetry-bearing packet (heartbeat/alarm) was received.';
