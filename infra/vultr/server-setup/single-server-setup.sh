#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Single-Server Setup (All-in-One)
# Installs: PostgreSQL 15 + PostGIS, Redis 7, RabbitMQ 3, Java 17, Nginx, Node.js 20, Certbot
#
# Run as root on a fresh Ubuntu 22.04 Vultr instance (1 vCPU / 1GB RAM).
#
# Usage:
#   sudo YANTRAGO_DOMAIN=yantrago.com bash single-server-setup.sh
#
# Environment variables:
#   YANTRAGO_DOMAIN    — your domain name (default: yantrago.com)
#   YANTRAGO_DB_PASSWORD    — PostgreSQL password (default: auto-generated)
#   YANTRAGO_REDIS_PASSWORD — Redis password (default: auto-generated)
#   YANTRAGO_RABBITMQ_PASSWORD — RabbitMQ password (default: auto-generated)
#   JWT_ACCESS_SECRET   — JWT access token secret (default: auto-generated)
#   JWT_REFRESH_SECRET  — JWT refresh token secret (default: auto-generated)

DOMAIN="${YANTRAGO_DOMAIN:-yantrago.com}"
DB_NAME="yantrago"
DB_USER="yantrago"
DB_PASS="${YANTRAGO_DB_PASSWORD:-$(openssl rand -base64 24)}"
REDIS_PASS="${YANTRAGO_REDIS_PASSWORD:-$(openssl rand -base64 24)}"
RABBIT_USER="yantrago"
RABBIT_PASS="${YANTRAGO_RABBITMQ_PASSWORD:-$(openssl rand -base64 24)}"
JWT_ACCESS="${JWT_ACCESS_SECRET:-$(openssl rand -base64 48)}"
JWT_REFRESH="${JWT_REFRESH_SECRET:-$(openssl rand -base64 48)}"

echo "=== YantraGO Single-Server Setup ==="
echo "Domain: $DOMAIN"
echo ""

# ─── 1. System update ────────────────────────────────────────────────
echo ">>> Updating system..."
apt-get update && apt-get upgrade -y
apt-get install -y wget curl gnupg apt-transport-https lsb-release ca-certificates software-properties-common ufw

# ─── 2. Create swap (essential for 1GB RAM) ──────────────────────────
echo ">>> Creating 2GB swap file..."
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    echo 'vm.swappiness=10' >> /etc/sysctl.conf
    sysctl -p
fi

# ─── 3. PostgreSQL 15 + PostGIS ──────────────────────────────────────
echo ">>> Installing PostgreSQL 15 + PostGIS..."
sh -c 'echo "deb https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -
apt-get update
apt-get install -y postgresql-15 postgresql-15-postgis-3 postgresql-contrib

systemctl enable postgresql
systemctl start postgresql

echo ">>> Creating database and user..."
sudo -u postgres psql <<SQL
CREATE DATABASE ${DB_NAME};
CREATE USER ${DB_USER} WITH ENCRYPTED PASSWORD '${DB_PASS}';
GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};
SQL

sudo -u postgres psql -d "${DB_NAME}" -c "CREATE EXTENSION IF NOT EXISTS postgis;"
sudo -u postgres psql -d "${DB_NAME}" -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"

# Optimize PostgreSQL for 1GB RAM
PG_CONF="/etc/postgresql/15/main/postgresql.conf"
cat >> "$PG_CONF" <<CONF

# YantraGO production settings (1GB RAM)
max_connections = 100
shared_buffers = 128MB
effective_cache_size = 512MB
work_mem = 4MB
maintenance_work_mem = 64MB
random_page_cost = 1.1
effective_io_concurrency = 200
max_wal_size = 1GB
min_wal_size = 128MB
CONF

systemctl restart postgresql

# ─── 4. Redis 7 ──────────────────────────────────────────────────────
echo ">>> Installing Redis..."
apt-get install -y redis-server

REDIS_CONF="/etc/redis/redis.conf"
cp "$REDIS_CONF" "${REDIS_CONF}.bak"
sed -i "s/^# requirepass .*/requirepass ${REDIS_PASS}/" "$REDIS_CONF"
sed -i "s/^bind 127.0.0.1 -::1/bind 127.0.0.1/" "$REDIS_CONF"

# Security: disable dangerous commands
echo "" >> "$REDIS_CONF"
echo "# YantraGO security settings" >> "$REDIS_CONF"
echo "rename-command FLUSHDB \"\"" >> "$REDIS_CONF"
echo "rename-command FLUSHALL \"\"" >> "$REDIS_CONF"
echo "rename-command CONFIG \"\"" >> "$REDIS_CONF"
echo "rename-command DEBUG \"\"" >> "$REDIS_CONF"

