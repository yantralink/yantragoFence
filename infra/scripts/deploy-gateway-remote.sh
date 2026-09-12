#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="/opt/yantrago/gateway"
BACKUP_DIR="/opt/yantrago/backups"
VERSION=$(date +%Y%m%d%H%M%S)

echo "=== YantraGO Device Gateway Deploy ==="

# Backup
if [ -f "${DEPLOY_DIR}/app.jar" ]; then
    mkdir -p "${BACKUP_DIR}"
    cp "${DEPLOY_DIR}/app.jar" "${BACKUP_DIR}/gateway-${VERSION}.jar"
    echo "Backed up to ${BACKUP_DIR}/gateway-${VERSION}.jar"
fi

# Stop service
echo ">>> Stopping gateway..."
systemctl stop yantrago-gateway

# Deploy
echo ">>> Deploying..."
cp /tmp/yantrago-gateway.jar "${DEPLOY_DIR}/app.jar"
chown yantrago:yantrago "${DEPLOY_DIR}/app.jar"

# Start service
echo ">>> Starting gateway..."
systemctl start yantrago-gateway

# Wait for health
echo ">>> Waiting for gateway to start..."
for i in $(seq 1 10); do
    if systemctl is-active --quiet yantrago-gateway; then
        echo "Gateway is running!"
        systemctl status yantrago-gateway --no-pager | head -5
        # Check if TCP port is listening
        if ss -tlnp | grep -q ':5000'; then
            echo "TCP port 5000 is listening"
        else
            echo "WARNING: TCP port 5000 is not listening yet"
        fi
        exit 0
    fi
    sleep 2
done

echo "WARNING: Gateway did not start within 20s"
journalctl -u yantrago-gateway --no-pager -n 20
exit 1
