#!/bin/bash
# Login
LOGIN_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"9527028875","password":"123456","role":"CUSTOMER"}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null)

echo "=== Machine detail (NOW) ==="
curl -s http://localhost:8080/api/v1/machines/69f8dd7c-54f7-4d1f-9913-5ee34fb0f25e \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

echo ""
echo "=== Telemetry latest (NOW) ==="
curl -s http://localhost:8080/api/v1/machines/69f8dd7c-54f7-4d1f-9913-5ee34fb0f25e/telemetry/latest \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
