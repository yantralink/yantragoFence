-- V3__seed_sample_customers.sql
-- Seed sample customers for the demo tenant.

BEGIN;

INSERT INTO customers (id, organization_id, name, email, phone, address, latitude, longitude, is_active)
VALUES
    (
        'a0000000-0000-0000-0000-000000000010',
        'a0000000-0000-0000-0000-000000000001',
        'Ramesh Patel',
        'ramesh.patel@example.com',
        '+919876543210',
        'Plot 12, Agricultural Zone, Anand, Gujarat',
        22.5645,
        72.0012,
        TRUE
    ),
    (
        'a0000000-0000-0000-0000-000000000011',
        'a0000000-0000-0000-0000-000000000001',
        'Suresh Kumar',
        'suresh.kumar@example.com',
        '+919876543211',
        'Survey 45, Farm Road, Nashik, Maharashtra',
        19.9975,
        73.7898,
        TRUE
    ),
    (
        'a0000000-0000-0000-0000-000000000012',
        'a0000000-0000-0000-0000-000000000001',
        'Lakshmi Farms',
        'contact@lakshmifarms.example.com',
        '+919876543212',
        'Plot 7-9, Cooperative Society, Coimbatore, Tamil Nadu',
        11.0168,
        76.9558,
        TRUE
    )
ON CONFLICT DO NOTHING;

COMMIT;
