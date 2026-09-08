#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"9527028875","password":"yantrago"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "=== Sending ON command ==="
RESULT=$(curl -s -X POST http://localhost:8080/api/v1/commands -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"machineId":"69f8dd7c-54f7-4d1f-9913-5ee34fb0f25e","commandType":"ON"}')
echo "$RESULT"
CMD_ID=$(echo "$RESULT" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
echo "Command ID: $CMD_ID"
echo "=== Waiting 5s for device ACK ==="
sleep 5
echo "=== Gateway logs (last 20 lines) ==="
tail -20 /opt/yantrago/gateway/logs/gateway.log
echo "=== Command status in DB ==="
sudo -u postgres psql -d yantrago -t -c "SELECT status, attempt_count, last_error FROM machine_commands WHERE id='$CMD_ID';"
