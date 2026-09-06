#!/usr/bin/env bash
set -euo pipefail

echo "=== Updating Nginx config with HTTPS ==="

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

# HTTP — redirect to HTTPS
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

# HTTPS — Main server
server {
    listen 443 ssl http2;
    server_name yantrago.com;

    ssl_certificate /etc/letsencrypt/live/yantrago.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yantrago.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

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
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
    }

    # WebSocket endpoint
    location /ws/ {
        proxy_pass http://yantrago_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
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
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}

# HTTPS — www redirect to apex
server {
    listen 443 ssl http2;
    server_name www.yantrago.com;

    ssl_certificate /etc/letsencrypt/live/yantrago.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yantrago.com/privkey.pem;

    return 301 https://yantrago.com$request_uri;
}
NGINX

ln -sf /etc/nginx/sites-available/yantrago /etc/nginx/sites-enabled/yantrago
rm -f /etc/nginx/sites-enabled/default

nginx -t 2>&1 && systemctl reload nginx && echo "NGINX_OK"
