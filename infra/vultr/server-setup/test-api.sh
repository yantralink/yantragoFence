#!/usr/bin/env bash
echo "=== Testing Login ==="
LOGIN_RESPONSE=$(curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yantrago.com","password":"password"}')
echo "Login response: $LOGIN_RESPONSE"

echo ""
echo "=== Testing API with token (if login succeeded) ==="
TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then
  echo "Token obtained: ${TOKEN:0:20}..."
  echo ""
  echo "=== Testing /api/v1/machines ==="
  curl -sk https://yantrago.com/api/v1/machines \
    -H "Authorization: Bearer $TOKEN" 2>&1 | head -5
  echo ""
  echo "=== Testing /api/v1/organizations ==="
  curl -sk https://yantrago.com/api/v1/organizations \
    -H "Authorization: Bearer $TOKEN" 2>&1 | head -5
else
  echo "No token in response"
fi
