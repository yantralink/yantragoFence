#!/bin/bash
echo "=== Check migration ==="
sudo -u postgres psql -d yantrago -c "SELECT version, description, success FROM flyway_schema_history WHERE version = '21';"

echo ""
echo "=== Check index definition ==="
sudo -u postgres psql -d yantrago -c "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_machines_org_serial';"

echo ""
echo "=== Check machines with empty serial_number ==="
sudo -u postgres psql -d yantrago -c "SELECT count(*) AS empty_serial_count FROM machines WHERE serial_number = '';"

echo ""
echo "=== Machine list ==="
sudo -u postgres psql -d yantrago -c "SELECT machine_id, name, organization_id, serial_number, status FROM machines ORDER BY created_at DESC LIMIT 10;"
