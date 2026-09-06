#!/usr/bin/env bash
echo "=== Testing admin user login ==="
curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@testwholesaler.com","password":"password123"}' 2>&1
