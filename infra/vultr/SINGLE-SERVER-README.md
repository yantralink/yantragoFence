# YantraGO — Single-Server Vultr Deployment Guide

This guide walks you through deploying YantraGO on a single Vultr instance
with all services running on one server.

## Architecture (Single Server)

```
┌─────────────────────────────────────────────────────────┐
│                   Vultr Instance                         │
│                   (1 vCPU / 1GB RAM)                     │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │ PostgreSQL│  │  Redis   │  │ RabbitMQ │  │  Nginx  │ │
│  │  15+GIS  │  │   7      │  │    3     │  │ (proxy) │ │
│  └──────────┘  └──────────┘  └──────────┘  └────┬────┘ │
│                                                 │       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐       │       │
│  │ Backend  │  │ Gateway  │  │ Admin Web│       │       │
│  │  (:8080) │  │ (:8081)  │  │ (:3001)  │◀──────┘       │
│  │          │  │ TCP:5000 │  │ Next.js  │               │
│  │          │  │ TCP:5001 │  │          │               │
│  │          │  │ TCP:5002 │  │          │               │
│  └──────────┘  └──────────┘  └──────────┘               │
│                                                          │
│  2GB Swap │ UFW Firewall │ Let's Encrypt SSL            │
└─────────────────────────────────────────────────────────┘

Traffic routing (Nginx):
  https://yantrago.com/          → Admin Web (Next.js :3001)
  https://yantrago.com/api/      → Backend API (:8080)
  https://yantrago.com/ws/       → WebSocket (:8080)
  https://yantrago.com/health    → Backend health check
  TCP 5000-5002                  → Device Gateway (direct)
```

## Prerequisites

1. **Vultr account** — https://www.vultr.com
2. **Domain name** — `yantrago.com` pointed to your Vultr instance IP
3. **SSH key** — for secure access to the instance
4. **GitHub repo** — your YantraGO code pushed to a GitHub repository

## Step 1: Create Vultr Instance

1. Log into Vultr dashboard
2. Click **Deploy New Instance**
3. Choose:
   - **Server Type:** Cloud Compute - Regular Performance
   - **Location:** Choose closest to your users
   - **OS:** Ubuntu 22.04 LTS x64
   - **Plan:** 1 vCPU / 1GB RAM / 25GB SSD ($6/mo)
   - **Additional Features:** Enable Auto Backup (recommended)
   - **SSH Keys:** Add your public SSH key
4. Click **Deploy Now**
5. Note the server IP address

## Step 2: Point DNS

In your domain registrar's DNS management:

| Type  | Name | Value           | TTL  |
|-------|------|-----------------|------|
| A     | @    | <your-server-ip> | 300  |
| A     | www  | <your-server-ip> | 300  |

Wait for DNS propagation (check with `dig yantrago.com` or `nslookup yantrago.com`).

## Step 3: Run Server Setup

SSH into your server:

```bash
ssh root@<your-server-ip>
```

Clone the repo and run the setup script:

```bash
# Clone the repo
mkdir -p /opt/yantrago/repo
cd /opt/yantrago/repo
git clone https://github.com/yantrago/yantrago.git .

# Run the single-server setup script
# This installs: PostgreSQL, Redis, RabbitMQ, Java 17, Node.js 20, Nginx, Certbot
# Creates: database, user, swap file, firewall rules, systemd units, production config
sudo YANTRAGO_DOMAIN=yantrago.com bash infra/vultr/server-setup/single-server-setup.sh
```

The script will output all generated credentials. **Save them securely.**

Credentials are also saved to `/opt/yantrago/config/credentials.txt` (chmod 600).

## Step 4: Get SSL Certificate

```bash
sudo certbot --nginx -d yantrago.com -d www.yantrago.com
```

Choose option 2 (redirect HTTP to HTTPS) when prompted.

Certbot will automatically modify the Nginx config and set up auto-renewal.

## Step 5: Build and Deploy

### Deploy Backend + Gateway + Admin Web (all at once)

```bash
cd /opt/yantrago/repo
bash infra/vultr/deploy/deploy-single-server.sh all
```

### Or deploy individually

```bash
# Backend only
bash infra/vultr/deploy/deploy-single-server.sh backend

# Gateway only
bash infra/vultr/deploy/deploy-single-server.sh gateway

# Admin web only
bash infra/vultr/deploy/deploy-single-server.sh admin-web
```

### Install admin-web systemd unit

```bash
sudo cp infra/vultr/systemd/yantrago-admin-web.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable yantrago-admin-web
sudo systemctl start yantrago-admin-web
```

## Step 6: Enable Services

```bash
sudo systemctl enable yantrago-backend yantrago-gateway yantrago-admin-web
sudo systemctl start yantrago-backend yantrago-gateway yantrago-admin-web
```

## Step 7: Verify

```bash
# Check service status
sudo systemctl status yantrago-backend
sudo systemctl status yantrago-gateway
sudo systemctl status yantrago-admin-web

# Check health endpoints
curl http://localhost:8080/actuator/health   # Backend
curl http://localhost:8081/actuator/health   # Gateway
curl http://localhost:3001                    # Admin Web

# Check via Nginx (public)
curl https://yantrago.com/health
curl https://yantrago.com/api/v1/health

# Check Nginx
sudo nginx -t
sudo systemctl status nginx

# Check firewall
sudo ufw status verbose

# Check TCP ports are listening
sudo ss -tlnp | grep -E '5000|5001|5002|8080|8081|3001'
```

