#!/usr/bin/env bash
set -euo pipefail

# YantraGO Database Seed Script
# Runs Flyway migrations and seeds initial data (organizations, users, sample machines)
# Usage: ./infra/scripts/seed-db.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== YantraGO Database Seed ==="

# Check if PostgreSQL is accessible
if ! pg_isready -h localhost -p 5432 -U yantrago 2>/dev/null; then
    echo "ERROR: PostgreSQL is not running on localhost:5432"
    echo "Start it with: docker compose -f infra/docker/docker-compose.dev.yml up -d postgres"
    exit 1
fi

# Run Flyway migrations via the backend
echo ">>> Running Flyway migrations..."
cd "$PROJECT_ROOT"
./gradlew :backend:bootRun --args='--spring.profiles.active=dev --spring.flyway.clean-disabled=false' &
BACKEND_PID=$!

# Wait for migrations to complete
echo ">>> Waiting for migrations (30s)..."
sleep 30
kill $BACKEND_PID 2>/dev/null || true

# Seed initial data
echo ">>> Seeding initial data..."
export PGPASSWORD=yantrago

# Create default organization
psql -h localhost -p 5432 -U yantrago -d yantrago <<'SQL'
-- Default organization
INSERT INTO organizations (id, name, slug, created_at)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'YantraGO Demo', 'yantrago-demo', NOW())
ON CONFLICT (slug) DO NOTHING;

-- Admin user (password: admin123 — bcrypt hash)
INSERT INTO users (id, organization_id, email, password_hash, role, created_at)
VALUES (
    'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'admin@yantrago.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqJ3kqZBm.YqjQGmT6rJxqBmY.bKjm',
    'ADMIN',
    NOW()
)
ON CONFLICT (email) DO NOTHING;

-- Sample device
INSERT INTO devices (id, organization_id, imei, name, protocol_type, status, created_at)
VALUES (
    'c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    '867010070113452',
    'Demo Fencing Machine #1',
    'YANTRAGO_FENCING',
    'OFFLINE',
    NOW()
)
ON CONFLICT (imei) DO NOTHING;
SQL

echo ""
echo "=== Seed complete ==="
echo "Admin login: admin@yantrago.com / admin123"
echo "Sample device IMEI: 867010070113452"