# Production settings (reduced for 1GB RAM)
cat >> "$REDIS_CONF" <<CONF

# YantraGO production settings (1GB RAM)
maxmemory 128mb
maxmemory-policy allkeys-lru
appendonly yes
appendfsync everysec
CONF

systemctl enable redis-server
systemctl restart redis-server

# ─── 5. RabbitMQ 3 ───────────────────────────────────────────────────
echo ">>> Installing RabbitMQ..."
cat <<EOF > /etc/apt/sources.list.d/rabbitmq.list
deb https://ppa1.rabbitmq.com/rabbitmq/rabbitmq-erlang/deb/ubuntu $(lsb_release -cs) main
deb https://ppa1.rabbitmq.com/rabbitmq/rabbitmq-server/deb/ubuntu $(lsb_release -cs) main
EOF

curl -fsSL https://ppa1.rabbitmq.com/rabbitmq/rabbitmq-signing-key-public.asc | apt-key add -
apt-get update
apt-get install -y rabbitmq-server

rabbitmq-plugins enable rabbitmq_management

rabbitmqctl add_user "$RABBIT_USER" "$RABBIT_PASS"
rabbitmqctl set_user_tags "$RABBIT_USER" administrator
rabbitmqctl set_permissions -p / "$RABBIT_USER" ".*" ".*" ".*"
rabbitmqctl delete_user guest 2>/dev/null || true

cat > /etc/rabbitmq/rabbitmq.conf <<CONF
# YantraGO RabbitMQ configuration (single server)
listeners.tcp.default = 5672
management.tcp.port = 15672
heartbeat = 60
vm_memory_high_watermark.relative = 0.4
CONF

systemctl enable rabbitmq-server
systemctl restart rabbitmq-server

# ─── 6. Java 17 (Eclipse Temurin) ────────────────────────────────────
echo ">>> Installing Java 17..."
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" > /etc/apt/sources.list.d/adoptium.list
apt-get update
apt-get install -y temurin-17-jdk
java -version

# ─── 7. Node.js 20 (for admin-web) ───────────────────────────────────
echo ">>> Installing Node.js 20..."
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt-get install -y nodejs
node --version
npm --version

# ─── 8. Nginx ────────────────────────────────────────────────────────
echo ">>> Installing Nginx..."
apt-get install -y nginx
systemctl enable nginx
systemctl start nginx

# ─── 9. Certbot for SSL ──────────────────────────────────────────────
echo ">>> Installing Certbot..."
apt-get install -y certbot python3-certbot-nginx

# ─── 10. Create yantrago user ────────────────────────────────────────
echo ">>> Creating yantrago user..."
if ! id -u yantrago &>/dev/null; then
    useradd -r -m -d /opt/yantrago -s /bin/bash yantrago
fi

# ─── 11. Create directories ──────────────────────────────────────────
echo ">>> Creating directories..."
mkdir -p /opt/yantrago/backend
mkdir -p /opt/yantrago/gateway
mkdir -p /opt/yantrago/admin-web
mkdir -p /opt/yantrago/logs
mkdir -p /opt/yantrago/config
mkdir -p /opt/yantrago/backups
mkdir -p /opt/yantrago/repo
chown -R yantrago:yantrago /opt/yantrago

# ─── 12. Generate production config ──────────────────────────────────
echo ">>> Generating application-prod.yml..."
cat > /opt/yantrago/config/application-prod.yml <<YAML
# YantraGO Backend — Production Configuration
# Generated by single-server-setup.sh
# File permissions: chmod 600

server:
  port: 8080

spring:
  profiles:
    active: prod

  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASS}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false

  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ${REDIS_PASS}
      timeout: 5000

  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: ${RABBIT_USER}
    password: ${RABBIT_PASS}
    virtual-host: /

jwt:
  access:
    secret: ${JWT_ACCESS}
    ttl: 900
  refresh:
    secret: ${JWT_REFRESH}
    ttl: 2592000

logging:
  level:
    root: INFO
    com.yantrago: INFO
    org.springframework.web: WARN
    org.springframework.security: WARN
    org.flywaydb: INFO
  file:
    name: /opt/yantrago/logs/backend.log
YAML

cat > /opt/yantrago/config/gateway-prod.yml <<YAML
# YantraGO Gateway — Production Configuration
# Generated by single-server-setup.sh
# File permissions: chmod 600

server:
  port: 8081

