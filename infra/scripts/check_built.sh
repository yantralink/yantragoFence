#!/bin/bash
echo "=== Search for organization in customers page chunk ==="
grep -o "organization[^,]*" /opt/yantrago/admin-web/.next/static/chunks/app/\(admin\)/customers/page-*.js 2>/dev/null | head -10

echo ""
echo "=== Search for superAdmin or super_admin in customers chunk ==="
grep -o "super[A-Za-z_]*\|isSuper[A-Za-z]*" /opt/yantrago/admin-web/.next/static/chunks/app/\(admin\)/customers/page-*.js 2>/dev/null | head -10

echo ""
echo "=== Full customers page chunk (first 500 chars) ==="
head -c 500 /opt/yantrago/admin-web/.next/static/chunks/app/\(admin\)/customers/page-*.js 2>/dev/null

echo ""
echo ""
echo "=== Search for orgId or org in customers chunk ==="
grep -o "orgId\|orgId\|selectedOrg" /opt/yantrago/admin-web/.next/static/chunks/app/\(admin\)/customers/page-*.js 2>/dev/null | head -10
