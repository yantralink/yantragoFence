#!/usr/bin/env bash
set -euo pipefail

echo "=== Finishing YantraGO setup ==="

# Generate passwords
DB_PASS=$(openssl rand -hex 16)
REDIS_PASS=$(openssl rand -hex 16)
RABBIT_PASS=$(openssl rand -hex 16)
JWT_ACCESS=$(openssl rand -hex 32)
JWT_REFRESH=$(openssl rand -hex 32)
DOMAIN="yantrago.com"
DB_NAME="yantrago"
DB_USER="yantrago"
RABBIT_USER="yantrago"

# ─── Set DB password (user already exists) ───────────────────────────
echo ">>> Setting database password..."
sudo -u postgres psql -c "ALTER USER ${DB_USER} WITH ENCRYPTED PASSWORD '${DB_PASS}';" 2>&1

# ─── Set Redis password ──────────────────────────────────────────────
echo ">>> Configuring Redis..."
REDIS_CONF="/etc/redis/redis.conf"
cp "$REDIS_CONF" "${REDIS_CONF}.bak2"
sed -i "s|^# requirepass .*|requirepass ${REDIS_PASS}|" "$REDIS_CONF"
sed -i "s|^bind 127.0.0.1 -::1|bind 127.0.0.1|" "$REDIS_CONF"
# Redis 6 compatible — skip rename-command (not supported in Redis 6)
# Security is handled by bind 127.0.0.1 + password + firewall
# Production settings
cat >> "$REDIS_CONF" <<CONF

# YantraGO production settings (1GB RAM)
maxmemory 128mb
maxmemory-policy allkeys-lru
appendonly yes
appendfsync everysec
CONF
systemctl restart redis-server
echo "Redis status: $(systemctl is-active redis-server)"

# ─── Configure RabbitMQ ──────────────────────────────────────────────
echo ">>> Configuring RabbitMQ..."
rabbitmqctl add_user "$RABBIT_USER" "$RABBIT_PASS" 2>/dev/null || rabbitmqctl change_password "$RABBIT_USER" "$RABBIT_PASS"
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
systemctl restart rabbitmq-server
echo "RabbitMQ status: $(systemctl is-active rabbitmq-server)"

# ─── Install Java 17 ─────────────────────────────────────────────────
echo ">>> Installing Java 17..."
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | apt-key add - 2>/dev/null
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" > /etc/apt/sources.list.d/adoptium.list
apt-get update -qq 2>&1 | tail -3
apt-get install -y temurin-17-jdk 2>&1 | tail -5
echo "Java: $(java -version 2>&1 | head -1)"

# ─── Install Node.js 20 ──────────────────────────────────────────────
echo ">>> Installing Node.js 20..."
curl -fsSL https://deb.nodesource.com/setup_20.x | bash - 2>&1 | tail -3
apt-get install -y nodejs 2>&1 | tail -3
echo "Node: $(node --version)"
echo "npm: $(npm --version)"

# ─── Nginx ───────────────────────────────────────────────────────────
echo ">>> Installing Nginx..."
apt-get install -y nginx 2>&1 | tail -3
systemctl enable nginx
systemctl start nginx
echo "Nginx status: $(systemctl is-active nginx)"

# ─── Certbot ─────────────────────────────────────────────────────────
echo ">>> Installing Certbot..."
apt-get install -y certbot python3-certbot-nginx 2>&1 | tail -3

# ─── Create yantrago user ────────────────────────────────────────────
echo ">>> Creating yantrago user..."
if ! id -u yantrago &>/dev/null; then
    useradd -r -m -d /opt/yantrago -s /bin/bash yantrago
fi

# ─── Create directories ──────────────────────────────────────────────
mkdir -p /opt/yantrago/backend /opt/yantrago/gateway /opt/yantrago/admin-web /opt/yantrago/logs /opt/yantrago/config /opt/yantrago/backups
chown -R yantrago:yantrago /opt/yantrago

# ─── Generate production configs ─────────────────────────────────────
echo ">>> Generating production configs..."
cat > /opt/yantrago/config/application-prod.yml <<YAML
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

# ─── Save credentials ────────────────────────────────────────────────
cat > /opt/yantrago/config/credentials.txt <<CRED
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
CRED
chmod 600 /opt/yantrago/config/credentials.txt
chown yantrago:yantrago /opt/yantrago/config/credentials.txt

# ─── Install systemd units ───────────────────────────────────────────
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

cat > /etc/systemd/system/yantrago-admin-web.service <<SVC
[Unit]
Description=YantraGO Admin Web (Next.js)
After=network.target
Wants=network.target

[Service]
Type=simple
User=yantrago
Group=yantrago
WorkingDirectory=/opt/yantrago/admin-web
Environment="NODE_ENV=production"
Environment="PORT=3001"
Environment="NEXT_PUBLIC_API_URL=https://yantrago.com/api"
ExecStart=/usr/bin/npm start
Restart=on-failure
RestartSec=10
StartLimitInterval=60
StartLimitBurst=3
StandardOutput=journal
StandardError=journal
SyslogIdentifier=yantrago-admin-web
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/opt/yantrago/admin-web
ProtectHome=true

[Install]
WantedBy=multi-user.target
SVC

systemctl daemon-reload

# ─── Nginx config ────────────────────────────────────────────────────
echo ">>> Installing Nginx config..."
cat > /etc/nginx/sites-available/yantrago <<'NGINX'
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

upstream yantrago_backend {
    server 127.0.0.1:8080;
    keepalive 16;
}

upstream yantrago_admin_web {
    server 127.0.0.1:3001;
    keepalive 8;
}

server {
    listen 80;
    server_name yantrago.com www.yantrago.com;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/yantrago /etc/nginx/sites-enabled/yantrago
rm -f /etc/nginx/sites-enabled/default
mkdir -p /var/www/certbot
chown -R www-data:www-data /var/www/certbot
nginx -t 2>&1 && systemctl reload nginx

# ─── Firewall ────────────────────────────────────────────────────────
echo ">>> Configuring firewall..."
ufw --force reset >/dev/null 2>&1
ufw default deny incoming >/dev/null 2>&1
ufw default allow outgoing >/dev/null 2>&1
ufw allow 22/tcp >/dev/null 2>&1
ufw allow 80/tcp >/dev/null 2>&1
ufw allow 443/tcp >/dev/null 2>&1
ufw allow 5000/tcp >/dev/null 2>&1
ufw allow 5001/tcp >/dev/null 2>&1
ufw allow 5002/tcp >/dev/null 2>&1
ufw --force enable >/dev/null 2>&1

# ─── Done ────────────────────────────────────────────────────────────
echo ""
echo "========================================================"
echo "=== YantraGO Setup Complete ==="
echo "========================================================"
echo ""
echo "Services:"
echo "  PostgreSQL: $(systemctl is-active postgresql)"
echo "  Redis:      $(systemctl is-active redis-server)"
echo "  RabbitMQ:   $(systemctl is-active rabbitmq-server)"
echo "  Nginx:      $(systemctl is-active nginx)"
echo ""
echo "Java:   $(java -version 2>&1 | head -1)"
echo "Node:   $(node --version 2>&1)"
echo ""
echo "Credentials: /opt/yantrago/config/credentials.txt"
echo ""
echo "=== NEXT STEPS ==="
echo "1. Point DNS A record for yantrago.com to this server's IP"
echo "2. Run: sudo certbot --nginx -d yantrago.com -d www.yantrago.com"
echo "3. Build and deploy: bash /opt/yantrago/repo/infra/vultr/deploy/deploy-single-server.sh all"
