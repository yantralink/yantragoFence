#!/usr/bin/env bash
# ============================================================
# YantraGO - Seed the Google Play reviewer account (PLAY 13.5)
# ============================================================
# Applies database/seeds/V5__seed_play_reviewer.sql to the target
# database after injecting a BCrypt hash of the password supplied
# via the REVIEWER_PASSWORD environment variable.
#
# The plaintext password is never written to disk and never
# committed. Enter it in the Play Console "App access" section.
#
# Usage:
#   REVIEWER_PASSWORD='<chosen password>' ./infra/scripts/seed-play-reviewer.sh
#
# Optional env vars (or pass psql options as args):
#   PGHOST, PGPORT, PGDATABASE, PGUSER  (libpq defaults otherwise)
#   e.g. PGDATABASE=yantrago PGUSER=postgres ./infra/scripts/seed-play-reviewer.sh
#
# Requires: psql, and one of htpasswd (apache2-utils), openssl >= 3.0,
#           or python3 for BCrypt generation.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_FILE="$SCRIPT_DIR/../../database/seeds/V5__seed_play_reviewer.sql"

if [ ! -f "$SEED_FILE" ]; then
    echo "ERROR: seed file not found: $SEED_FILE" >&2
    exit 1
fi

: "${REVIEWER_PASSWORD:?Set REVIEWER_PASSWORD env var (e.g. REVIEWER_PASSWORD='...' $0)}"

if [ "${#REVIEWER_PASSWORD}" -lt 12 ]; then
    echo "ERROR: reviewer password must be >= 12 characters." >&2
    exit 1
fi

# --- Generate a BCrypt hash ---
# htpasswd -B emits $2y$ which Spring Security's BCryptPasswordEncoder
# accepts (2a/2b/2y all supported).
gen_bcrypt() {
    if command -v htpasswd >/dev/null 2>&1; then
        htpasswd -bnBC 10 reviewer "$REVIEWER_PASSWORD" | cut -d: -f2-
        return
    fi
    if command -v python3 >/dev/null 2>&1 && python3 -c "import bcrypt" 2>/dev/null; then
        python3 - "$REVIEWER_PASSWORD" <<'PY'
import bcrypt, sys
print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(10)).decode())
PY
        return
    fi
    cat >&2 <<'EOF'
ERROR: no BCrypt tool found. Install one of:
  - htpasswd  (apt install apache2-utils / brew install httpd)
  - python3 bcrypt (pip install bcrypt)
EOF
    exit 1
}

HASH="$(gen_bcrypt)"

case "$HASH" in
    \$2a\$*|\$2b\$*|\$2y\$*) ;;
    *) echo "ERROR: generated hash is not BCrypt: ${HASH:0:7}..." >&2; exit 1 ;;
esac

echo "Applying V5__seed_play_reviewer.sql (play.review@yantrago.com) ..."
sed "s|__REVIEWER_PASSWORD_BCRYPT__|${HASH}|" "$SEED_FILE" | psql -v ON_ERROR_STOP=1 "$@"
echo "Done. Provide the credentials to Google via Play Console → App access."
echo "  email:    play.review@yantrago.com"
echo "  password: <the REVIEWER_PASSWORD you supplied>"
