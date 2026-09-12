#!/bin/bash
echo "=== Current device state in DB ==="
sudo -u postgres psql -d yantrago -c "SELECT id, imei, machine_id, battery_pct, charging, gsm_signal, last_telemetry_at, last_seen_at FROM devices WHERE imei='866221070980656';"

echo ""
echo "=== Latest battery_readings ==="
sudo -u postgres psql -d yantrago -c "SELECT battery_pct, recorded_at FROM battery_readings WHERE device_id IN (SELECT id FROM devices WHERE imei='866221070980656') ORDER BY recorded_at DESC LIMIT 5;"

echo ""
echo "=== Latest heartbeats from gateway log ==="
grep -i "heartbeat\|charging\|terminal" /opt/yantrago/gateway/logs/yantrago-gateway.log 2>/dev/null | tail -10

echo ""
echo "=== Device online status ==="
sudo -u postgres psql -d yantrago -c "SELECT id, imei, last_seen_at, is_online FROM devices WHERE imei='866221070980656';"
