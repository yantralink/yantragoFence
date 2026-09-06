#!/usr/bin/env bash
set -euo pipefail

# YantraGO — Database Server Setup (PostgreSQL 15 + PostGIS)
# Run as root on a fresh Ubuntu 22.04/24.04 Vultr instance.
#
# Usage: sudo bash server-setup/db-server-setup.sh

echo "=== YantraGO DB Server Setup ==="

# Update system
apt-get update && apt-get upgrade -y

# Install PostgreSQL 15
echo ">>> Installing PostgreSQL 15..."
sh -c 'echo "deb https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -
apt-get update
apt-get install -y postgresql-15 postgresql-15-postgis-3 postgresql-contrib

# Start and enable PostgreSQL
systemctl enable postgresql
systemctl start postgresql

# Create database and user
echo ">>> Creating database and user..."
DB_NAME="${YANTRAGO_DB_NAME:-yantrago}"
DB_USER="${YANTRAGO_DB_USER:-yantrago}"
DB_PASS="${YANTRAGO_DB_PASSWORD:-$(openssl rand -base64 24)}"

sudo -u postgres psql <<SQL
CREATE DATABASE ${DB_NAME};
CREATE USER ${DB_USER} WITH ENCRYPTED PASSWORD '${DB_PASS}';
GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};
SQL

# Enable PostGIS extension
sudo -u postgres psql -d "${DB_NAME}" -c "CREATE EXTENSION IF NOT EXISTS postgis;"
sudo -u postgres psql -d "${DB_NAME}" -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"

# Configure PostgreSQL for remote access (internal network only)
PG_CONF="/etc/postgresql/15/main/postgresql.conf"
PG_HBA="/etc/postgresql/15/main/pg_hba.conf"

# Set listen_addresses
sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" "$PG_CONF"

# Allow connections from internal network (adjust CIDR as needed)
echo "# YantraGO internal network" >> "$PG_HBA"
echo "host    all    all    10.0.0.0/8    md5" >> "$PG_HBA"
echo "host    all    all    172.16.0.0/12    md5" >> "$PG_HBA"

# Optimize for production
cat >> "$PG_CONF" <<CONF

# YantraGO production settings
max_connections = 200
shared_buffers = 512MB
effective_cache_size = 2GB
work_mem = 16MB
maintenance_work_mem = 256MB
random_page_cost = 1.1
effective_io_concurrency = 200
max_wal_size = 2GB
min_wal_size = 256MB
CONF

systemctl restart postgresql

echo ""
echo "=== DB Server Setup Complete ==="
echo "Database: ${DB_NAME}"
echo "User:     ${DB_USER}"
echo "Password: ${DB_PASS}"
echo ""
echo "IMPORTANT: Save these credentials securely. They will not be shown again."
echo "Configure your firewall to allow port 5432 only from internal network."