## Step 8: Test Device Connection

From your local machine, test TCP connectivity to the gateway:

```bash
# Test Concox V5 port (5000)
nc -zv yantrago.com 5000

# Test JT808 port (5001)
nc -zv yantrago.com 5001

# Test Fencing port (5002)
nc -zv yantrago.com 5002
```

## File Layout

```
/opt/yantrago/
├── repo/              # Git repository (source code)
├── backend/
│   └── app.jar        # Backend JAR
├── gateway/
│   └── app.jar        # Gateway JAR
├── admin-web/
│   ├── .next/         # Next.js build output
│   ├── package.json
│   └── public/
├── config/
│   ├── application-prod.yml   # Backend config (chmod 600)
│   ├── gateway-prod.yml       # Gateway config (chmod 600)
│   └── credentials.txt        # All credentials (chmod 600)
├── logs/
│   ├── backend.log
│   └── gateway.log
└── backups/
    └── *.jar          # Previous JAR versions
```

## Service Management

```bash
# Restart services
sudo systemctl restart yantrago-backend
sudo systemctl restart yantrago-gateway
sudo systemctl restart yantrago-admin-web

# View logs
sudo journalctl -u yantrago-backend -f
sudo journalctl -u yantrago-gateway -f
sudo journalctl -u yantrago-admin-web -f

# Application logs
tail -f /opt/yantrago/logs/backend.log
tail -f /opt/yantrago/logs/gateway.log
```

## Database Management

```bash
# Connect to PostgreSQL
sudo -u postgres psql -d yantrago

# Check Flyway migration status (via backend logs)
grep -i "flyway" /opt/yantrago/logs/backend.log

# Backup database
pg_dump -U yantrago -h 127.0.0.1 yantrago > /opt/yantrago/backups/db-$(date +%Y%m%d).sql

# Restore database
psql -U yantrago -h 127.0.0.1 yantrago < /opt/yantrago/backups/db-YYYYMMDD.sql
```

## RabbitMQ Management

Access the RabbitMQ management UI via SSH tunnel:

```bash
# From your local machine
ssh -L 15672:localhost:15672 root@<your-server-ip>
```

Then open http://localhost:15672 in your browser.
Login with the credentials from `/opt/yantrago/config/credentials.txt`.

## Updating the Application

```bash
cd /opt/yantrago/repo
git pull origin main
bash infra/vultr/deploy/deploy-single-server.sh all
```

## Rollback

```bash
# List backups
ls -la /opt/yantrago/backups/

# Rollback backend to a specific version
sudo systemctl stop yantrago-backend
cp /opt/yantrago/backups/backend-YYYYMMDDHHMMSS.jar /opt/yantrago/backend/app.jar
chown yantrago:yantrago /opt/yantrago/backend/app.jar
sudo systemctl start yantrago-backend

# Rollback gateway
sudo systemctl stop yantrago-gateway
cp /opt/yantrago/backups/gateway-YYYYMMDDHHMMSS.jar /opt/yantrago/gateway/app.jar
chown yantrago:yantrago /opt/yantrago/gateway/app.jar
sudo systemctl start yantrago-gateway
```

## Security Checklist

- [ ] SSH key authentication enabled (disable password auth)
- [ ] UFW firewall enabled (ports 22, 80, 443, 5000-5002)
- [ ] SSL certificate installed and auto-renewing
- [ ] `credentials.txt` saved securely and deleted from server
- [ ] `application-prod.yml` and `gateway-prod.yml` have chmod 600
- [ ] PostgreSQL listens on 127.0.0.1 only (single-server)
- [ ] Redis listens on 127.0.0.1 only
- [ ] RabbitMQ guest user deleted
- [ ] Vultr auto-backup enabled

## Cost Estimate (Single Server)

| Resource        | Monthly Cost |
|----------------|--------------|
| Vultr 1vCPU/1GB | $6.00       |
| Auto Backup     | $1.20       |
| Domain (annual) | ~$1/mo      |
| **Total**       | **~$8/mo**  |

## Scaling Up

When you outgrow the single-server setup:

1. **Vertical scale first** — upgrade to 2 vCPU / 4GB RAM ($24/mo)
2. **Split database** — move PostgreSQL to a dedicated instance
3. **Split gateway** — move the TCP gateway to a dedicated instance
4. **Add Redis cache** — move Redis to a dedicated instance
5. **Use the 5-server scripts** — see `infra/vultr/server-setup/` for individual server setup scripts

## Troubleshooting

### Backend won't start
```bash
sudo journalctl -u yantrago-backend -n 50 --no-pager
# Check if PostgreSQL is running
sudo systemctl status postgresql
# Check if port 8080 is in use
sudo ss -tlnp | grep 8080
```

### Gateway won't start
```bash
sudo journalctl -u yantrago-gateway -n 50 --no-pager
# Check if RabbitMQ is running
sudo systemctl status rabbitmq-server
# Check if TCP ports are in use
sudo ss -tlnp | grep -E '5000|5001|5002'
```

### Nginx 502 Bad Gateway
```bash
# Check if backend is running
sudo systemctl status yantrago-backend
# Check Nginx error log
sudo tail -f /var/log/nginx/error.log
```

### Out of memory (OOM)
```bash
# Check memory usage
free -h
# Check swap
swapon --show
# Check for OOM killer
dmesg | grep -i "out of memory"
# Reduce JVM heap if needed: edit systemd unit
# -Xms128m -Xmx256m is already set for 1GB RAM
```
