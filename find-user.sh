#!/bin/bash
echo "=== User lookup ==="
sudo -u postgres psql -d yantrago -c "SELECT id, email, phone, first_name, last_name FROM users WHERE phone='9527028875' OR email LIKE '%9527028875%';"

echo ""
echo "=== Customer lookup ==="
sudo -u postgres psql -d yantrago -c "SELECT c.id, c.name, c.user_id, u.email FROM customers c JOIN users u ON c.user_id = u.id WHERE u.phone='9527028875';"
