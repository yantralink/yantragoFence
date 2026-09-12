#!/bin/bash
set -e
echo ">>> Stopping services..."
systemctl stop yantrago-backend
systemctl stop yantrago-gateway
echo ">>> Replacing JARs..."
mv /tmp/yantrago-backend.jar /opt/yantrago/backend/app.jar
mv /tmp/yantrago-gateway.jar /opt/yantrago/gateway/app.jar
chown yantrago:yantrago /opt/yantrago/backend/app.jar /opt/yantrago/gateway/app.jar
echo ">>> Starting gateway..."
systemctl start yantrago-gateway
sleep 3
echo ">>> Starting backend..."
systemctl start yantrago-backend
echo ">>> Waiting for backend health..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "Backend is healthy!"
    break
  fi
  sleep 2
done
echo ">>> Backend status:"
systemctl status yantrago-backend --no-pager | head -5
echo ">>> Gateway status:"
systemctl status yantrago-gateway --no-pager | head -5
echo ">>> Done"
