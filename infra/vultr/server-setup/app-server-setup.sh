#!/usr/bin/env bash
set -euo pipefail

# YantraGO — App Server Setup (Java 17 + Nginx)
# Run as root on a fresh Ubuntu 22.04/24.04 Vultr instance.
#
# Usage: sudo bash server-setup/app-server-setup.sh

echo "=== YantraGO App Server Setup ==="

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

# Install Nginx
echo ">>> Installing Nginx..."
apt-get install -y nginx
systemctl enable nginx
systemctl start nginx

# Create yantrago user
echo ">>> Creating yantrago user..."
if ! id -u yantrago &>/dev/null; then
    useradd -r -m -d /opt/yantrago -s /bin/bash yantrago
fi

# Create directories
echo ">>> Creating directories..."
mkdir -p /opt/yantrago/backend
mkdir -p /opt/yantrago/logs
mkdir -p /opt/yantrago/config
mkdir -p /opt/yantrago/backups
chown -R yantrago:yantrago /opt/yantrago

# Install Certbot for SSL
echo ">>> Installing Certbot..."
apt-get install -y certbot python3-certbot-nginx

# Copy Nginx configs (if available in repo)
if [ -f /opt/yantrago/repo/infra/vultr/nginx/nginx.conf ]; then
    cp /opt/yantrago/repo/infra/vultr/nginx/nginx.conf /etc/nginx/sites-available/yantrago
    ln -sf /etc/nginx/sites-available/yantrago /etc/nginx/sites-enabled/yantrago
    rm -f /etc/nginx/sites-enabled/default
    nginx -t && systemctl reload nginx
fi

echo ""
echo "=== App Server Setup Complete ==="
echo "Java: $(java -version 2>&1 | head -1)"
echo "Nginx: $(nginx -v 2>&1)"
echo "User: yantrago"
echo "Home: /opt/yantrago"
echo ""
echo "Next steps:"
echo "1. Copy backend JAR to /opt/yantrago/backend/"
echo "2. Copy application-prod.yml to /opt/yantrago/config/"
echo "3. Install systemd unit: cp infra/vultr/systemd/yantrago-backend.service /etc/systemd/system/"
echo "4. Start: systemctl enable yantrago-backend && systemctl start yantrago-backend"
