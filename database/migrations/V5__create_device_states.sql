-- V5__create_device_states.sql
-- device_states: last-known state per device (upsert pattern, one row per device).
-- device_locations: current GPS location per device (upsert pattern, one row per device).

CREATE TABLE IF NOT EXISTS device_states (
    device_id         UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    online            BOOLEAN NOT NULL DEFAULT FALSE,
    relay_state       VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN', -- ON | OFF | UNKNOWN
    voltage           DOUBLE PRECISION,
    battery           DOUBLE PRECISION,
    gsm_signal        INTEGER,
    last_seen_at      TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE device_states IS 'Last-known state per device (upsert). One row per device.';
COMMENT ON COLUMN device_states.relay_state IS 'ON | OFF | UNKNOWN';

CREATE TABLE IF NOT EXISTS device_locations (
    device_id         UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id        UUID REFERENCES machines(id) ON DELETE SET NULL,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    speed             DOUBLE PRECISION,
    course            DOUBLE PRECISION,
    location_geo      GEOGRAPHY(POINT, 4326),
    recorded_at       TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE device_locations IS 'Current GPS location per device (upsert). One row per device.';
COMMENT ON COLUMN device_locations.location_geo IS 'PostGIS geography point for spatial queries.';

-- Trigger to auto-populate location_geo from latitude/longitude on insert/update.
CREATE OR REPLACE FUNCTION trg_device_locations_set_geo()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location_geo := ST_MakePoint(NEW.longitude, NEW.latitude)::GEOGRAPHY(POINT, 4326);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_device_locations_geo
    BEFORE INSERT OR UPDATE OF latitude, longitude ON device_locations
    FOR EACH ROW
    EXECUTE FUNCTION trg_device_locations_set_geo();