spring:
  profiles:
    active: prod

  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASS}
    driver-class-name: org.postgresql.Driver

  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ${REDIS_PASS}
      database: 0

  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: ${RABBIT_USER}
    password: ${RABBIT_PASS}

tcp:
  server:
    port: 5000

jt808:
  tcp:
    enabled: true
    port: 5001

fencing:
  tcp:
    enabled: true
    port: 5002

jt1076:
  rtp:
    enabled: false
    port: 5003

logging:
  level:
    com.yantrago.gateway: INFO
    com.yantrago.gateway.tcp: INFO
  file:
    name: /opt/yantrago/logs/gateway.log
YAML

chmod 600 /opt/yantrago/config/application-prod.yml
chmod 600 /opt/yantrago/config/gateway-prod.yml
chown yantrago:yantrago /opt/yantrago/config/*.yml

# ─── 13. Save credentials ────────────────────────────────────────────
CRED_FILE="/opt/yantrago/config/credentials.txt"
cat > "$CRED_FILE" <<CRED
=== YantraGO Credentials ===
Generated: $(date)
Domain: ${DOMAIN}

PostgreSQL:
  Database: ${DB_NAME}
  User:     ${DB_USER}
  Password: ${DB_PASS}
  Host:     127.0.0.1
  Port:     5432

Redis:
  Host:     127.0.0.1
  Port:     6379
  Password: ${REDIS_PASS}

RabbitMQ:
  Host:     127.0.0.1
  AMQP Port:    5672
  Mgmt Port:    15672
  User:     ${RABBIT_USER}
  Password: ${RABBIT_PASS}

JWT:
  Access Secret:  ${JWT_ACCESS}
  Refresh Secret: ${JWT_REFRESH}

IMPORTANT: Save this file securely and delete it after noting the credentials.
CRED
chmod 600 "$CRED_FILE"
chown yantrago:yantrago "$CRED_FILE"

# ─── 14. Install systemd units ───────────────────────────────────────
echo ">>> Installing systemd units..."
cat > /etc/systemd/system/yantrago-backend.service <<SVC
[Unit]
Description=YantraGO Backend API
After=network.target postgresql.service redis-server.service rabbitmq-server.service
Wants=postgresql.service redis-server.service rabbitmq-server.service

[Service]
Type=simple
User=yantrago
Group=yantrago
WorkingDirectory=/opt/yantrago/backend
ExecStart=/usr/bin/java -Xms128m -Xmx256m -XX:+UseG1GC -jar /opt/yantrago/backend/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StartLimitInterval=60
StartLimitBurst=3
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_CONFIG_ADDITIONAL_LOCATION=/opt/yantrago/config/"
StandardOutput=journal
StandardError=journal
SyslogIdentifier=yantrago-backend
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/opt/yantrago/logs /opt/yantrago/backend
ProtectHome=true

[Install]
WantedBy=multi-user.target
SVC

cat > /etc/systemd/system/yantrago-gateway.service <<SVC
[Unit]
Description=YantraGO Device Gateway
After=network.target postgresql.service redis-server.service rabbitmq-server.service
Wants=postgresql.service redis-server.service rabbitmq-server.service

[Service]
Type=simple
User=yantrago
Group=yantrago
WorkingDirectory=/opt/yantrago/gateway
ExecStart=/usr/bin/java -Xms128m -Xmx256m -XX:+UseG1GC -jar /opt/yantrago/gateway/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StartLimitInterval=60
StartLimitBurst=3
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_CONFIG_ADDITIONAL_LOCATION=/opt/yantrago/config/"
StandardOutput=journal
StandardError=journal
SyslogIdentifier=yantrago-gateway
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/opt/yantrago/logs /opt/yantrago/gateway
ProtectHome=true
LimitNOFILE=1000000

[Install]
WantedBy=multi-user.target
SVC

systemctl daemon-reload

# ─── 15. Install Nginx config ────────────────────────────────────────
echo ">>> Installing Nginx config..."
cat > /etc/nginx/sites-available/yantrago <<NGINX
# YantraGO — Single-server Nginx config
# Domain: ${DOMAIN}
# Routes: / → admin web (Next.js :3001), /api/ → backend (:8080), /ws/ → WebSocket

limit_req_zone \$binary_remote_addr zone=api_limit:10m rate=10r/s;

upstream yantrago_backend {
    server 127.0.0.1:8080;
    keepalive 16;
}

upstream yantrago_admin_web {
    server 127.0.0.1:3001;
    keepalive 8;
}

# HTTP — redirect to HTTPS
server {
    listen 80;
    server_name ${DOMAIN} www.${DOMAIN};

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

# HTTPS — Main server
server {
    listen 443 ssl http2;
    server_name ${DOMAIN};

    # SSL certificates (will be created by certbot)
    ssl_certificate /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    # Security headers
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    client_max_body_size 10m;

    # Backend API
    location /api/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://yantrago_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
    }

    # WebSocket endpoint
    location /ws/ {
        proxy_pass http://yantrago_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # Actuator (restricted to localhost)
    location /actuator/ {
        allow 127.0.0.1;
        deny all;
        proxy_pass http://yantrago_backend;
    }

    # Health check
    location /health {
        proxy_pass http://yantrago_backend/actuator/health;
        access_log off;
    }

    # Admin web (Next.js) — catch-all
    location / {
        proxy_pass http://yantrago_admin_web;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}

# HTTPS — www redirect to apex
server {
    listen 443 ssl http2;
    server_name www.${DOMAIN};

    ssl_certificate /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;

    return 301 https://${DOMAIN}\$request_uri;
}
NGINX

ln -sf /etc/nginx/sites-available/yantrago /etc/nginx/sites-enabled/yantrago
rm -f /etc/nginx/sites-enabled/default

# Create certbot webroot directory
mkdir -p /var/www/certbot
chown -R www-data:www-data /var/www/certbot

# ─── 16. Firewall ────────────────────────────────────────────────────
echo ">>> Configuring firewall..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing

# SSH
ufw allow 22/tcp

# HTTP/HTTPS (public)
ufw allow 80/tcp
ufw allow 443/tcp

# TCP device ports (public — devices connect)
ufw allow 5000/tcp
ufw allow 5001/tcp
ufw allow 5002/tcp

# RabbitMQ management (restrict to SSH tunnel or VPN)
# ufw allow from 127.0.0.1 to any port 15672

ufw --force enable
ufw status verbose

# ─── Done ────────────────────────────────────────────────────────────
echo ""
echo "========================================================"
echo "=== YantraGO Single-Server Setup Complete ==="
echo "========================================================"
echo ""
echo "Domain:       ${DOMAIN}"
echo "Java:         $(java -version 2>&1 | head -1)"
echo "Node.js:      $(node --version)"
echo "Nginx:        $(nginx -v 2>&1)"
echo "PostgreSQL:   $(psql --version 2>&1)"
echo "Redis:        $(redis-server --version 2>&1)"
echo "RabbitMQ:     $(rabbitmqctl version 2>&1 | head -1)"
echo ""
echo "Credentials saved to: /opt/yantrago/config/credentials.txt"
echo "Config files:"
echo "  /opt/yantrago/config/application-prod.yml  (backend)"
echo "  /opt/yantrago/config/gateway-prod.yml      (gateway)"
echo ""
echo "=== NEXT STEPS ==="
echo ""
echo "1. Point DNS A record for ${DOMAIN} and www.${DOMAIN} to this server's IP"
echo ""
echo "2. Get SSL certificate:"
echo "   sudo certbot --nginx -d ${DOMAIN} -d www.${DOMAIN}"
echo ""
echo "3. Clone the repo and build JARs:"
echo "   cd /opt/yantrago/repo"
echo "   git clone https://github.com/yantrago/yantrago.git ."
echo "   ./gradlew :backend:bootJar :device-gateway:bootJar -x test"
echo ""
echo "4. Deploy backend:"
echo "   cp backend/build/libs/backend-1.0.0.jar /opt/yantrago/backend/app.jar"
echo "   chown yantrago:yantrago /opt/yantrago/backend/app.jar"
echo "   systemctl enable yantrago-backend && systemctl start yantrago-backend"
echo ""
echo "5. Deploy gateway:"
echo "   cp device-gateway/build/libs/device-gateway-1.0.0.jar /opt/yantrago/gateway/app.jar"
echo "   chown yantrago:yantrago /opt/yantrago/gateway/app.jar"
echo "   systemctl enable yantrago-gateway && systemctl start yantrago-gateway"
echo ""
echo "6. Deploy admin web:"
echo "   cd admin-web && npm install && npm run build"
echo "   (use pm2 or systemd to run 'npm start' on port 3001)"
echo ""
echo "7. Verify:"
echo "   systemctl status yantrago-backend"
echo "   systemctl status yantrago-gateway"
echo "   curl https://${DOMAIN}/health"
echo ""
echo "IMPORTANT: Save the credentials from /opt/yantrago/config/credentials.txt"
echo "           securely, then delete that file."
