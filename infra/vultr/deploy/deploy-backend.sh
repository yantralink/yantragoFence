#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Deploy Backend
# Usage: ./deploy/deploy-backend.sh [jar-path] [version]
#
# Copies the backend JAR to the server, backs up the current version,
# and restarts the systemd service.

JAR_PATH="${1:-backend/build/libs/backend-1.0.0-SNAPSHOT.jar}"
VERSION="${2:-$(date +%Y%m%d%H%M%S)}"
REMOTE_HOST="${YANTRAGO_APP_HOST:?Set YANTRAGO_APP_HOST env var}"
REMOTE_USER="${YANTRAGO_DEPLOY_USER:-yantrago}"
DEPLOY_DIR="/opt/yantrago/backend"
BACKUP_DIR="/opt/yantrago/backups"

echo "=== YantraGO Backend Deploy ==="
echo "JAR:     $JAR_PATH"
echo "Version: $VERSION"
echo "Host:    $REMOTE_HOST"
echo ""

if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: JAR not found at $JAR_PATH"
    echo "Build it first: ./gradlew :backend:bootJar -x test"
    exit 1
fi

# Upload JAR
echo ">>> Uploading JAR..."
scp "$JAR_PATH" "${REMOTE_USER}@${REMOTE_HOST}:/tmp/yantrago-backend.jar"

# Deploy remotely
echo ">>> Deploying..."
ssh "${REMOTE_USER}@${REMOTE_HOST}" <<EOF
set -euo pipefail

# Backup current version
if [ -f ${DEPLOY_DIR}/app.jar ]; then
    mkdir -p ${BACKUP_DIR}
    cp ${DEPLOY_DIR}/app.jar ${BACKUP_DIR}/backend-${VERSION}.jar
    echo "Backed up to ${BACKUP_DIR}/backend-${VERSION}.jar"
fi

# Stop service
sudo systemctl stop yantrago-backend || true

# Deploy new JAR
mv /tmp/yantrago-backend.jar ${DEPLOY_DIR}/app.jar
chown yantrago:yantrago ${DEPLOY_DIR}/app.jar

# Start service
sudo systemctl start yantrago-backend

# Wait for health
echo "Waiting for health check..."
for i in \$(seq 1 30); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "Backend is healthy!"
        systemctl status yantrago-backend --no-pager | head -5
        exit 0
    fi
    sleep 2
done

echo "ERROR: Backend did not become healthy within 60s"
sudo journalctl -u yantrago-backend --no-pager -n 20
exit 1
EOF

echo ""
echo "=== Backend Deploy Complete ==="
