#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Deploy Gateway
# Usage: ./deploy/deploy-gateway.sh [jar-path] [version]

JAR_PATH="${1:-device-gateway/build/libs/device-gateway-1.0.0-SNAPSHOT.jar}"
VERSION="${2:-$(date +%Y%m%d%H%M%S)}"
REMOTE_HOST="${YANTRAGO_GATEWAY_HOST:?Set YANTRAGO_GATEWAY_HOST env var}"
REMOTE_USER="${YANTRAGO_DEPLOY_USER:-yantrago}"
DEPLOY_DIR="/opt/yantrago/gateway"
BACKUP_DIR="/opt/yantrago/backups"

echo "=== YantraGO Gateway Deploy ==="
echo "JAR:     $JAR_PATH"
echo "Version: $VERSION"
echo "Host:    $REMOTE_HOST"
echo ""

if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: JAR not found at $JAR_PATH"
    echo "Build it first: ./gradlew :device-gateway:bootJar -x test"
    exit 1
fi

# Upload JAR
echo ">>> Uploading JAR..."
scp "$JAR_PATH" "${REMOTE_USER}@${REMOTE_HOST}:/tmp/yantrago-gateway.jar"

# Deploy remotely
echo ">>> Deploying..."
ssh "${REMOTE_USER}@${REMOTE_HOST}" <<EOF
set -euo pipefail

# Backup current version
if [ -f ${DEPLOY_DIR}/app.jar ]; then
    mkdir -p ${BACKUP_DIR}
    cp ${DEPLOY_DIR}/app.jar ${BACKUP_DIR}/gateway-${VERSION}.jar
    echo "Backed up to ${BACKUP_DIR}/gateway-${VERSION}.jar"
fi

# Stop service
sudo systemctl stop yantrago-gateway || true

# Deploy new JAR
mv /tmp/yantrago-gateway.jar ${DEPLOY_DIR}/app.jar
chown yantrago:yantrago ${DEPLOY_DIR}/app.jar

# Start service
sudo systemctl start yantrago-gateway

# Wait for health
echo "Waiting for health check..."
for i in \$(seq 1 30); do
    if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
        echo "Gateway is healthy!"
        systemctl status yantrago-gateway --no-pager | head -5
        exit 0
    fi
    sleep 2
done

echo "ERROR: Gateway did not become healthy within 60s"
sudo journalctl -u yantrago-gateway --no-pager -n 20
exit 1
EOF

echo ""
echo "=== Gateway Deploy Complete ==="
