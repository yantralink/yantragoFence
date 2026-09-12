#!/usr/bin/env bash
set -euo pipefail

BUILD_DIR="/tmp/yantrago-admin-web-build"
DEPLOY_DIR="/opt/yantrago/admin-web"
BACKUP_DIR="/opt/yantrago/backups"
VERSION=$(date +%Y%m%d%H%M%S)

echo "=== Building admin-web on server (clean rebuild) ==="

# Clean everything
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"
tar -xzf /tmp/yantrago-admin-web-src.tar.gz
cd admin-web

# Verify source has the changes
echo ">>> Verifying source..."
grep -c "isSuperAdmin\|selectedOrgId" src/app/\(admin\)/customers/page.tsx

# Install dependencies
echo ">>> Installing dependencies..."
npm install --legacy-peer-deps 2>&1 | tail -5

# Clean build cache and build
echo ">>> Cleaning build cache..."
rm -rf .next

echo ">>> Building Next.js app..."
NEXT_PUBLIC_API_URL=https://yantrago.com/api/v1 npm run build 2>&1 | tail -20

# Verify build output has the changes
echo ">>> Verifying build output..."
grep -c "organizationId" .next/server/app/\(admin\)/customers/page.js 2>/dev/null || echo "0 matches in server page"
grep -rl "Select organization" .next/static/chunks/app/ 2>/dev/null | head -5

# Backup current deployment
echo ">>> Backing up current deployment..."
mkdir -p "$BACKUP_DIR"
tar -czf "$BACKUP_DIR/admin-web-$VERSION.tar.gz" -C "$DEPLOY_DIR" .
echo "Backed up to $BACKUP_DIR/admin-web-$VERSION.tar.gz"

# Stop service
echo ">>> Stopping service..."
systemctl stop yantrago-admin-web || true

# Deploy
echo ">>> Deploying..."
rm -rf "$DEPLOY_DIR"/*
cp -r "$BUILD_DIR/admin-web/.next" "$DEPLOY_DIR/.next"
cp -r "$BUILD_DIR/admin-web/public" "$DEPLOY_DIR/public" 2>/dev/null || mkdir -p "$DEPLOY_DIR/public"
cp "$BUILD_DIR/admin-web/package.json" "$DEPLOY_DIR/package.json"
cp "$BUILD_DIR/admin-web/next.config.js" "$DEPLOY_DIR/next.config.js"

# Install production dependencies
cd "$DEPLOY_DIR"
npm install --omit=dev --legacy-peer-deps 2>&1 | tail -5

# Fix permissions
chown -R yantrago:yantrago "$DEPLOY_DIR"

# Start service
echo ">>> Starting service..."
systemctl start yantrago-admin-web

# Health check
echo ">>> Waiting for health check..."
for i in $(seq 1 15); do
    if curl -sf http://localhost:3001 > /dev/null 2>&1; then
        echo "Admin web is running!"
        systemctl status yantrago-admin-web --no-pager | head -5
        exit 0
    fi
    sleep 2
done

echo "WARNING: Admin web did not respond within 30s"
journalctl -u yantrago-admin-web --no-pager -n 20
exit 1
