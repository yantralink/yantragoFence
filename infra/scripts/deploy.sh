#!/usr/bin/env bash
set -euo pipefail

# YantraGO Deployment Script
# Usage: ./infra/scripts/deploy.sh [staging|prod]

ENVIRONMENT="${1:-staging}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/infra/docker/docker-compose.${ENVIRONMENT}.yml"

echo "=== YantraGO Deploy: $ENVIRONMENT ==="

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "ERROR: Compose file not found: $COMPOSE_FILE"
    echo "Available environments: dev, staging"
    exit 1
fi

# Build application JARs
echo ">>> Building application JARs..."
cd "$PROJECT_ROOT"
./gradlew :backend:bootJar :device-gateway:bootJar -x test --no-daemon

# Build Docker images
echo ">>> Building Docker images..."
docker compose -f "$COMPOSE_FILE" build

# Start services
echo ">>> Starting services..."
docker compose -f "$COMPOSE_FILE" up -d

# Wait for health checks
echo ">>> Waiting for services to become healthy..."
sleep 10
docker compose -f "$COMPOSE_FILE" ps

echo ""
echo "=== Deployment complete ==="
echo "Backend API:   http://localhost:8080"
echo "Gateway:       tcp://localhost:5000 (Concox), :5001 (JT808), :5002 (Fencing)"
echo "Prometheus:    http://localhost:9090"
echo "Grafana:       http://localhost:3002 (admin/admin)"
echo "RabbitMQ UI:   http://localhost:15672 (guest/guest)"
