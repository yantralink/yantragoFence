#!/usr/bin/env bash
set -euo pipefail

# YantraGO — RabbitMQ Server Setup
# Run as root on a fresh Ubuntu 22.04/24.04 Vultr instance.
#
# Usage: sudo bash server-setup/rabbitmq-server-setup.sh

echo "=== YantraGO RabbitMQ Server Setup ==="

# Update system
apt-get update && apt-get upgrade -y

# Install Erlang and RabbitMQ
echo ">>> Installing RabbitMQ..."
apt-get install -y curl gnupg apt-transport-https

# Add RabbitMQ repository
cat <<EOF > /etc/apt/sources.list.d/rabbitmq.list
deb https://ppa1.rabbitmq.com/rabbitmq/rabbitmq-erlang/deb/ubuntu $(lsb_release -cs) main
deb https://ppa1.rabbitmq.com/rabbitmq/rabbitmq-server/deb/ubuntu $(lsb_release -cs) main
EOF

curl -fsSL https://ppa1.rabbitmq.com/rabbitmq/rabbitmq-signing-key-public.asc | apt-key add -
apt-get update
apt-get install -y rabbitmq-server

# Enable management plugin
echo ">>> Enabling management plugin..."
rabbitmq-plugins enable rabbitmq_management

# Create YantraGO user
RABBIT_USER="${YANTRAGO_RABBITMQ_USER:-yantrago}"
RABBIT_PASS="${YANTRAGO_RABBITMQ_PASSWORD:-$(openssl rand -base64 24)}"

echo ">>> Creating RabbitMQ user..."
rabbitmqctl add_user "$RABBIT_USER" "$RABBIT_PASS"
rabbitmqctl set_user_tags "$RABBIT_USER" administrator
rabbitmqctl set_permissions -p / "$RABBIT_USER" ".*" ".*" ".*"

# Delete default guest user for security
rabbitmqctl delete_user guest 2>/dev/null || true

# Configure RabbitMQ
cat > /etc/rabbitmq/rabbitmq.conf <<CONF
# YantraGO RabbitMQ configuration
listeners.tcp.default = 5672
management.tcp.port = 15672
loopback_users.guest = false
default_user_tags.administrator = true
heartbeat = 60
connection_max = 1000
CONF

# Restart RabbitMQ
systemctl enable rabbitmq-server
systemctl restart rabbitmq-server

echo ""
echo "=== RabbitMQ Server Setup Complete ==="
echo "User: ${RABBIT_USER}"
echo "Password: ${RABBIT_PASS}"
echo "AMQP Port: 5672"
echo "Management UI: http://<server-ip>:15672"
echo ""
echo "IMPORTANT: Save the credentials securely. Configure firewall to allow"
echo "ports 5672 and 15672 only from app and gateway servers."
