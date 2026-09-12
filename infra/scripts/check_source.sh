#!/bin/bash
echo "=== Check customers page source ==="
grep -c "isSuperAdmin\|selectedOrgId\|Select organization" /tmp/yantrago-admin-web-build/admin-web/src/app/\(admin\)/customers/page.tsx 2>/dev/null || echo "File not found or no matches"

echo ""
echo "=== Check if source exists ==="
ls -la /tmp/yantrago-admin-web-build/admin-web/src/app/\(admin\)/customers/ 2>/dev/null || echo "Directory not found"

echo ""
echo "=== Show first 10 lines of customers page ==="
head -10 /tmp/yantrago-admin-web-build/admin-web/src/app/\(admin\)/customers/page.tsx 2>/dev/null || echo "File not found"
