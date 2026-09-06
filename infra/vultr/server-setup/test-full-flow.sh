#!/usr/bin/env bash
echo "=== Testing organization creation ==="
LOGIN=$(curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yantrago.com","password":"password"}')
TOKEN=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

echo "=== Creating test organization ==="
curl -sk -X POST https://yantrago.com/api/v1/organizations \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Test Wholesaler","slug":"test-wholesaler"}' 2>&1
echo ""

echo "=== Listing organizations ==="
curl -sk https://yantrago.com/api/v1/organizations \
  -H "Authorization: Bearer $TOKEN" 2>&1
echo ""

echo "=== Listing roles ==="
curl -sk https://yantrago.com/api/v1/roles \
  -H "Authorization: Bearer $TOKEN" 2>&1
echo ""

echo "=== Creating admin user ==="
ORG_ID=$(curl -sk https://yantrago.com/api/v1/organizations \
  -H "Authorization: Bearer $TOKEN" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "Org ID: $ORG_ID"
curl -sk -X POST https://yantrago.com/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"admin@testwholesaler.com\",\"password\":\"password123\",\"fullName\":\"Test Admin\",\"organizationId\":\"$ORG_ID\",\"roleName\":\"org_admin\"}" 2>&1
echo ""
