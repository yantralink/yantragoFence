#!/bin/bash
echo "=== Login attempt 1 (CUSTOMER role) ==="
curl -sv -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"9527028875","password":"123456","role":"CUSTOMER"}' 2>&1 | tail -20

echo ""
echo "=== Login attempt 2 (no role) ==="
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"9527028875","password":"123456"}' 2>&1

echo ""
echo "=== Check user in DB ==="
sudo -u postgres psql -d yantrago -c "SELECT u.id, u.phone, u.role, u.organization_id FROM users u WHERE u.phone='9527028875';"
