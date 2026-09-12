#!/usr/bin/env bash
cd /tmp
echo "=== Device ==="
sudo -u postgres psql -d yantrago -c "SELECT id, imei, machine_id, organization_id, is_active, last_seen_at FROM devices WHERE imei = '866221071626621';"
echo "=== Recent device_locations ==="
sudo -u postgres psql -d yantrago -c "SELECT device_id, machine_id, latitude, longitude, speed, course, recorded_at, updated_at FROM device_locations WHERE device_id IN (SELECT id FROM devices WHERE imei = '866221071626621') ORDER BY updated_at DESC LIMIT 5;"
echo "=== Recent location_history ==="
sudo -u postgres psql -d yantrago -c "SELECT device_id, machine_id, latitude, longitude, speed, recorded_at, received_at FROM location_history WHERE device_id IN (SELECT id FROM devices WHERE imei = '866221071626621') ORDER BY received_at DESC LIMIT 5;"
echo "=== Machine for this device ==="
sudo -u postgres psql -d yantrago -c "SELECT m.id, m.machine_id, m.name, m.customer_id, d.imei FROM machines m JOIN devices d ON d.machine_id = m.id WHERE d.imei = '866221071626621';"
