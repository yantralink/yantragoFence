# YantraGO — Play Store Launch Execution Plan

**Created:** 21 October 2026
**Status:** Owner-approved task list. Execute in order, one phase at a time per `AGENTS.md` rule 20.

---

## 0. Owner Decisions on Record

### 0.1 Shared customer password — RISK ACCEPTED (21 Oct 2026)

**Decision:** The shared default customer password (`yantrago`) will remain. No OTP, no activation link, no forced password change on first login. Admin-provisioned accounts only.

**Risk acknowledged by owner:**
- One password leak compromises all customer accounts.
- Phone number (semi-public) + known shared password = account takeover with no effort.
- Command audit attribution is weakened (shared credential = no cryptographic proof of identity).

**Why this does NOT block Play Store:** Google Play does not audit authentication mechanisms. This is a security liability the owner has accepted, not a Play policy violation.

**What still must be done under this decision:** Remove the credential publication from public GitHub release notes (Task 1.2) — that is a separate credential-leak issue, not a password-policy question.

### 0.2 Account deletion — login-only app (21 Oct 2026)

**Decision:** No in-app "Delete account" button. Users are admin-provisioned; no in-app or app-directed signup exists.

**Why this is acceptable:** Play's in-app deletion requirement is conditional and applies only when users can create accounts in-app or are directed to external signup. A login-only app with admin-provisioned accounts does not trigger it.

**What still must be done:**
- Publish a privacy policy with a retention period + a deletion-request contact (email/web form).
- Implement a real backend deletion workflow (not just deactivation) to honor erasure requests.
- If the QR-based web onboarding flow is launched later, add a deletion-request path on that same web channel.

