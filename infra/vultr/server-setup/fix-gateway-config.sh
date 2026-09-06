#!/usr/bin/env bash
set -euo pipefail

# Fix gateway to use its own config file and port

# Create a separate config directory for the gateway
mkdir -p /opt/yantrago/config/gateway
cp /opt/yantrago/config/gateway-prod.yml /opt/yantrago/config/gateway/application-prod.yml
chmod 600 /opt/yantrago/config/gateway/application-prod.yml
chown -R yantrago:yantrago /opt/yantrago/config/gateway

# Update the gateway systemd unit to point to the gateway-specific config dir
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
Environment="SPRING_CONFIG_ADDITIONAL_LOCATION=/opt/yantrago/config/gateway/"
StandardOutput=journal
StandardError=journal
SyslogIdentifier=yantrago-gateway
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/opt/yantrago/logs /opt/yantrago/gateway /opt/yantrago/config/gateway
ProtectHome=true
LimitNOFILE=1000000

[Install]
WantedBy=multi-user.target
SVC

systemctl daemon-reload
systemctl restart yantrago-gateway

sleep 20
echo "Gateway status: $(systemctl is-active yantrago-gateway)"
curl -s http://localhost:8081/actuator/health 2>&1
echo ""
ss -tlnp | grep -E '5000|5001|5002|8081'
