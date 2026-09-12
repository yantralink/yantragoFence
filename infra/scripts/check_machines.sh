#!/bin/bash
sudo -u postgres psql -d yantrago -c "SELECT id, machine_id, name, organization_id, serial_number, status FROM machines ORDER BY created_at DESC LIMIT 10;"
