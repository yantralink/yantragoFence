#!/usr/bin/env bash
set -euo pipefail

echo "=== Pulling latest code ==="
cd /opt/yantrago/repo
git pull origin main 2>&1

echo ""
echo "=== Rebuilding backend ==="
export JAVA_OPTS='-Xmx512m'
./gradlew :backend:bootJar -x test --no-daemon 2>&1 | tail -5

echo ""
echo "=== Deploying backend ==="
cp backend/build/libs/backend-1.0.0.jar /opt/yantrago/backend/app.jar
chown yantrago:yantrago /opt/yantrago/backend/app.jar
systemctl restart yantrago-backend

echo "Waiting for backend to start..."
sleep 25
echo "Backend status: $(systemctl is-active yantrago-backend)"
curl -s http://localhost:8080/actuator/health 2>&1
echo ""

echo ""
echo "=== Testing login ==="
curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yantrago.com","password":"password"}'
