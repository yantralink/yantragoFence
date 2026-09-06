#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Single-Server Deploy Script
# Builds and deploys backend, gateway, and admin-web on the same server.
#
# Usage:
#   bash deploy/deploy-single-server.sh [service]
#
# Where service is: all | backend | gateway | admin-web
# Default: all

SERVICE="${1:-all}"
REPO_DIR="/opt/yantrago/repo"
BACKEND_DIR="/opt/yantrago/backend"
GATEWAY_DIR="/opt/yantrago/gateway"
ADMIN_WEB_DIR="/opt/yantrago/admin-web"

echo "=== YantraGO Single-Server Deploy: ${SERVICE} ==="

# ─── Backend ─────────────────────────────────────────────────────────
deploy_backend() {
    echo ">>> Building backend JAR..."
    cd "$REPO_DIR"
    ./gradlew :backend:bootJar -x test

    echo ">>> Deploying backend..."
    # Backup current version
    if [ -f "${BACKEND_DIR}/app.jar" ]; then
        cp "${BACKEND_DIR}/app.jar" "/opt/yantrago/backups/backend-$(date +%Y%m%d%H%M%S).jar"
    fi

    cp backend/build/libs/backend-1.0.0.jar "${BACKEND_DIR}/app.jar"
    chown yantrago:yantrago "${BACKEND_DIR}/app.jar"

    systemctl restart yantrago-backend

    echo ">>> Waiting for backend health..."
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
            echo "Backend is healthy!"
            systemctl status yantrago-backend --no-pager | head -5
            return 0
        fi
        sleep 2
    done
    echo "ERROR: Backend did not become healthy within 60s"
    journalctl -u yantrago-backend --no-pager -n 20
    return 1
}

# ─── Gateway ─────────────────────────────────────────────────────────
deploy_gateway() {
    echo ">>> Building gateway JAR..."
    cd "$REPO_DIR"
    ./gradlew :device-gateway:bootJar -x test

    echo ">>> Deploying gateway..."
    if [ -f "${GATEWAY_DIR}/app.jar" ]; then
        cp "${GATEWAY_DIR}/app.jar" "/opt/yantrago/backups/gateway-$(date +%Y%m%d%H%M%S).jar"
    fi

    cp device-gateway/build/libs/device-gateway-1.0.0.jar "${GATEWAY_DIR}/app.jar"
    chown yantrago:yantrago "${GATEWAY_DIR}/app.jar"

    systemctl restart yantrago-gateway

    echo ">>> Waiting for gateway health..."
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
            echo "Gateway is healthy!"
            systemctl status yantrago-gateway --no-pager | head -5
            return 0
        fi
        sleep 2
    done
    echo "ERROR: Gateway did not become healthy within 60s"
    journalctl -u yantrago-gateway --no-pager -n 20
    return 1
}

# ─── Admin Web ───────────────────────────────────────────────────────
deploy_admin_web() {
    echo ">>> Building admin web..."
    cd "$REPO_DIR/admin-web"
    npm install
    npm run build

    echo ">>> Deploying admin web..."
    # Copy build output
    rsync -a --delete .next/ "${ADMIN_WEB_DIR}/.next/"
    cp -r package.json package-lock.json "${ADMIN_WEB_DIR}/"
    cp -r public/ "${ADMIN_WEB_DIR}/public/" 2>/dev/null || true
    chown -R yantrago:yantrago "${ADMIN_WEB_DIR}"

    # Restart admin web via systemd
    systemctl restart yantrago-admin-web 2>/dev/null || true

    echo ">>> Admin web deployed on port 3001"
}

# ─── Run ─────────────────────────────────────────────────────────────
case "$SERVICE" in
    backend)
        deploy_backend
        ;;
    gateway)
        deploy_gateway
        ;;
    admin-web)
        deploy_admin_web
        ;;
    all)
        deploy_backend
        deploy_gateway
        deploy_admin_web
        ;;
    *)
        echo "Unknown service: $SERVICE"
        echo "Usage: bash deploy-single-server.sh [all|backend|gateway|admin-web]"
        exit 1
        ;;
esac

echo ""
echo "=== Deploy Complete: ${SERVICE} ==="
