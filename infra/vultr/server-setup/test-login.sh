#!/usr/bin/env bash
echo "=== Testing login via browser API path ==="
curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yantrago.com","password":"password"}' 2>&1
echo ""
