# YantraGO — Vultr Infrastructure

Deployment scripts and server setup configs for running YantraGO on Vultr cloud instances.

## Server Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  App Server  │     │ Gateway Srv │     │  DB Server  │
│  (Backend +  │────▶│  (TCP 5000- │     │ PostgreSQL  │
│   Admin Web) │     │   5002)     │     │  + PostGIS  │
│  Nginx proxy │     │             │     │             │
└──────┬───────┘     └──────┬──────┘     └─────────────┘
       │                    │
       ├────────────────────┼──── Redis Server ────┐
       │                    │                      │
       └────────────────────┴──── RabbitMQ Server ─┘
```

## Server Setup

### 1. Database Server (PostgreSQL 15 + PostGIS)

```bash
ssh root@<db-server-ip>
git clone https://github.com/yantrago/yantrago.git /opt/yantrago/repo
cd /opt/yantrago/repo
sudo bash infra/vultr/server-setup/db-server-setup.sh
```

**Ports:** 5432 (internal only)

### 2. App Server (Backend API + Admin Web + Nginx)

```bash
ssh root@<app-server-ip>
git clone https://github.com/yantrago/yantrago.git /opt/yantrago/repo
cd /opt/yantrago/repo
sudo bash infra/vultr/server-setup/app-server-setup.sh
```

**Ports:** 80, 443 (public), 8080, 3001 (internal)

### 3. Gateway Server (TCP Device Gateway)

```bash
ssh root@<gateway-server-ip>
git clone https://github.com/yantrago/yantrago.git /opt/yantrago/repo
cd /opt/yantrago/repo
sudo bash infra/vultr/server-setup/gateway-server-setup.sh
```

**Ports:** 5000, 5001, 5002 (public — devices connect), 8081 (internal)

### 4. Redis Server

```bash
ssh root@<redis-server-ip>
git clone https://github.com/yantrago/yantrago.git /opt/yantrago/repo
cd /opt/yantrago/repo
sudo bash infra/vultr/server-setup/redis-server-setup.sh
```

**Ports:** 6379 (internal only)

### 5. RabbitMQ Server

```bash
ssh root@<rabbitmq-server-ip>
git clone https://github.com/yantrago/yantrago.git /opt/yantrago/repo
cd /opt/yantrago/repo
sudo bash infra/vultr/server-setup/rabbitmq-server-setup.sh
```

**Ports:** 5672 (AMQP, internal), 15672 (Management UI, internal)

## Firewall Setup

After running server setup, apply firewall rules on each server:

```bash
# On DB server
sudo bash -c 'while IFS= read -r line; do eval "$line"; done < infra/vultr/firewall/db-server.ufw'

# On App server
sudo bash -c 'while IFS= read -r line; do eval "$line"; done < infra/vultr/firewall/app-server.ufw'

# On Gateway server
sudo bash -c 'while IFS= read -r line; do eval "$line"; done < infra/vultr/firewall/gateway-server.ufw'

# On Redis server
sudo bash -c 'while IFS= read -r line; do eval "$line"; done < infra/vultr/firewall/redis-server.ufw'
```

Set environment variables for IP restrictions:
```bash
export ADMIN_IP="your.admin.ip.here"
export APP_SERVER_IP="10.0.0.x"
export GATEWAY_SERVER_IP="10.0.0.y"
```

## Systemd Services

Install systemd units on the respective servers:

```bash
# On App server (backend)
sudo cp infra/vultr/systemd/yantrago-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable yantrago-backend

# On Gateway server
sudo cp infra/vultr/systemd/yantrago-gateway.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable yantrago-gateway
```

## Nginx Configuration

On the App server:

```bash
sudo cp infra/vultr/nginx/nginx.conf /etc/nginx/sites-available/yantrago
sudo cp infra/vultr/nginx/nginx.websocket.conf /etc/nginx/sites-available/
sudo cp infra/vultr/nginx/nginx.ssl.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/yantrago /etc/nginx/sites-enabled/yantrago
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

### SSL Certificates (Let's Encrypt)

```bash
sudo certbot --nginx -d api.yantrago.com -d admin.yantrago.com
```

## Deployment

### Deploy Backend

```bash
./gradlew :backend:bootJar -x test
YANTRAGO_APP_HOST=<app-server-ip> bash infra/vultr/deploy/deploy-backend.sh
```

### Deploy Gateway

```bash
./gradlew :device-gateway:bootJar -x test
YANTRAGO_GATEWAY_HOST=<gateway-server-ip> bash infra/vultr/deploy/deploy-gateway.sh
```

### Deploy Admin Web

```bash
cd admin-web && npm run build && cd ..
YANTRAGO_APP_HOST=<app-server-ip> bash infra/vultr/deploy/deploy-admin-web.sh
```

### Rollback

```bash
# Rollback backend to latest backup
YANTRAGO_APP_HOST=<app-server-ip> bash infra/vultr/deploy/rollback.sh backend

# Rollback gateway to specific version
YANTRAGO_GATEWAY_HOST=<gateway-server-ip> bash infra/vultr/deploy/rollback.sh gateway 20240101120000

# Rollback admin web
YANTRAGO_APP_HOST=<app-server-ip> bash infra/vultr/deploy/rollback.sh admin-web
```

## Configuration

Create `application-prod.yml` on each server at `/opt/yantrago/config/`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<db-server-ip>:5432/yantrago
    username: yantrago
    password: <secure-password>
  redis:
    host: <redis-server-ip>
    password: <secure-password>
  rabbitmq:
    host: <rabbitmq-server-ip>
    username: yantrago
    password: <secure-password>
```

Set file permissions: `chmod 600 /opt/yantrago/config/application-prod.yml`

## File Layout

```
infra/vultr/
├── server-setup/
│   ├── db-server-setup.sh        # PostgreSQL 15 + PostGIS
│   ├── app-server-setup.sh       # Java 17 + Nginx
│   ├── gateway-server-setup.sh   # Java 17 + TCP optimizations
│   ├── redis-server-setup.sh     # Redis 7 + password
│   └── rabbitmq-server-setup.sh  # RabbitMQ 3 + management
├── systemd/
│   ├── yantrago-backend.service
│   ├── yantrago-gateway.service
│   └── yantrago-rabbitmq.service
├── nginx/
│   ├── nginx.conf                # Reverse proxy + SSL
│   ├── nginx.websocket.conf      # WebSocket upgrade config
│   └── nginx.ssl.conf            # SSL/TLS settings
├── firewall/
│   ├── db-server.ufw             # 5432 internal
│   ├── app-server.ufw            # 80/443 public, 8080/3001 internal
│   ├── gateway-server.ufw        # 5000-5002 public, 8081 internal
│   └── redis-server.ufw          # 6379 internal
├── deploy/
│   ├── deploy-backend.sh         # Upload + restart backend
│   ├── deploy-gateway.sh         # Upload + restart gateway
│   ├── deploy-admin-web.sh       # Build + upload + restart admin web
│   └── rollback.sh               # Rollback any service
└── README.md
```
