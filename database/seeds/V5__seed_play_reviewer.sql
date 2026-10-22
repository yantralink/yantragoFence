-- V5__seed_play_reviewer.sql
-- PLAY Task 13.5: least-privilege test account for Google Play reviewers.
--
-- Creates a DEDICATED organization ("YantraGO Play Review") containing a
-- single customer account with two synthetic machines, devices, live-ish
-- telemetry and a short location trail. The account uses the 'customer'
-- role only — no admin, super-admin, or cross-tenant access — and all
-- data is synthetic (no real fencing equipment or customer PII).
--
-- Self-contained: applies cleanly on its own (depends only on roles
-- created by migrations) — run with:
--   REVIEWER_PASSWORD='<chosen password>' ./infra/scripts/seed-play-reviewer.sh
--
-- The script injects a BCrypt hash into __REVIEWER_PASSWORD_BCRYPT__.
-- The plaintext password is NEVER committed — provide it to Google only
-- in the Play Console "App access" credentials section.

BEGIN;

-- ===== Dedicated review organization =====
INSERT INTO organizations (id, name, slug, white_label_config, is_active)
VALUES (
    'b0000000-0000-0000-0000-000000000001',
    'YantraGO Play Review',
    'yantrago-play-review',
    '{"primaryColor": "#0B65C2"}'::JSONB,
    TRUE
)
ON CONFLICT DO NOTHING;

-- ===== Reviewer user (customer role — least privilege) =====
INSERT INTO users (id, organization_id, email, password_hash, full_name, phone, is_active)
VALUES (
    'b0000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000001',
    'play.review@yantrago.com',
    '__REVIEWER_PASSWORD_BCRYPT__',
    'Play Reviewer',
    '+919999900001',
    TRUE
)
ON CONFLICT DO NOTHING;

-- Customer role (created by migration V18, always present).
INSERT INTO user_roles (user_id, role_id)
VALUES (
    'b0000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000014'
)
ON CONFLICT DO NOTHING;

-- ===== Linked customer record =====
INSERT INTO customers (id, organization_id, user_id, name, email, phone, address, latitude, longitude, is_active)
VALUES (
    'b0000000-0000-0000-0000-000000000010',
    'b0000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000002',
    'Play Review Farm',
    'play.review@yantrago.com',
    '+919999900001',
    'Demo Plot 1, Review Road, Pune, Maharashtra (synthetic)',
    18.5204,
    73.8567,
    TRUE
)
ON CONFLICT DO NOTHING;

-- ===== Synthetic machines =====
INSERT INTO machines (id, organization_id, customer_id, name, serial_number, model, status, is_online, machine_id)
VALUES
    (
        'b0000000-0000-0000-0000-000000000020',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000010',
        'Review Fence Unit A',
        'YG-REVIEW-0001',
        'YG-Pro-2',
        'ACTIVE',
        TRUE,
        'YG-REVIEW-A'
    ),
    (
        'b0000000-0000-0000-0000-000000000021',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000010',
        'Review Fence Unit B',
        'YG-REVIEW-0002',
        'YG-Lite-1',
        'ACTIVE',
        FALSE,
        'YG-REVIEW-B'
    )
ON CONFLICT DO NOTHING;

-- ===== Devices =====
INSERT INTO devices (id, organization_id, machine_id, imei, sim_number, protocol_type, firmware_version, is_active)
VALUES
    (
        'b0000000-0000-0000-0000-000000000030',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000020',
        '999999000000001',
        '+919999900011',
        'CONCOX_V5',
        'v2.3.1',
        TRUE
    ),
    (
        'b0000000-0000-0000-0000-000000000031',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000021',
        '999999000000002',
        '+919999900012',
        'CONCOX_V5',
        'v2.3.1',
        TRUE
    )
ON CONFLICT DO NOTHING;

-- ===== Current assignments =====
INSERT INTO machine_assignments (id, organization_id, machine_id, customer_id, assigned_at)
VALUES
    (
        'b0000000-0000-0000-0000-000000000040',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000020',
        'b0000000-0000-0000-0000-000000000010',
        now()
    ),
    (
        'b0000000-0000-0000-0000-000000000041',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000021',
        'b0000000-0000-0000-0000-000000000010',
        now()
    )
ON CONFLICT DO NOTHING;

