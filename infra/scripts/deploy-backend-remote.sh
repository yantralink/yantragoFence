#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR=/opt/yantrago/backups
DEPLOY_DIR=/opt/yantrago/backend
VERSION=$(date +%Y%m%d%H%M%S)

mkdir -p $BACKUP_DIR

if [ -f $DEPLOY_DIR/app.jar ]; then
    cp $DEPLOY_DIR/app.jar $BACKUP_DIR/backend-$VERSION.jar
    echo "Backed up to $BACKUP_DIR/backend-$VERSION.jar"
fi

systemctl stop yantrago-backend || true

mv /tmp/yantrago-backend.jar $DEPLOY_DIR/app.jar
chown yantrago:yantrago $DEPLOY_DIR/app.jar

systemctl start yantrago-backend

echo "Service started, waiting for health..."
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "Backend is healthy!"
        systemctl status yantrago-backend --no-pager | head -5
        exit 0
    fi
    sleep 2
done

echo "ERROR: Backend did not become healthy within 60s"
journalctl -u yantrago-backend --no-pager -n 20
exit 1
