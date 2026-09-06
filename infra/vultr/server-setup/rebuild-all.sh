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
echo "Waiting for backend..."
sleep 20
echo "Backend: $(systemctl is-active yantrago-backend) - $(curl -s http://localhost:8080/actuator/health)"

echo ""
echo "=== Rebuilding admin-web ==="
cd /opt/yantrago/repo/admin-web
npm run build 2>&1 | tail -10

echo ""
echo "=== Deploying admin-web ==="
rsync -a --delete .next/ /opt/yantrago/admin-web/.next/
cp package.json package-lock.json /opt/yantrago/admin-web/
chown -R yantrago:yantrago /opt/yantrago/admin-web
cd /opt/yantrago/admin-web
npm install --production 2>&1 | tail -3
systemctl restart yantrago-admin-web
sleep 5
echo "Admin web: $(systemctl is-active yantrago-admin-web)"

echo ""
echo "=== Testing /auth/me ==="
LOGIN=$(curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yantrago.com","password":"password"}')
TOKEN=$(echo "$LOGIN" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
curl -sk https://yantrago.com/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" 2>&1
