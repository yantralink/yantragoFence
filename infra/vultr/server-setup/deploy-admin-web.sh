#!/usr/bin/env bash
set -euo pipefail

echo "=== Deploying Admin Web (Next.js) ==="

cd /opt/yantrago/repo/admin-web

echo ">>> Installing dependencies..."
npm install 2>&1 | tail -5

echo ""
echo ">>> Building Next.js..."
npm run build 2>&1 | tail -15

echo ""
echo ">>> Deploying to /opt/yantrago/admin-web..."
mkdir -p /opt/yantrago/admin-web
rsync -a --delete .next/ /opt/yantrago/admin-web/.next/
cp package.json package-lock.json /opt/yantrago/admin-web/
cp -r public/ /opt/yantrago/admin-web/public/ 2>/dev/null || true
cp next.config.js /opt/yantrago/admin-web/ 2>/dev/null || true
chown -R yantrago:yantrago /opt/yantrago/admin-web

echo ""
echo ">>> Installing admin-web systemd unit..."
cp /opt/yantrago/repo/infra/vultr/systemd/yantrago-admin-web.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable yantrago-admin-web
systemctl restart yantrago-admin-web

sleep 5
echo ""
echo "Admin web status: $(systemctl is-active yantrago-admin-web)"
echo -n "Health check: "
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001/ 2>&1
echo ""
echo ""
echo "=== Admin Web Deploy Complete ==="
