#!/bin/bash
sudo -u postgres psql -d yantrago -c "
SELECT u.id, u.email, u.full_name, u.organization_id, u.is_active,
       string_agg(r.name, ',') AS roles
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
GROUP BY u.id, u.email, u.full_name, u.organization_id, u.is_active
ORDER BY u.email;
"
