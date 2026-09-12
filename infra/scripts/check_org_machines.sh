#!/bin/bash
echo "=== Organizations ==="
sudo -u postgres psql -d yantrago -c "
SELECT id, name, slug FROM organizations WHERE name ILIKE '%accenture%' OR id = '61d391a7-9f47-42f3-9097-a7a95b627bdf';
"

echo ""
echo "=== All machines in customer's org (61d391a7-...) ==="
sudo -u postgres psql -d yantrago -c "
SELECT m.id, m.machine_id, m.name, m.organization_id, m.customer_id, m.status
FROM machines m
WHERE m.organization_id = '61d391a7-9f47-42f3-9097-a7a95b627bdf';
"

echo ""
echo "=== All machines assigned to any customer in this org ==="
sudo -u postgres psql -d yantrago -c "
SELECT m.id, m.machine_id, m.name, m.customer_id, m.status, c.name AS customer_name, c.phone
FROM machines m
LEFT JOIN customers c ON m.customer_id = c.id
WHERE m.organization_id = '61d391a7-9f47-42f3-9097-a7a95b627bdf'
ORDER BY m.machine_id;
"
