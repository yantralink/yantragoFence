#!/bin/bash
###############################################################################
# YantraGO Theft Detection — End-to-End Test Script
# Run on the production server: bash /tmp/test-theft-detection.sh
###############################################################################
set -e

API="http://localhost:8080/api/v1"
ADMIN_EMAIL="admin@testwholesaler.com"
ADMIN_PASS="password123"
MACHINE_ID="69f8dd7c-54f7-4d1f-9913-5ee34fb0f25e"

echo "=========================================="
echo "  YantraGO Theft Detection Test Suite"
echo "=========================================="
echo ""

# ─── Login ─────────────────────────────────────────────────────────────────
echo ">>> [1/8] Logging in as admin..."
LOGIN=$(curl -s -X POST "$API/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASS\"}")
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "FAIL: Login failed. Response: $LOGIN"
  exit 1
fi
echo "PASS: Login successful"
echo ""

# ─── Test 1: List geofences (should be empty) ──────────────────────────────
echo ">>> [2/8] Listing geofences (should be empty)..."
RESP=$(curl -s -w "\n%{http_code}" "$API/geofences" -H "Authorization: Bearer $TOKEN")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" = "200" ]; then
  echo "PASS: GET /geofences returned 200"
  echo "  Body: $BODY"
else
  echo "FAIL: Expected 200, got $CODE"
  echo "  Body: $BODY"
fi
echo ""

# ─── Test 2: Create a geofence ─────────────────────────────────────────────
echo ">>> [3/8] Creating geofence for machine $MACHINE_ID..."
GEOFENCE_RESP=$(curl -s -X POST "$API/geofences" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineId\": \"$MACHINE_ID\",
    \"name\": \"Test Geofence - Office\",
    \"latitude\": 18.62203,
    \"longitude\": 73.70909,
    \"radiusMeters\": 200,
    \"isActive\": true
  }")
GEOFENCE_ID=$(echo "$GEOFENCE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

if [ -n "$GEOFENCE_ID" ]; then
  echo "PASS: Geofence created with ID: $GEOFENCE_ID"
else
  echo "NOTE: Could not create (may already exist). Fetching existing..."
  # Fetch existing geofence for this machine
  EXISTING=$(curl -s "$API/geofences/machine/$MACHINE_ID" -H "Authorization: Bearer $TOKEN")
  GEOFENCE_ID=$(echo "$EXISTING" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)
  if [ -n "$GEOFENCE_ID" ]; then
    echo "PASS: Using existing geofence ID: $GEOFENCE_ID"
  else
    echo "FAIL: No geofence found and could not create one"
    echo "  Response: $GEOFENCE_RESP"
    exit 1
  fi
fi
echo ""

# ─── Test 3: Get geofence by machine ID ────────────────────────────────────
echo ">>> [4/8] Fetching geofence by machine ID..."
RESP=$(curl -s -w "\n%{http_code}" "$API/geofences/machine/$MACHINE_ID" -H "Authorization: Bearer $TOKEN")
CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | head -n -1)
if [ "$CODE" = "200" ]; then
  echo "PASS: GET /geofences/machine/{id} returned 200"
  echo "  Body: $BODY"
else
  echo "FAIL: Expected 200, got $CODE"
fi
echo ""

# ─── Test 4: Verify geofence in database ───────────────────────────────────
echo ">>> [5/8] Verifying geofence in database..."
sudo -u postgres psql -d yantrago -c "SELECT id, name, latitude, longitude, radius_meters, is_active, ST_AsText(center) AS center FROM geofences WHERE machine_id = '$MACHINE_ID';"
echo ""

# ─── Test 5: Simulate GPS OUTSIDE geofence (trigger breach) ─────────────────
echo ">>> [6/8] Simulating GPS OUTSIDE geofence (should trigger GEOFENCE_BREACH)..."
echo "  Geofence center: 18.62203, 73.70909 (radius: 200m)"
echo "  Sending GPS:     18.62500, 73.71500 (~700m away — OUTSIDE)"

# Insert a GPS location outside the geofence directly into device_locations
# Note: This updates the DB directly. Breach detection runs via RabbitMQ
# when a real GPS packet arrives from the device. To trigger breach detection
# end-to-end, send a GPS packet via the gateway or call the breach check API.
sudo -u postgres psql -d yantrago -c "
UPDATE device_locations SET
  latitude = 18.62500,
  longitude = 73.71500,
  speed = 0,
  recorded_at = now()
WHERE device_id = (SELECT id FROM devices WHERE machine_id = '$MACHINE_ID' AND is_active = true LIMIT 1);
"
echo "  GPS location updated in device_locations (OUTSIDE geofence)"
echo ""

