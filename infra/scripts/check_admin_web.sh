#!/bin/bash
echo "=== Check customers page chunks ==="
ls /opt/yantrago/admin-web/.next/static/chunks/app/\(admin\)/customers/ 2>/dev/null

echo ""
echo "=== Search for org selector text ==="
grep -rl "Select organization" /opt/yantrago/admin-web/.next/static/chunks/app/ 2>/dev/null | head -5

echo ""
echo "=== Search for isSuperAdmin ==="
grep -rl "isSuperAdmin\|selectedOrgId\|organizationId" /opt/yantrago/admin-web/.next/static/chunks/app/\(admin\)/customers/ 2>/dev/null | head -5

echo ""
echo "=== Check server-side page ==="
grep -c "organizationId\|isSuperAdmin\|selectedOrgId" /opt/yantrago/admin-web/.next/server/app/\(admin\)/customers/page.js 2>/dev/null
