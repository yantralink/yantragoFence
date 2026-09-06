#!/usr/bin/env bash
set -euo pipefail

# YantraGO Rollback Script
# Usage: ./infra/scripts/rollback.sh [staging|prod] [--previous]

ENVIRONMENT="${1:-staging}"
ROLLBACK_PREVIOUS="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/infra/docker/docker-compose.${ENVIRONMENT}.yml"

echo "=== YantraGO Rollback: $ENVIRONMENT ==="

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "ERROR: Compose file not found: $COMPOSE_FILE"
    exit 1
fi

if [ "$ROLLBACK_PREVIOUS" = "--previous" ]; then
    echo ">>> Rolling back to previous images..."
    # List previous images
    docker images --format "{{.Repository}}:{{.Tag}} {{.CreatedAt}}" | grep yantrago | head -10
    echo ""
    echo "Specify the image tag to roll back to and re-run:"
    echo "  docker compose -f $COMPOSE_FILE down"
    echo "  docker tag yantrago-backend:<tag> yantrago-backend:latest"
    echo "  docker compose -f $COMPOSE_FILE up -d"
    exit 0
fi

# Stop current services
echo ">>> Stopping current services..."
docker compose -f "$COMPOSE_FILE" down

# Restart with last built images
echo ">>> Restarting services..."
docker compose -f "$COMPOSE_FILE" up -d

echo ""
echo "=== Rollback complete ==="
docker compose -f "$COMPOSE_FILE" ps