**Status (implemented):**
- Privacy policy page ships at `infra/vultr/public-site/privacy-policy/index.html`, served by Nginx at `https://yantrago.com/privacy-policy` (apex server block added to `infra/vultr/nginx/nginx.conf`; `admin.yantrago.com/privacy-policy` aliases the same file). Deploy: copy `infra/vultr/public-site/` → `/var/www/yantrago-public/` and issue the apex cert (`certbot --nginx -d yantrago.com -d www.yantrago.com`).
- Deletion requests: authenticated `POST /api/v1/account/deletion-request` records the request in `audit_logs` and emails `PRIVACY_NOTIFY_EMAIL` (the app's Profile → "Request account deletion" calls it); unauthenticated users email `privacy@yantrago.com` per the policy.
- Erasure: `UserErasureService` tombstones the user row (name/email/phone/credentials stripped, locked, inactive), purges sessions, refresh tokens, push tokens, notification/delivery/inbox/preference rows, login-session metadata and role grants; `CustomerService.deleteCustomer` removes the customer record and unassigns machines. Only pseudonymous audit/command records are retained — matching the retention policy published in the privacy policy.

---

## 1. Phase A — Credential Leak Fix (SEC-01, partial)

> The shared-password mechanism is accepted. Only the public credential publication remains.

### Task 1.1 — Remove credentials from GitHub release notes
- **File:** `.github/workflows/build-apk.yml` lines 104-106
- **Change:** Delete the `### Login` section from the release `body:` block. Keep the Download and Server sections.
- **Why:** Currently publishes `superadmin@yantrago.com / password` and `customer phone / yantrago` to the public internet.
- **Impact:** None on app functionality. Only affects GitHub release text.
- **Verify:** Next CI run produces a release with no credentials in the body.

### Task 1.2 — Rotate exposed credentials (owner action, not code)
- **Action:** Owner must change the super admin password and any customer passwords that may have been exposed via public GitHub releases.
- **Why:** The credentials have been public; changing the code does not un-leak them.
- **Impact:** Existing sessions invalidated; users re-login with new credentials.
- **Note:** This is an operational task, not a code change. I cannot perform it.

---

## 2. Phase B — Central Resource Authorization (SEC-02, SEC-07)

### Task 2.1 — Create a reusable machine-access policy
- **What:** A shared authorization check covering: authenticated identity + explicit platform role + organization + active customer linkage + current machine assignment.
- **Files:** New policy class in `backend/src/main/java/com/yantrago/api/security/` or `service/`.
- **Rule:** Missing/inactive customer record denies access (fail-closed).

### Task 2.2 — Apply the policy to all machine-derived endpoints
- **Endpoints to audit:** machines, commands, locations, telemetry, analytics, alerts, histories, settings, geofences, reports/exports.
- **Files:** `MachineService.java`, `CommandService.java`, `LocationService.java`, `TelemetryService.java`, `AlertService.java`, `ReportExportService.java`, and their controllers.
- **Rule:** Customer-supplied filters cannot widen scope. Server resolves ownership.

### Task 2.3 — Fix fail-open null-tenant context
- **Files:** `TenantGuard.java:32-57`, `OwnerContextService.java:34-39`, `TenantContextFilter.java:37-53`.
- **Change:** Require explicit authenticated platform authority for bypass. Distinguish absent context from approved platform context. Only SUPER_ADMIN may lack an organization.

### Task 2.4 — Tests
- **What:** Real filter-chain endpoint tests + service tests with two organizations, two customers per org, malformed/missing claims, unassigned and reassigned machines.
- **Verify:** Unauthorized commands produce zero queue messages and zero command records. Unauthorized reads return consistent 403/404.

---

## 3. Phase D — Token Lifecycle & Revocation (SEC-04, SEC-05)

### Task 3.1 — Atomic refresh rotation
- **File:** `AuthService.java:149-202`
- **Change:** Use PostgreSQL row locking or compare-and-set for refresh token consumption. Commit replay-triggered revocation independently of the rejected refresh (do not let rollback erase the revocation).

### Task 3.2 — Session version / revocation mechanism
- **What:** Bounded authoritative session/version check for REST and active WebSockets.
- **Change:** Validate current user active/locked state, organization activity, and membership before refresh. Revoke on password reset/change, deletion/deactivation, role or tenant changes.

### Task 3.3 — Logout semantics
- **What:** Separate "logout this session" from "logout all devices". Invalidate push associations appropriately.

### Task 3.4 — Token validation hardening
- **What:** Validate token purpose, issuer/audience, distinct access/refresh key configuration.

### Task 3.5 — Tests
- **What:** Real PostgreSQL integration tests (not Mockito-only) for: two concurrent refreshes, committed replay revocation, rollback failures, disabled/moved users, stale roles, logout.

---

## 4. Phase C — WebSocket Authorization (SEC-03, SEC-09)

### Task 4.1 — Destination allowlists
- **File:** `StompChannelInterceptor.java:56-88`
- **Change:** Reject raw queues, wildcards, malformed IDs, unauthorized cross-tenant/customer destinations. Bind user queues to authenticated principal. Apply Phase B's resource policy to exact STOMP destinations.

### Task 4.2 — Replace URL bearer tokens
- **Files:** `WebSocketAuthInterceptor.java:49-81`, `mobile/lib/features/commands/providers/command_socket_provider.dart:79-87`.
- **Change:** Use Authorization headers for native clients. For browser compatibility, use a short-lived, one-use session-bound ticket or secured STOMP CONNECT flow. No access JWT in URL.

### Task 4.3 — Server-side expiry/revocation on active connections
- **Change:** Token expiry or Phase D revocation must close existing subscriptions, not just reject new frames.

### Task 4.4 — Update mobile + admin socket clients
- **Files:** All socket providers in `mobile/lib/features/` and `admin-web/`.
- **Change:** Switch from URL-token to header/ticket auth. Redact edge logging of query strings.

### Task 4.5 — Tests
- **What:** Integration tests with real clients: denied subscriptions receive no broadcasts, ticket replay, expired credentials, account changes, reconnection.

---

## 5. Phase E — Rate Limiter & Trusted Proxy (SEC-06)

### Task 5.1 — Trusted proxy identity
- **Files:** `RateLimitFilter.java:42-87`, `SecurityConfig.java:66-68`, `infra/vultr/nginx/nginx.conf:60-67`.
- **Change:** Resolve clients only through explicitly trusted proxies. Overwrite (not append) untrusted forwarding headers at the edge.

### Task 5.2 — Split rate limiting
- **Change:** Separate unauthenticated/IP/login-account limiting from post-authentication user/tenant/command limits. Use bounded expiring state and shared counters for multiple instances.

### Task 5.3 — Tests
- **What:** Header spoofing, loopback via proxy, distributed attempts, bounded bucket growth, consistent 429/Retry-After, no account-lockout abuse.

---

## 6. Phase F — Mobile Logging & Logout (SEC-08, SEC-16)

### Task 6.1 — Remove sensitive Dio logging
- **File:** `mobile/lib/core/network/api_client.dart:23-33`
- **Change:** Remove response-body logging. Debug instrumentation must be explicitly enabled and redacted. Never log tokens, passwords, coordinates, customer data, push tokens.
- **Impact:** None on functionality. Slightly faster.

### Task 6.2 — Guarantee logout cleanup
- **File:** `mobile/lib/features/auth/providers/auth_provider.dart:130-145`
- **Change:** Capture identity before state changes. Guarantee local credential/cache/socket/pending-navigation cleanup in a top-level `finally` regardless of push/network errors.
- **Define:** Current-installation vs all-device push logout. Prevent stale-account notifications after switching.

### Task 6.3 — Tests
- **What:** Offline logout, Firebase/storage errors, app restart, two accounts on one phone, multi-device login. Sentinel-value log inspection on release build.

---

## 7. Phase G — TCP Gateway Hardening (SEC-10, SEC-11)

### Task 7.1 — Device admission & session ownership
- **Files:** `FencingProtocolHandler.java:154-167`, `FencingTcpServer.java:152-160`, `DeviceConnectionRegistry.java:25-38`, `DeviceAuthService.java:36-47`.
- **Change:** Add active-device admission and current-session ownership outside reused parser logic. Make replacement/removal conditional on same connection identity. Call `DeviceAuthService` on login.

### Task 7.2 — Bound connection resources
- **Files:** `FencingTcpServer.java:39-40,81-90,109-123`, Concox/JT808 server files.
- **Change:** Bound connections/workers and admission rates. Enforce pre-authentication deadlines and max idle lifetime. Per-source quotas compatible with carrier NAT.
- **Rule:** Do not rewrite Concox/JT808 parsing logic (AGENTS.md rule 16). Only change admission/wrapper logic.

### Task 7.3 — Tests
- **What:** Unknown/inactive/duplicate identities, stale disconnects, ACK attribution. Use isolated simulator, never real energized equipment.

---

## 8. Phase H — Web Dependencies & CI Gates (SEC-12)

### Task 8.1 — Upgrade vulnerable admin-web dependencies
- **File:** `admin-web/package.json:12-24`
- **Change:** Upgrade Next.js from 14.2.5 to a supported patched line (check current advisories; don't assume 14.2.35). Resolve nested postcss.
- **Rule:** Prefer versions 7+ days old (AGENTS.md rule 23).

### Task 8.2 — Harden CI security gates
- **File:** `.github/workflows/security-scan.yml:9-15,27-35,61-67`
- **Change:** Add root Gradle/mobile/lockfile-only PR triggers. Pin `trivy-action` to a reviewed immutable revision (not `@master`). Explicitly fail on vulnerability severity. Least-privilege permissions.

### Task 8.3 — Make analyzer blocking in release workflow
- **File:** `.github/workflows/build-apk.yml:53-55`
- **Change:** Remove `|| true` so `flutter analyze` failures block release builds.

### Task 8.4 — Produce resolved SBOMs
- **What:** Java, Node, Dart/native, and image SBOMs. Scan built artifacts and images. Gate releases on analyzed/tested/scanned commits.

---

## 9. Phase I — CORS, Audit, Deployment, CSV (SEC-13, SEC-14, SEC-17, SEC-18)

### Task 9.1 — Lock down CORS
- **File:** `SecurityConfig.java:56-64,121-132`
- **Change:** Allowlist production CORS origins (not all patterns). Review CSP/security headers. Permit only intentional public routes. Restrict Swagger/OpenAPI by environment.

### Task 9.2 — Persist security audit records
- **File:** `AuditLogInterceptor.java:14-18,52-58`
- **Change:** Persist security events (actor, tenant/platform scope, target, outcome, trusted client metadata, correlation ID) — no secrets or whole request bodies. Capture deny/failure outcomes. Deliberate transaction strategy so rejected actions don't erase audit evidence. Define retention.

### Task 9.3 — Tighten deployment trust boundaries
- **Files:** `infra/docker/docker-compose.staging.yml`, `infra/vultr/firewall/*.ufw`.
- **Change:** Remove unnecessary host port publication or bind loopback/private explicitly. Fail setup if admin/source addresses missing. Least-privilege DB/broker users. TLS with peer validation for cross-host paths.
- **Owner action:** Verify real firewall/cloud rules, active Nginx config, log redaction, encrypted backups, restore drills, service-account rotation.

### Task 9.4 — CSV formula neutralization
- **File:** `ReportExportService.java:107-131`
- **Change:** Apply a tested spreadsheet-safe string-cell encoder for formula-leading/control-character cases. Preserve numeric values, valid CSV structure, UTF-8.

---

## 10. Phase J — Map & Geocoding Privacy (SEC-15)

### Task 10.1 — Bundle the map library
- **File:** `mobile/lib/features/machines/pages/machine_location_map_page.dart:84-86,151-168`
- **Change:** Bundle the reviewed map library locally instead of loading from unpkg. Constrain navigation/resources. Serialize labels safely. Validate coordinates. Preserve attribution.

### Task 10.2 — Approved geocoding provider
- **File:** `mobile/lib/core/services/reverse_geocoding_service.dart:87-129`
- **Change:** Choose an approved production map/geocoding provider with data-processing terms. Use the backend for bounded caching/rate control if appropriate. Add a consent dialog before transmitting farmer-linked coordinates to a third party.

### Task 10.3 — Disclose third-party transfers
- **Where:** Privacy policy + in-app notice.
- **Change:** Inform users of third-party map/geocoding transfers. Obtain prior affirmative consent when the unexpected-sensitive-data rule applies.

---

## 11. Privacy Policy & Legal (required for Play)

### Task 11.1 — Write and publish the privacy policy
- **What:** Public, active, non-geofenced, non-PDF page at e.g. `https://yantrago.com/privacy-policy`.
- **Content:** Identify YantraGO/developer/contact. Purposes, data collected, recipients, security, retention period, deletion-request contact.
- **Rule:** A generic template is insufficient. Cover actual launch jurisdictions (India — DPDP Act) and contracts.

### Task 11.2 — Add privacy policy link in-app
- **Files:** `mobile/lib/features/auth/pages/login_page.dart` (before login), `mobile/lib/features/profile/pages/profile_page.dart` (from profile).
- **Change:** Add a tappable "Privacy Policy" link opening the URL via `url_launcher`.

### Task 11.3 — Implement backend deletion workflow
- **File:** `CustomerService.java:181-198` (currently only deactivates).
- **Change:** Real deletion workflow: anonymize/remove PII, keep only audit records. Expose a deletion-request endpoint or email process.
- **Why:** When a user requests erasure (via the privacy policy contact), you must be able to actually delete, not just deactivate.

### Task 11.4 — Add "Contact support / About" section in Profile
- **File:** `mobile/lib/features/profile/pages/profile_page.dart`
- **Change:** Add support email, privacy policy link, deletion-request contact, app version (already exists).

---

## 12. Release Engineering (required for Play)

**Status (implemented):**
- Release signing config added (`mobile/android/app/build.gradle.kts` reads `key.properties`; `key.properties.template` provided; falls back to debug key only when the file is absent so dev builds still work). `.gitignore` blocks `**/key.properties`, `*.jks`, `*.keystore`, `*.pem`, `*.p12`.
- New `.github/workflows/build-aab.yml` builds a signed AAB (keystore + passwords injected from `ANDROID_UPLOAD_KEYSTORE_BASE64`/`ANDROID_KEYSTORE_PASSWORD`/`ANDROID_KEY_ALIAS`/`ANDROID_KEY_PASSWORD` secrets), runs analyze + tests, prints the SHA-256 upload-key fingerprint for comparison with Play Console, scrubs signing material, and attaches the AAB to a GitHub release on `v*` tags.
- `android:label` fixed to `YantraGO`; `mailto:` package-visibility query added (needed for the support/privacy email links on Android 11+).
- `flutter_launcher_icons` + `flutter_native_splash` configured in `pubspec.yaml` (run `dart run flutter_launcher_icons` and `dart run flutter_native_splash:create` to generate); launch background switched to brand color + centered icon so the white flash is already gone.
- **Owner actions remaining:** 12.1 generate the upload key + store secrets (`keytool` command below), 12.2 enroll in Play App Signing, add the four `ANDROID_*` GitHub secrets, run the two generator commands before the first AAB build.

### Task 12.1 — Generate upload signing key
- **Command:** `keytool -genkey -v -keystore yantrago-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias yantrago-upload`
- **Store:** Keystore + passwords in GitHub secrets or a secrets manager. Never commit to repo.

### Task 12.2 — Enroll in Play App Signing
- **Where:** Play Console → App signing → opt in.
- **What:** Upload the upload key (or let Google generate one). Google holds the final app-signing key.

### Task 12.3 — Configure release signing in the Flutter project
- **Files:** Create `mobile/android/key.properties` (gitignored), update `mobile/android/app/build.gradle.kts:36-42`.
- **Change:** Replace the debug signing config with a `release` signing config reading from `key.properties`.
- **Add to `.gitignore`:** `key.properties` and `*.jks`.

### Task 12.4 — Switch CI from APK to AAB
- **File:** `.github/workflows/build-apk.yml` (or new `build-aab.yml`).
- **Change:** Replace `flutter build apk --release --split-per-abi` with `flutter build appbundle --release`. Inject keystore from secrets. Output: `app-release.aab`.

### Task 12.5 — Bump version
- **File:** `mobile/pubspec.yaml`
- **Change:** Set `version: 1.0.0+1` (or your chosen scheme) for the first Play release.

### Task 12.6 — Branded app icon
- **What:** Generate adaptive launcher icon using the YantraGO logo via `flutter_launcher_icons`.
- **Replace:** The default 192×192 colormap PNG in `mipmap-*` directories.

### Task 12.7 — Fix app label
- **File:** `mobile/android/app/src/main/AndroidManifest.xml:7`
- **Change:** `android:label="yantrago"` → `android:label="YantraGO"`.

### Task 12.8 — (Recommended) Native splash screen
- **What:** Add `flutter_native_splash` to eliminate the white flash on cold start.
- **Impact:** Cosmetic only.

---

## 13. Verification (before submitting to Play)

> Implementation status: Tasks 13.1–13.3 are **enforced in CI** inside
> `.github/workflows/build-aab.yml` — every release AAB is checked before
> it becomes an artifact, so a non-compliant build cannot be submitted
> accidentally. Task 13.5 is implemented via `database/seeds/V5__seed_play_reviewer.sql`
> + `infra/scripts/seed-play-reviewer.sh`. Task 13.4 and the staging checks
> below remain owner-side.

### Task 13.1 — Verify target API level
- **What:** Check the merged manifest of the final AAB. Play requires Android 16 / API 36+ for new apps since 31 Aug 2026.
- **Status:** CI-failed check — `build-aab.yml` runs `apkanalyzer manifest target-sdk` on the produced AAB and fails the build when it reports `< 36`.

### Task 13.2 — Verify 16 KB page support
- **What:** Validate every packaged native library (Flutter + plugins) supports 16 KB pages on 64-bit. Required for Android 15+ targets from 1 Nov 2025.
- **Status:** CI-failed check — `build-aab.yml` extracts every `lib/arm64-v8a`/`lib/x86_64` `.so` from the AAB and verifies each `LOAD` segment's ELF alignment is `>= 0x4000` via `readelf`. A misaligned library fails the build.

### Task 13.3 — Inspect merged manifest permissions
- **What:** Check the merged manifest for SDK-added permissions. Keep minimal. Do NOT add background location just to display a remote machine's GPS.
- **Status:** CI-failed check — `build-aab.yml` dumps `apkanalyzer manifest permissions` and fails if any dangerous permission from the denylist (location, camera, mic, contacts, SMS, phone, storage, bluetooth, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, etc.) appears. The full permission list is printed for manual review too.

### Task 13.4 — Device testing
- **What:** Install the AAB-derived APK on at least one arm64 device + one emulator. Walk through:
  - Cold start → splash → login → machine list → machine detail
  - Command dispatch → ACK received
  - Alerts → notifications (grant + deny) → analytics
  - Profile → language switch (EN/HI/MR) → logout
  - Logout with network off
  - Map consent gate → grant → map/tiles/geocoded address → logout → re-asked
  - Profile → privacy policy link → support email → deletion request
- **Verify:** No sensitive data in `adb logcat`, no white flash, branded icon, correct app name, offline error states user-friendly, notification denial doesn't crash.
- **Status:** Owner-side — run against the reviewer-account data after seeding (13.5).

### Task 13.5 — Prepare reviewer test account
- **What:** Stable, least-privilege test account with synthetic machines/history/notifications. No real fencing equipment, no real customer data, no super-admin credentials.
- **Where:** Provide in Play Console test credentials section.
- **Status:** Implemented. Apply once on the production/staging database:

  ```bash
  REVIEWER_PASSWORD='<choose a strong password>' \
    ./infra/scripts/seed-play-reviewer.sh
  ```

  This creates a dedicated `YantraGO Play Review` organization containing
  `play.review@yantrago.com` (customer role only — machine/telemetry/alert
  read + fencing commands, no admin access), a linked customer, two
  synthetic machines (`Review Fence Unit A` online/energized, `Unit B`
  offline), devices, and a short GPS trail — enough for a reviewer to
  exercise list → detail → map → command → profile → privacy links.
  The password is BCrypt-hashed at apply time and never stored in the repo.
  Provide `play.review@yantrago.com` + the chosen password in Play Console →
  App access, plus a note: "Login is the only account flow — accounts are
  provisioned by the operator (no self-signup by design). Fence on/off
  commands are dispatched and audited but remain in PENDING state because
  the review account uses synthetic devices with no physical hardware to
  acknowledge them — this is expected behavior."

  Do NOT use `infra/scripts/seed-db.sh` — it is deprecated (old schema)
  and now exits with an error pointing at the current seed files.

---

## 14. Play Console Submission

### Task 14.1 — Create Google Play Developer account
- **Type:** Organization account (recommended) — requires D-U-N-S number, contact email, website, $25 one-time fee.
- **Or:** Personal account (if created after 13 Nov 2023, requires 12 testers for 14 days before production access).

### Task 14.2 — Create the app in Play Console
- **Name:** YantraGO. **Language:** English (US). **Type:** App. **Pricing:** Free.

### Task 14.3 — Complete Store listing
- App name, short + full description, icon (512×512), feature graphic (1024×500), phone screenshots (2-8, ≥1080×1920).
- Add Hindi and Marathi localized listings.
- Category (Productivity/Tools), contact details, privacy policy URL.

### Task 14.4 — Complete content declarations
- Content rating (IARC questionnaire — likely "Everyone").
- Target audience (18+ — do not select under 18).
- Ads: No.
- App access: provide test credentials (Task 13.5).

### Task 14.5 — Complete Data Safety form
- Declare: login data, user IDs, FCM tokens, Firebase installation ID, coordinates, IMEI/SIM, IP/logs, app activity, personal content.
- Answer "Data deletion": Yes — provide deletion-request contact from privacy policy.
- Do NOT answer "no data collected".

### Task 14.6 — Set up testing tracks
- **Internal testing:** Add up to 100 internal testers. Upload AAB here first.
- **Closed testing:** At least one closed track (e.g. "Beta"). If personal account: 12 testers for 14 days.
- Each track upload is reviewed by Google (1-3 days for first submission).

### Task 14.7 — Upload AAB to Production (or testing track)
- Upload `app-release.aab`. Confirm signing fingerprint matches Play App Signing.
- Enter release name + release notes (no credentials).
- Select countries. Review release → Start rollout.

### Task 14.8 — Pass Google review
- First-time org account reviews: 1-3 days, sometimes longer.
- Pre-empt common rejections: privacy policy accessible before login, Data Safety matches SDK behavior, deletion-request contact exists, target API 36+, 16 KB support, test credentials provided, permissions justified.
- If rejected: fix issue, bump `versionCode`, rebuild AAB, re-upload.

### Task 14.9 — Post-release monitoring
- Watch Android vitals (crash rate, ANR rate).
- Monitor backend logs for security events (after Phase I audit persistence).
- Respond to user reviews. Update listing translations.
- Plan versioned updates: bump `pubspec.yaml`, rebuild AAB, upload new release.

---

## Execution Summary

| Phase | Tasks | App functionality impact | Blocks Play? |
|---|---|---|---|
| A — Credential leak fix | 1.1, 1.2 | None | Yes (B3) |
| B — Resource authorization | 2.1-2.4 | None for legitimate users | Yes (B10) |
| D — Token lifecycle | 3.1-3.5 | Minimal (reliable revocation) | Yes (B10) |
| C — WebSocket auth | 4.1-4.5 | None (transparent plumbing) | Yes (B10) |
| E — Rate limiter | 5.1-5.3 | None for legitimate users | Yes (B10) |
| F — Mobile logging/logout | 6.1-6.3 | None (more reliable logout) | Yes (B6, B7) |
| G — TCP gateway | 7.1-7.3 | None on app | Yes (B10) |
| H — Web deps & CI | 8.1-8.4 | admin-web only | Yes (B10) |
| I — CORS/audit/deploy/CSV | 9.1-9.4 | None on app | Yes (B10) |
| J — Map & geocoding | 10.1-10.3 | Map consent dialog (intentional) | Yes (B8) |
| Privacy & legal | 11.1-11.4 | Privacy link + support section added | Yes (B4, B5) |
| Release engineering | 12.1-12.8 | Branded icon, app name, splash | Yes (B1, B2, B9) |
| Verification | 13.1-13.5 | None | Yes (V1-V7) |
| Play Console | 14.1-14.9 | None | Final step |

**Total: 14 phases, ~50 tasks.**

Execute one phase at a time. After each phase, STOP and ask the user to verify before proceeding (AGENTS.md rule 20).
