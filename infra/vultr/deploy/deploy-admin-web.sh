#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Deploy Admin Web (Next.js)
# Usage: ./deploy/deploy-admin-web.sh [build-dir] [version]
#
# Builds the Next.js app locally and uploads the standalone output.

BUILD_DIR="${1:-admin-web}"
VERSION="${2:-$(date +%Y%m%d%H%M%S)}"
REMOTE_HOST="${YANTRAGO_APP_HOST:?Set YANTRAGO_APP_HOST env var}"
REMOTE_USER="${YANTRAGO_DEPLOY_USER:-yantrago}"
DEPLOY_DIR="/opt/yantrago/admin-web"
BACKUP_DIR="/opt/yantrago/backups"

echo "=== YantraGO Admin Web Deploy ==="
echo "Build:   $BUILD_DIR"
echo "Version: $VERSION"
echo "Host:    $REMOTE_HOST"
echo ""

# Build locally
echo ">>> Building Next.js app..."
cd "$BUILD_DIR"
npm ci
npm run build

# Create deployment archive
echo ">>> Creating archive..."
tar -czf /tmp/yantrago-admin-web.tar.gz .next standalone public package.json next.config.js

# Upload
echo ">>> Uploading..."
scp /tmp/yantrago-admin-web.tar.gz "${REMOTE_USER}@${REMOTE_HOST}:/tmp/"

# Deploy remotely
ssh "${REMOTE_USER}@${REMOTE_HOST}" <<EOF
set -euo pipefail

# Backup current version
if [ -d ${DEPLOY_DIR} ]; then
    mkdir -p ${BACKUP_DIR}
    tar -czf ${BACKUP_DIR}/admin-web-${VERSION}.tar.gz -C ${DEPLOY_DIR} .
    echo "Backed up to ${BACKUP_DIR}/admin-web-${VERSION}.tar.gz"
fi

# Stop PM2/systemd (if running)
sudo systemctl stop yantrago-admin-web 2>/dev/null || true

# Deploy
mkdir -p ${DEPLOY_DIR}
rm -rf ${DEPLOY_DIR}/*
tar -xzf /tmp/yantrago-admin-web.tar.gz -C ${DEPLOY_DIR}
rm /tmp/yantrago-admin-web.tar.gz

# Install production dependencies
cd ${DEPLOY_DIR}
npm ci --omit=dev

# Start
sudo systemctl start yantrago-admin-web 2>/dev/null || \
    echo "NOTE: Install systemd unit for admin-web or start with: PORT=3001 npm start"

# Health check
echo "Waiting for health check..."
for i in \$(seq 1 15); do
    if curl -sf http://localhost:3001 > /dev/null 2>&1; then
        echo "Admin web is running!"
        exit 0
    fi
    sleep 2
done

echo "WARNING: Admin web did not respond within 30s"
exit 0
EOF

echo ""
echo "=== Admin Web Deploy Complete ==="
