-- V35: Add external voltage column to devices table.
-- Stores the LATEST external power voltage from the 0x94 info packet
-- (information type 0x00 = external voltage). Time-series history remains
-- in voltage_readings (V7).
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Per AGENTS.md rule 19: UUIDs as primary keys (unchanged here — just adding a column).

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS voltage DOUBLE PRECISION;

COMMENT ON COLUMN devices.voltage IS 'Latest external power voltage in volts from 0x94 info packet (type 0x00).';