-- ===== Device states (A online/energized, B offline) =====
INSERT INTO device_states (device_id, organization_id, online, relay_state, voltage, battery, gsm_signal, last_seen_at)
VALUES
    (
        'b0000000-0000-0000-0000-000000000030',
        'b0000000-0000-0000-0000-000000000001',
        TRUE, 'ON', 12.4, 88.0, 21, now()
    ),
    (
        'b0000000-0000-0000-0000-000000000031',
        'b0000000-0000-0000-0000-000000000001',
        FALSE, 'OFF', 12.1, 76.0, 17, now() - interval '3 hours'
    )
ON CONFLICT (device_id) DO NOTHING;

-- ===== Synthetic alerts (Alerts screen) =====
INSERT INTO alerts (id, organization_id, machine_id, device_id, alert_type, severity, message, is_acknowledged, triggered_at)
VALUES
    (
        'b0000000-0000-0000-0000-000000000050',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000020',
        'b0000000-0000-0000-0000-000000000030',
        'VOLTAGE_DROP',
        'WARNING',
        'Fence voltage dropped below threshold on Review Fence Unit A.',
        FALSE,
        now() - interval '50 minutes'
    ),
    (
        'b0000000-0000-0000-0000-000000000051',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000021',
        'b0000000-0000-0000-0000-000000000031',
        'DEVICE_OFFLINE',
        'INFO',
        'Review Fence Unit B went offline.',
        FALSE,
        now() - interval '3 hours'
    )
ON CONFLICT DO NOTHING;

-- ===== Inbox notifications (Notifications screen) =====
-- Deterministic event_ids + UNIQUE(event_id, user_id) keep re-runs idempotent.
INSERT INTO notification_inbox
    (organization_id, user_id, alert_id, event_id, alert_type, severity,
     incident_state, title, body, machine_id)
VALUES
    (
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000002',
        'b0000000-0000-0000-0000-000000000050',
        'b0000000-0000-0000-0000-000000000060',
        'VOLTAGE_DROP',
        'WARNING',
        'OPEN',
        'Low fence voltage',
        'Fence voltage dropped below threshold on Review Fence Unit A.',
        'b0000000-0000-0000-0000-000000000020'
    ),
    (
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000002',
        'b0000000-0000-0000-0000-000000000051',
        'b0000000-0000-0000-0000-000000000061',
        'DEVICE_OFFLINE',
        'INFO',
        'OPEN',
        'Device offline',
        'Review Fence Unit B went offline.',
        'b0000000-0000-0000-0000-000000000021'
    )
ON CONFLICT DO NOTHING;

-- ===== Current positions =====
INSERT INTO device_locations (device_id, organization_id, machine_id, latitude, longitude, speed, course, recorded_at)
VALUES
    (
        'b0000000-0000-0000-0000-000000000030',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000020',
        18.5204, 73.8567, 0.0, 0.0, now()
    ),
    (
        'b0000000-0000-0000-0000-000000000031',
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000021',
        18.5312, 73.8445, 0.0, 0.0, now() - interval '3 hours'
    )
ON CONFLICT (device_id) DO NOTHING;

-- ===== Short location trail for Unit A (map + history screens) =====
-- location_history PK is (id, recorded_at) with auto-generated ids, so
-- ON CONFLICT cannot dedupe — clear prior seeded rows to stay re-runnable.
DELETE FROM location_history
WHERE device_id IN (
    'b0000000-0000-0000-0000-000000000030',
    'b0000000-0000-0000-0000-000000000031'
);

INSERT INTO location_history
    (organization_id, device_id, machine_id, imei, latitude, longitude,
     speed, course, location_geo, recorded_at)
VALUES
    (
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000030',
        'b0000000-0000-0000-0000-000000000020',
        '999999000000001',
        18.5190, 73.8550, 4.2, 45.0,
        ST_GeogFromText('POINT(73.8550 18.5190)'),
        now() - interval '95 minutes'
    ),
    (
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000030',
        'b0000000-0000-0000-0000-000000000020',
        '999999000000001',
        18.5196, 73.8558, 3.8, 50.0,
        ST_GeogFromText('POINT(73.8558 18.5196)'),
        now() - interval '60 minutes'
    ),
    (
        'b0000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000030',
        'b0000000-0000-0000-0000-000000000020',
        '999999000000001',
        18.5204, 73.8567, 0.0, 0.0,
        ST_GeogFromText('POINT(73.8567 18.5204)'),
        now() - interval '20 minutes'
    );

COMMIT;
