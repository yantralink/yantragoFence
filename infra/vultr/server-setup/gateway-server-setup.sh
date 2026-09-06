#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Gateway Server Setup (Java 17)
# Run as root on a fresh Ubuntu 22.04/24.04 Vultr instance.
#
# Usage: sudo bash server-setup/gateway-server-setup.sh

echo "=== YantraGO Gateway Server Setup ==="

# Update system
apt-get update && apt-get upgrade -y

# Install Java 17 (Eclipse Temurin)
echo ">>> Installing Java 17..."
apt-get install -y wget apt-transport-https gnupg
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" > /etc/apt/sources.list.d/adoptium.list
apt-get update
apt-get install -y temurin-17-jdk

# Verify Java
java -version

# Create yantrago user
echo ">>> Creating yantrago user..."
if ! id -u yantrago &>/dev/null; then
    useradd -r -m -d /opt/yantrago -s /bin/bash yantrago
fi

# Create directories
echo ">>> Creating directories..."
mkdir -p /opt/yantrago/gateway
mkdir -p /opt/yantrago/logs
mkdir -p /opt/yantrago/config
chown -R yantrago:yantrago /opt/yantrago

# Optimize kernel for TCP connections
echo ">>> Optimizing kernel for TCP..."
cat >> /etc/sysctl.conf <<SYSCTL

# YantraGO gateway TCP optimizations
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_tw_reuse = 1
net.ipv4.ip_local_port_range = 10000 65535
fs.file-max = 1000000
SYSCTL

sysctl -p

# Increase file descriptor limit for yantrago user
cat >> /etc/security/limits.conf <<LIMITS
yantrago  soft  nofile  1000000
yantrago  hard  nofile  1000000
LIMITS

echo ""
echo "=== Gateway Server Setup Complete ==="
echo "Java: $(java -version 2>&1 | head -1)"
echo "User: yantrago"
echo "Home: /opt/yantrago"
echo "TCP ports: 5000 (Concox), 5001 (JT808), 5002 (Fencing)"
echo ""
echo "Next steps:"
echo "1. Copy gateway JAR to /opt/yantrago/gateway/"
echo "2. Copy application-prod.yml to /opt/yantrago/config/"
echo "3. Install systemd unit: cp infra/vultr/systemd/yantrago-gateway.service /etc/systemd/system/"
echo "4. Start: systemctl enable yantrago-gateway && systemctl start yantrago-gateway"
