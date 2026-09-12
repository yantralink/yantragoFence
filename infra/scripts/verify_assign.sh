#!/bin/bash
sudo -u postgres psql -d yantrago -c "SELECT machine_id, name, organization_id, serial_number FROM machines WHERE organization_id = '87499bf8-3086-4b7a-a336-fcdae4893781';"
