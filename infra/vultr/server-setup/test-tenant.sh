#!/usr/bin/env bash
echo "=== Login as admin user ==="
LOGIN=$(curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@testwholesaler.com","password":"password123"}')
TOKEN=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "Token obtained"

echo ""
echo "=== Creating customer ==="
curl -sk -X POST https://yantrago.com/api/v1/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"John Farmer","email":"john@example.com","phoneNumber":"+91 98765 43210","address":"Farm Road, Village"}' 2>&1
echo ""

echo ""
echo "=== Listing customers ==="
curl -sk https://yantrago.com/api/v1/customers \
  -H "Authorization: Bearer $TOKEN" 2>&1
echo ""

echo ""
echo "=== Creating machine ==="
curl -sk -X POST https://yantrago.com/api/v1/machines \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"imei":"123456789012345","name":"Fence Unit #1","protocolType":"YANTRAGO_FENCING","simNumber":"+91 98765 43210"}' 2>&1
echo ""
