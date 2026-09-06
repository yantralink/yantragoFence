#!/usr/bin/env bash
set -euo pipefail

# Fix gateway systemd unit to allow bean definition overriding
sed -i '/SPRING_CONFIG_ADDITIONAL_LOCATION/a Environment="SPRING_MAIN_ALLOW_BEAN_DEFINITION_OVERRIDING=true"' /etc/systemd/system/yantrago-gateway.service

systemctl daemon-reload
systemctl restart yantrago-gateway

sleep 20
echo "Gateway status: $(systemctl is-active yantrago-gateway)"
curl -s http://localhost:8081/actuator/health 2>&1
echo ""
ss -tlnp | grep -E '5000|5001|5002|8081'
