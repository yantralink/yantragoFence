#!/bin/bash
echo "=== Health check ==="
curl -s http://localhost:8080/actuator/health
echo ""

echo "=== Login ==="
LOGIN_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"9527028875","password":"123456","role":"CUSTOMER"}')
echo "Login response (first 300 chars):"
echo "$LOGIN_RESP" | head -c 300
echo ""

TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "Trying without role..."
  LOGIN_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"phone":"9527028875","password":"123456"}')
  echo "$LOGIN_RESP" | head -c 300
  echo ""
  TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)
fi

if [ -z "$TOKEN" ]; then
  echo "ERROR: No token"
  exit 1
fi

echo ""
echo "=== Machine detail ==="
curl -s http://localhost:8080/api/v1/machines/69f8dd7c-54f7-4d1f-9913-5ee34fb0f25e \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

echo ""
echo "=== Telemetry latest ==="
curl -s http://localhost:8080/api/v1/machines/69f8dd7c-54f7-4d1f-9913-5ee34fb0f25e/telemetry/latest \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
