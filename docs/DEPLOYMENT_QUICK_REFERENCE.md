# YantraGO — Deployment Quick Reference

> **Purpose:** Step-by-step guide for deploying the latest code to production.
> Use this every time you need to deploy backend, gateway, or admin-web.

---

## Connection Details

| Item | Value |
|------|-------|
| **Production server** | `65.20.92.165` |
| **Hostname** | `yantrago-prod-01` |
| **SSH user** | `root` |
| **SSH key** | `C:\Users\Admin\.ssh\id_ed25519` |
| **Repo on server** | `/opt/yantrago/repo` |
| **Production config** | `/opt/yantrago/config/application-prod.yml` |
| **Domain** | `https://yantrago.com` |

---

## SSH Connection

```bash
# From Windows (Git Bash / PowerShell)
ssh -i ~/.ssh/id_ed25519 root@65.20.92.165
```

---

## Deployment Steps

### Step 1: Push local code to GitHub

```bash
# From local repo D:\CascadeProjects\yantrago
git push origin main
```

Verify push succeeded:
```bash
git log --oneline -1   # should show your latest commit
```

### Step 2: Pull latest code on the server

```bash
ssh -i ~/.ssh/id_ed25519 root@65.20.92.165

cd /opt/yantrago/repo
git pull origin main
```

If pull fails due to untracked files:
```bash
# Remove untracked files that conflict with tracked versions
git checkout -- .
git clean -fd admin-web/next-env.d.ts admin-web/package-lock.json
git pull origin main
```

### Step 3: Deploy using the single-server script

```bash
cd /opt/yantrago/repo

# Deploy ALL services (backend + gateway + admin-web)
bash infra/vultr/deploy/deploy-single-server.sh all

# OR deploy a single service:
bash infra/vultr/deploy/deploy-single-server.sh backend
bash infra/vultr/deploy/deploy-single-server.sh gateway
bash infra/vultr/deploy/deploy-single-server.sh admin-web
```

The script will:
1. Build the JAR (backend/gateway) or Next.js bundle (admin-web)
2. Backup the current version to `/opt/yantrago/backups/`
3. Copy the new build to the deployment directory
4. Restart the systemd service
5. Wait for health check (up to 60 seconds)

---

## Manual Deployment (if script times out)

The script's 60-second health timeout may be too short if Flyway migrations
are running. Use manual deployment instead:

### Backend (manual)

```bash
cd /opt/yantrago/repo
./gradlew :backend:bootJar -x test

# Backup and deploy
cp /opt/yantrago/backend/app.jar /opt/yantrago/backups/backend-$(date +%Y%m%d%H%M%S).jar
cp backend/build/libs/backend-1.0.0.jar /opt/yantrago/backend/app.jar
chown yantrago:yantrago /opt/yantrago/backend/app.jar

# Restart and wait
sudo systemctl restart yantrago-backend

# Wait for health (backend takes 30-60s, longer if migrations run)
for i in $(seq 1 45); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "Backend UP"
    curl -s http://localhost:8080/actuator/health
    break
  fi
  sleep 2
done
```

### Gateway (manual)

```bash
cd /opt/yantrago/repo
./gradlew :device-gateway:bootJar -x test

cp /opt/yantrago/gateway/app.jar /opt/yantrago/backups/gateway-$(date +%Y%m%d%H%M%S).jar
cp device-gateway/build/libs/device-gateway-1.0.0.jar /opt/yantrago/gateway/app.jar
chown yantrago:yantrago /opt/yantrago/gateway/app.jar

sudo systemctl restart yantrago-gateway

for i in $(seq 1 30); do
  if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "Gateway UP"
    curl -s http://localhost:8081/actuator/health
    break
  fi
  sleep 2
done
```

### Admin Web (manual)

```bash
cd /opt/yantrago/repo/admin-web
npm install
npm run build

# Deploy
tar -czf /tmp/admin-web-build.tar.gz .next
tar -xzf /tmp/admin-web-build.tar.gz -C /opt/yantrago/admin-web/
sudo systemctl restart yantrago-admin-web
```

---

## Post-Deployment Verification

### Check all services

```bash
# Backend health
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Gateway health
curl -s http://localhost:8081/actuator/health
# Expected: {"status":"UP"}

# Admin web (should redirect to login)
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
# Expected: 200 or 307

# All systemd services
sudo systemctl status yantrago-backend yantrago-gateway yantrago-admin-web
```

### Check RabbitMQ queues

```bash
sudo rabbitmqctl list_queues name messages consumers | grep -E "command|notification"
# Expected: all queues with 0 or low message counts, consumers > 0
```

### Check logs for errors