# Manually trigger breach check by calling the same SQL the service uses
echo "  Manually checking distance from geofence center..."
sudo -u postgres psql -d yantrago -c "
SELECT id, name, radius_meters,
  ROUND(ST_Distance(center, ST_MakePoint(73.71500, 18.62500)::geography)::numeric, 1) AS distance_meters,
  CASE WHEN ST_Distance(center, ST_MakePoint(73.71500, 18.62500)::geography) > radius_meters
       THEN 'OUTSIDE — BREACH!' ELSE 'INSIDE — OK' END AS status
FROM geofences WHERE machine_id = '$MACHINE_ID' AND is_active = true;
"
echo ""

# Check if breach alert was created (may need a GPS packet via gateway)
echo "  Checking for GEOFENCE_BREACH alerts..."
sudo -u postgres psql -d yantrago -c "SELECT id, alert_type, incident_state, severity, message, created_at FROM alerts WHERE machine_id = '$MACHINE_ID' AND alert_type = 'GEOFENCE_BREACH' ORDER BY created_at DESC LIMIT 5;"
echo ""

# ─── Test 6: Simulate GPS BACK INSIDE geofence (resolve breach) ─────────────
echo ">>> [7/8] Simulating GPS BACK INSIDE geofence (should resolve breach)..."
echo "  Sending GPS: 18.62210, 73.70915 (~10m from center — INSIDE)"

sudo -u postgres psql -d yantrago -c "
UPDATE device_locations SET
  latitude = 18.62210,
  longitude = 73.70915,
  speed = 0,
  recorded_at = now()
WHERE device_id = (SELECT id FROM devices WHERE machine_id = '$MACHINE_ID' AND is_active = true LIMIT 1);
"
echo "  GPS location updated to INSIDE geofence"
echo "  Manually checking distance..."
sudo -u postgres psql -d yantrago -c "
SELECT id, name, radius_meters,
  ROUND(ST_Distance(center, ST_MakePoint(73.70915, 18.62210)::geography)::numeric, 1) AS distance_meters,
  CASE WHEN ST_Distance(center, ST_MakePoint(73.70915, 18.62210)::geography) > radius_meters
       THEN 'OUTSIDE — BREACH!' ELSE 'INSIDE — OK' END AS status
FROM geofences WHERE machine_id = '$MACHINE_ID' AND is_active = true;
"
echo ""

# ─── Test 7: Create MACHINE_MOVING alert rule ───────────────────────────────
echo ">>> [8/8] Creating MACHINE_MOVING alert rule (speed > 5 km/h)..."
RULE_RESP=$(curl -s -X POST "$API/alert-rules" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"machineId\": \"$MACHINE_ID\",
    \"name\": \"Theft Detection - Speed > 5 km/h\",
    \"alertType\": \"MACHINE_MOVING\",
    \"conditionConfig\": {\"metric\": \"speed\", \"operator\": \"GT\", \"threshold\": 5},
    \"severity\": \"CRITICAL\",
    \"isActive\": true,
    \"sustainMinutes\": 0,
    \"recoveryMinutes\": 1
  }")
echo "  Alert rule response: $RULE_RESP"
echo ""

# ─── Summary ───────────────────────────────────────────────────────────────
echo "=========================================="
echo "  Test Summary"
echo "=========================================="
echo ""
echo "Geofence CRUD:        Tested (create, list, get by machine)"
echo "Geofence breach:      GPS outside/inside simulated in DB"
echo "Movement alert rule:  Created (speed > 5 km/h)"
echo ""
echo "To test breach detection end-to-end:"
echo "  1. Send a real GPS packet via the gateway with location outside geofence"
echo "  2. Check alerts table for GEOFENCE_BREACH incident"
echo "  3. Check notification_inbox for the breach notification"
echo ""
echo "To test movement detection end-to-end:"
echo "  1. Send a real GPS packet via the gateway with speed > 5 km/h"
echo "  2. Check alerts table for MACHINE_MOVING incident"
echo "  3. Check notification_inbox for the movement notification"
echo ""
echo "Admin web UI:"
echo "  1. Visit https://yantrago.com/geofences"
echo "  2. Login as admin@testwholesaler.com"
echo "  3. Verify geofence table shows 'Test Geofence - Office'"
echo "  4. Test create/edit/delete/activate/deactivate"
echo ""
echo "Mobile app:"
echo "  1. Install the APK on emulator (already done)"
echo "  2. Login as customer"
echo "  3. Check notification inbox for GEOFENCE_BREACH / MACHINE_MOVING"
echo "  4. Test 'Geofence' and 'Movement' filter chips"
echo "  5. Tap a notification to see detail page with danger tone"
