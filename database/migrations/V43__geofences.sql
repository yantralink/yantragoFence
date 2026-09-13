-- V43: Geofences table for geo-fence breach detection (Phase 10).
--
-- One active geofence per machine. When a GPS location packet arrives,
-- the backend checks if the machine is within its geofence radius.
-- If outside, a GEOFENCE_BREACH alert is generated.
--
-- Per AGENTS.md rule 14: schema changes via Flyway migrations only.
-- Per AGENTS.md rule 19: UUIDs as primary keys.

CREATE TABLE IF NOT EXISTS geofences (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    machine_id      UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    radius_meters   INTEGER NOT NULL DEFAULT 100,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    center          GEOGRAPHY(POINT, 4326),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

COMMENT ON TABLE geofences IS 'Geo-fence boundaries for theft detection. One active geofence per machine.';
COMMENT ON COLUMN geofences.center IS 'PostGIS geography point — auto-populated from latitude/longitude.';
COMMENT ON COLUMN geofences.radius_meters IS 'Radius in meters. Machine outside this radius triggers GEOFENCE_BREACH.';

-- One active geofence per machine (partial unique index)
CREATE UNIQUE INDEX idx_geofences_machine_active
    ON geofences(machine_id)
    WHERE is_active = true;

CREATE INDEX idx_geofences_org_machine ON geofences(organization_id, machine_id);
CREATE INDEX idx_geofences_center ON geofences USING GIST(center);

-- Trigger to auto-populate center from latitude/longitude on insert/update
CREATE OR REPLACE FUNCTION trg_geofences_set_center()
RETURNS TRIGGER AS $$
BEGIN
    NEW.center := ST_MakePoint(NEW.longitude, NEW.latitude)::GEOGRAPHY(POINT, 4326);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_geofences_center
    BEFORE INSERT OR UPDATE OF latitude, longitude ON geofences
    FOR EACH ROW
    EXECUTE FUNCTION trg_geofences_set_center();
