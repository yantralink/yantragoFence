#!/bin/bash
echo "=== Find customer with phone 7517691781 ==="
sudo -u postgres psql -d yantrago -c "
SELECT c.id, c.name, c.phone, c.organization_id, c.user_id, c.is_active
FROM customers c
WHERE c.phone LIKE '%7517691781%';
"

echo ""
echo "=== Find user with email 7517691781 ==="
sudo -u postgres psql -d yantrago -c "
SELECT u.id, u.email, u.organization_id, u.is_active,
       string_agg(r.name, ',') AS roles
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
WHERE u.email LIKE '%7517691781%'
GROUP BY u.id, u.email, u.organization_id, u.is_active;
"

echo ""
echo "=== Find machines assigned to this customer ==="
sudo -u postgres psql -d yantrago -c "
SELECT m.id, m.machine_id, m.name, m.organization_id, m.customer_id, m.status
FROM machines m
WHERE m.customer_id IN (
    SELECT c.id FROM customers c WHERE c.phone LIKE '%7517691781%'
);
"

echo ""
echo "=== All machines in Accenture org ==="
sudo -u postgres psql -d yantrago -c "
SELECT m.id, m.machine_id, m.name, m.organization_id, m.customer_id, m.status
FROM machines m
WHERE m.organization_id = '87499bf8-3086-4b7a-a336-fcdae4893781';
"

echo ""
echo "=== Machine assignments for this customer ==="
sudo -u postgres psql -d yantrago -c "
SELECT ma.id, ma.machine_id, ma.customer_id, ma.assigned_at, ma.unassigned_at
FROM machine_assignments ma
WHERE ma.customer_id IN (
    SELECT c.id FROM customers c WHERE c.phone LIKE '%7517691781%'
)
ORDER BY ma.assigned_at DESC;
"
