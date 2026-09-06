#!/usr/bin/env bash
# Generate correct BCrypt hash for "password" and update the super admin user
cd /opt/yantrago/repo
HASH=$(java -cp backend/build/libs/backend-1.0.0.jar -Dloader.main=org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder org.springframework.boot.loader.launch.PropertiesLauncher 2>/dev/null || echo "FALLBACK")

# Use Python instead to generate BCrypt hash
pip3 install bcrypt 2>/dev/null || apt-get install -y python3-bcrypt 2>/dev/null
HASH=$(python3 -c "import bcrypt; print(bcrypt.hashpw(b'password', bcrypt.gensalt()).decode())" 2>/dev/null)

if [ -z "$HASH" ] || [ "$HASH" = "FALLBACK" ]; then
    # Use htpasswd as fallback
    apt-get install -y apache2-utils 2>/dev/null
    HASH=$(htpasswd -nbBC 10 "" password | tr -d ':\n' | sed 's/^\$2y/\$2a/')
fi

echo "Generated hash: $HASH"

# Update the user's password in the database
sudo -u postgres psql -d yantrago -c "UPDATE users SET password_hash = '${HASH}' WHERE email = 'superadmin@yantrago.com';" 2>&1

# Verify
echo ""
echo "=== Testing Login ==="
curl -sk -X POST https://yantrago.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yantrago.com","password":"password"}'
