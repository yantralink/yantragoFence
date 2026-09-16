-- ACC (ignition) status on the devices table.
-- Updated by TelemetryConsumer from TelemetryMessage.ignitionOn (ConcoxV5
-- heartbeat 0x13 Terminal Info Bit1 / GPS 0x22 ACC byte / alarm 0x26).
-- NULL = unknown (no ACC-capable packet received yet, or legacy device).
ALTER TABLE devices ADD COLUMN ignition_on BOOLEAN;
