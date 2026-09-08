#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"9527028875","password":"yantrago"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "=== Sending ON command ==="
RESULT=$(curl -s -X POST http://localhost:8080/api/v1/commands -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"machineId":"69f8dd7c-54f7-4d1f-9913-5ee34fb0f25e","commandType":"ON"}')
echo "$RESULT"
echo "=== Waiting 8s for ACK ==="
sleep 8
echo "=== Gateway logs ==="
tail -30 /opt/yantrago/gateway/logs/gateway.log
