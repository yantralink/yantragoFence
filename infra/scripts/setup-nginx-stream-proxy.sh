#!/usr/bin/env bash
set -euo pipefail

echo "=== Setting up Nginx SNI-based stream proxy on port 443 ==="

# 1. Change HTTPS server to listen on 8443 (internal)
sed -i 's/listen 443 ssl http2;/listen 8443 ssl http2;/g' /etc/nginx/sites-available/yantrago
echo ">>> Changed HTTPS to listen on 8443"

# 2. Add stream block to nginx.conf (before the http block)
# The stream block uses ssl_preread to route based on SNI:
#   - SNI = yantrago.com or www.yantrago.com → HTTPS server (8443)
#   - No SNI (raw TCP from device) → TCP gateway (5000)
cat > /etc/nginx/stream-proxy.conf << 'STREAM_EOF'
# SNI-based routing on port 443
# Routes TLS traffic (with SNI) to the HTTPS server,
# and raw TCP traffic (no SNI, from IoT devices) to the TCP gateway.
map $ssl_preread_server_name $backend {
    yantrago.com      https_backend;
    www.yantrago.com  https_backend;
    default           device_gateway;
}

upstream https_backend {
    server 127.0.0.1:8443;
    keepalive 16;
}

upstream device_gateway {
    server 127.0.0.1:5000;
}

server {
    listen 443;
    ssl_preread on;
    proxy_pass $backend;

    # Timeout settings for long-lived device connections
    proxy_connect_timeout 10s;
    proxy_timeout 1h;
}
STREAM_EOF

echo ">>> Created stream-proxy.conf"

# 3. Include the stream block in nginx.conf if not already present
if ! grep -q 'stream-proxy.conf' /etc/nginx/nginx.conf; then
    # Add the stream include before the http block
    sed -i '/^http {/i \
stream {\
    include /etc/nginx/stream-proxy.conf;\
}\
' /etc/nginx/nginx.conf
    echo ">>> Added stream block to nginx.conf"
else
    echo ">>> Stream block already present in nginx.conf"
fi

# 4. Test nginx config
echo ">>> Testing nginx config..."
nginx -t 2>&1

if [ $? -eq 0 ]; then
    echo ">>> Nginx config test passed, reloading..."
    systemctl reload nginx
    echo ">>> Nginx reloaded"
    
    # Verify ports
    sleep 1
    echo ">>> Port status:"
    ss -tlnp | grep -E ':443|:8443|:5000|:80'
else
    echo ">>> ERROR: Nginx config test failed! Restoring backup..."
    TS=$(date +%Y%m%d%H%M%S)
    cp /etc/nginx/nginx.conf.bak.* /etc/nginx/nginx.conf 2>/dev/null || true
    cp /etc/nginx/sites-available/yantrago.bak.* /etc/nginx/sites-available/yantrago 2>/dev/null || true
    rm -f /etc/nginx/stream-proxy.conf
    nginx -t 2>&1
    systemctl reload nginx
    echo ">>> Restored from backup"
    exit 1
fi
