-- V4__seed_sample_machines.sql
-- Seed sample machines, devices, and assignments for the demo tenant.

BEGIN;

-- ===== Machines =====
INSERT INTO machines (id, organization_id, customer_id, name, serial_number, model, status, is_online)
VALUES
    (
        'a0000000-0000-0000-0000-000000000020',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000010',
        'YantraGO Fence Unit #1',
        'YG-2026-0001',
        'YG-Pro-2',
        'ACTIVE',
        TRUE
    ),
    (
        'a0000000-0000-0000-0000-000000000021',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000010',
        'YantraGO Fence Unit #2',
        'YG-2026-0002',
        'YG-Pro-2',
        'ACTIVE',
        FALSE
    ),
    (
        'a0000000-0000-0000-0000-000000000022',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000011',
        'YantraGO Fence Unit #3',
        'YG-2026-0003',
        'YG-Lite-1',
        'ACTIVE',
        TRUE
    )
ON CONFLICT DO NOTHING;

-- ===== Devices =====
INSERT INTO devices (id, organization_id, machine_id, imei, sim_number, protocol_type, firmware_version, is_active)
VALUES
    (
        'a0000000-0000-0000-0000-000000000030',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000020',
        '861234500000001',
        '+919876500001',
        'CONCOX_V5',
        'v2.3.1',
        TRUE
    ),
    (
        'a0000000-0000-0000-0000-000000000031',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000021',
        '861234500000002',
        '+919876500002',
        'CONCOX_V5',
        'v2.3.1',
        TRUE
    ),
    (
        'a0000000-0000-0000-0000-000000000032',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000022',
        '861234500000003',
        '+919876500003',
        'JT808',
        'v1.8.0',
        TRUE
    )
ON CONFLICT DO NOTHING;

-- ===== Machine assignments (current) =====
INSERT INTO machine_assignments (id, organization_id, machine_id, customer_id, assigned_at)
VALUES
    (
        'a0000000-0000-0000-0000-000000000040',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000020',
        'a0000000-0000-0000-0000-000000000010',
        now()
    ),
    (
        'a0000000-0000-0000-0000-000000000041',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000021',
        'a0000000-0000-0000-0000-000000000010',
        now()
    ),
    (
        'a0000000-0000-0000-0000-000000000042',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000022',
        'a0000000-0000-0000-0000-000000000011',
        now()
    )
ON CONFLICT DO NOTHING;

-- ===== Device states (initial) =====
INSERT INTO device_states (device_id, organization_id, online, relay_state, voltage, battery, gsm_signal, last_seen_at)
VALUES
    (
        'a0000000-0000-0000-0000-000000000030',
        'a0000000-0000-0000-0000-000000000001',
        TRUE, 'ON', 12.4, 85.0, 22, now()
    ),
    (
        'a0000000-0000-0000-0000-000000000031',
        'a0000000-0000-0000-0000-000000000001',
        FALSE, 'OFF', NULL, NULL, NULL, NULL
    ),
    (
        'a0000000-0000-0000-0000-000000000032',
        'a0000000-0000-0000-0000-000000000001',
        TRUE, 'ON', 12.6, 92.0, 18, now()
    )
ON CONFLICT (device_id) DO NOTHING;

-- ===== Device locations (initial) =====
INSERT INTO device_locations (device_id, organization_id, machine_id, latitude, longitude, speed, course, recorded_at)
VALUES
    (
        'a0000000-0000-0000-0000-000000000030',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000020',
        22.5645, 72.0012, 0.0, 0.0, now()
    ),
    (
        'a0000000-0000-0000-0000-000000000032',
        'a0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000022',
        19.9975, 73.7898, 0.0, 0.0, now()
    )
ON CONFLICT (device_id) DO NOTHING;

COMMIT;