```bash
# Backend logs (last 20 lines)
tail -20 /opt/yantrago/backend/logs/yantrago-backend.log

# Gateway logs (last 20 lines)
tail -20 /opt/yantrago/gateway/logs/gateway.log

# Look for ERROR or Exception
grep -i "error\|exception" /opt/yantrago/backend/logs/yantrago-backend.log | tail -10
```

---

## Rollback

If deployment fails or causes issues:

```bash
# List available backups
ls -lt /opt/yantrago/backups/

# Rollback backend
cp /opt/yantrago/backups/backend-<timestamp>.jar /opt/yantrago/backend/app.jar
chown yantrago:yantrago /opt/yantrago/backend/app.jar
sudo systemctl restart yantrago-backend

# Rollback gateway
cp /opt/yantrago/backups/gateway-<timestamp>.jar /opt/yantrago/gateway/app.jar
chown yantrago:yantrago /opt/yantrago/gateway/app.jar
sudo systemctl restart yantrago-gateway
```

Or use the rollback script:
```bash
cd /opt/yantrago/repo
bash infra/vultr/deploy/rollback.sh
```

---

## Production Configuration

The production config is at `/opt/yantrago/config/application-prod.yml`.

**Important:**
- This file is NOT in the git repo (contains secrets)
- Changes to this file persist across deployments
- The deploy script does NOT overwrite it
- Always back it up before modifying: `cp /opt/yantrago/config/application-prod.yml /opt/yantrago/config/application-prod.yml.bak`

To temporarily enable health details for debugging:
```bash
# Backup
cp /opt/yantrago/config/application-prod.yml /opt/yantrago/config/application-prod.yml.bak

# Edit: under management:endpoints:health: change show-details to "always"
sudo nano /opt/yantrago/config/application-prod.yml

# Restart
sudo systemctl restart yantrago-backend

# ... debug ...

# REVERT when done
cp /opt/yantrago/config/application-prod.yml.bak /opt/yantrago/config/application-prod.yml
sudo systemctl restart yantrago-backend
```

---

## Service Paths

| Service | Binary/Build | Logs | Systemd |
|---------|-------------|------|---------|
| Backend | `/opt/yantrago/backend/app.jar` | `/opt/yantrago/backend/logs/yantrago-backend.log` | `yantrago-backend` |
| Gateway | `/opt/yantrago/gateway/app.jar` | `/opt/yantrago/gateway/logs/gateway.log` | `yantrago-gateway` |
| Admin Web | `/opt/yantrago/admin-web/` (Next.js) | `journalctl -u yantrago-admin-web` | `yantrago-admin-web` |

---

## Common Issues and Fixes

### 1. Backend health check timeout
**Cause:** Flyway migrations running on startup (takes >60s)
**Fix:** Use manual deployment (above) with longer wait

### 2. RabbitMQ queue argument mismatch
```
PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'
```
**Cause:** Queue args changed in code, but existing queue in RabbitMQ has old args
**Fix:** Delete the queue (if 0 messages) and restart backend:
```bash
sudo rabbitmqctl delete_queue yantrago.command.result.queue
sudo systemctl restart yantrago-backend
```

### 3. Git pull blocked by untracked files
**Fix:**
```bash
git checkout -- .
git clean -fd
git pull origin main
```

### 4. Device with NULL organization_id
**Symptom:** `OfflineDetectionScheduler` errors in logs
**Fix:** Already fixed in code — scheduler skips devices without org_id

---

## Quick Deploy (All-in-One)

For a full deployment from local Windows machine:

```bash
# 1. Push to GitHub
cd /d/CascadeProjects/yantrago
git push origin main

# 2. SSH and deploy
ssh -i ~/.ssh/id_ed25519 root@65.20.92.165 << 'EOF'
cd /opt/yantrago/repo
git pull origin main
bash infra/vultr/deploy/deploy-single-server.sh all
EOF

# 3. Verify
ssh -i ~/.ssh/id_ed25519 root@65.20.92.165 \
  "curl -s http://localhost:8080/actuator/health && echo && \
   curl -s http://localhost:8081/actuator/health && echo && \
   curl -s -o /dev/null -w 'admin-web: %{http_code}\n' http://localhost:3001"
```

---

## APK Build (Mobile)

APK builds are done via GitHub Actions, not on the server.

1. Push code to `main` (triggers workflow automatically if mobile files changed)
2. Or manually trigger: GitHub → Actions → "Build Android APK" → Run workflow
3. Download APK from GitHub Releases:
   `https://github.com/yantralink/yantragoFence/releases`

Workflow file: `.github/workflows/build-apk.yml`
- Flutter 3.47.2
- Java 17
- API base URL: `https://yantrago.com`
- WebSocket base URL: `wss://yantrago.com/ws`
