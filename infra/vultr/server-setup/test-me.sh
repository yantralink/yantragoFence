#!/usr/bin/env bash
echo "=== Testing /auth/me ==="
LOGIN=$(curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yantrago.com","password":"password"}')
TOKEN=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "Token: ${TOKEN:0:30}..."
echo ""
echo "=== Calling /auth/me ==="
curl -sk https://yantrago.com/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" 2>&1
