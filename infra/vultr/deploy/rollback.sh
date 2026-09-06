#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Rollback
# Usage: ./deploy/rollback.sh [backend|gateway|admin-web] [version]
#
# If no version specified, rolls back to the most recent backup.

SERVICE="${1:?Usage: rollback.sh [backend|gateway|admin-web] [version]}"
VERSION="${2:-}"
REMOTE_HOST="${YANTRAGO_APP_HOST:-${YANTRAGO_GATEWAY_HOST}}"
REMOTE_USER="${YANTRAGO_DEPLOY_USER:-yantrago}"
BACKUP_DIR="/opt/yantrago/backups"

echo "=== YantraGO Rollback: $SERVICE ==="

case "$SERVICE" in
    backend)
        DEPLOY_DIR="/opt/yantrago/backend"
        SYSTEMD_UNIT="yantrago-backend"
        BACKUP_PREFIX="backend"
        ;;
    gateway)
        DEPLOY_DIR="/opt/yantrago/gateway"
        SYSTEMD_UNIT="yantrago-gateway"
        BACKUP_PREFIX="gateway"
        REMOTE_HOST="${YANTRAGO_GATEWAY_HOST:?Set YANTRAGO_GATEWAY_HOST}"
        ;;
    admin-web)
        DEPLOY_DIR="/opt/yantrago/admin-web"
        SYSTEMD_UNIT="yantrago-admin-web"
        BACKUP_PREFIX="admin-web"
        ;;
    *)
        echo "ERROR: Unknown service '$SERVICE'. Use: backend, gateway, or admin-web"
        exit 1
        ;;
esac

echo "Service: $SERVICE"
echo "Host:    $REMOTE_HOST"
echo ""

# Rollback remotely
ssh "${REMOTE_USER}@${REMOTE_HOST}" <<EOF
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR}"
DEPLOY_DIR="${DEPLOY_DIR}"

# Find backup version
if [ -z "${VERSION}" ]; then
    VERSION=\$(ls -t \${BACKUP_DIR}/${BACKUP_PREFIX}-*.jar 2>/dev/null | head -1 | sed "s/.*${BACKUP_PREFIX}-//;s/\.jar//")
    if [ -z "\${VERSION}" ]; then
        echo "ERROR: No backups found in \${BACKUP_DIR}"
        exit 1
    fi
    echo "Rolling back to latest backup: \${VERSION}"
fi

BACKUP_FILE="${BACKUP_PREFIX}-\${VERSION}"

if [ ! -f "\${BACKUP_DIR}/\${BACKUP_FILE}.jar" ] && [ ! -f "\${BACKUP_DIR}/\${BACKUP_FILE}.tar.gz" ]; then
    echo "ERROR: Backup not found: \${BACKUP_FILE}"
    echo "Available backups:"
    ls -lt \${BACKUP_DIR}/${BACKUP_PREFIX}-* 2>/dev/null || echo "  (none)"
    exit 1
fi

# Stop service
sudo systemctl stop ${SYSTEMD_UNIT} || true

# Restore
if [ -f "\${BACKUP_DIR}/\${BACKUP_FILE}.jar" ]; then
    cp "\${BACKUP_DIR}/\${BACKUP_FILE}.jar" "${DEPLOY_DIR}/app.jar"
    chown yantrago:yantrago "${DEPLOY_DIR}/app.jar"
elif [ -f "\${BACKUP_DIR}/\${BACKUP_FILE}.tar.gz" ]; then
    rm -rf "${DEPLOY_DIR:?}/"*
    tar -xzf "\${BACKUP_DIR}/\${BACKUP_FILE}.tar.gz" -C "${DEPLOY_DIR}"
fi

# Start service
sudo systemctl start ${SYSTEMD_UNIT}

# Health check
echo "Waiting for health check..."
for i in \$(seq 1 30); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1 || \
       curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1 || \
       curl -sf http://localhost:3001 > /dev/null 2>&1; then
        echo "${SERVICE} is healthy after rollback!"
        systemctl status ${SYSTEMD_UNIT} --no-pager | head -5
        exit 0
    fi
    sleep 2
done

echo "ERROR: ${SERVICE} did not become healthy after rollback"
sudo journalctl -u ${SYSTEMD_UNIT} --no-pager -n 20
exit 1
EOF

echo ""
echo "=== Rollback Complete ==="
