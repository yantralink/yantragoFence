#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Redis Server Setup
# Run as root on a fresh Ubuntu 22.04/24.04 Vultr instance.
#
# Usage: sudo bash server-setup/redis-server-setup.sh

echo "=== YantraGO Redis Server Setup ==="

# Update system
apt-get update && apt-get upgrade -y

# Install Redis
echo ">>> Installing Redis..."
apt-get install -y redis-server

# Generate password if not provided
REDIS_PASS="${YANTRAGO_REDIS_PASSWORD:-$(openssl rand -base64 24)}"

# Configure Redis
REDIS_CONF="/etc/redis/redis.conf"
cp "$REDIS_CONF" "${REDIS_CONF}.bak"

# Set password
sed -i "s/^# requirepass .*/requirepass ${REDIS_PASS}/" "$REDIS_CONF"

# Bind to all interfaces (firewall will restrict access)
sed -i "s/^bind 127.0.0.1 -::1/bind 0.0.0.0/" "$REDIS_CONF"

# Disable dangerous commands
echo "" >> "$REDIS_CONF"
echo "# YantraGO security settings" >> "$REDIS_CONF"
echo "rename-command FLUSHDB \"\"" >> "$REDIS_CONF"
echo "rename-command FLUSHALL \"\"" >> "$REDIS_CONF"
echo "rename-command CONFIG \"\"" >> "$REDIS_CONF"
echo "rename-command DEBUG \"\"" >> "$REDIS_CONF"

# Production settings
cat >> "$REDIS_CONF" <<CONF

# YantraGO production settings
maxmemory 512mb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec
CONF

# Restart Redis
systemctl enable redis-server
systemctl restart redis-server

echo ""
echo "=== Redis Server Setup Complete ==="
echo "Password: ${REDIS_PASS}"
echo "Port: 6379"
echo ""
echo "IMPORTANT: Save the password securely. Configure firewall to allow"
echo "port 6379 only from app and gateway servers."
