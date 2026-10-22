# YantraGO — Google Play Store Upload Guide

**Created:** 21 October 2026
**App package:** `com.yantrago.yantrago`
**Current pubspec version:** `1.0.0+2`
**Verdict:** **NOT READY for Play Store production release.** See Section 1.

This guide has two parts:
1. **Readiness assessment** — what is blocking and what must be implemented first.
2. **Step-by-step upload procedure** — the exact sequence to follow once the blockers are resolved.

---

## 1. Readiness Assessment — NOT READY

The app is functionally complete (auth, machines, commands, alerts, notifications, push, analytics, multilingual EN/HI/MR, maps, profile) but **not yet fit for a public Play Store production release**. The blockers below are confirmed by direct inspection of the source and align with the existing `docs/SECURITY_AND_GOOGLE_PLAY_AUDIT.md` (29 Sep 2026).

### 1.1 Hard blockers — MUST fix before any production submission

| # | Blocker | Evidence | Why it blocks |
|---|---|---|---|
| B1 | **Release build is signed with DEBUG keys** | `mobile/android/app/build.gradle.kts:36-40` — `signingConfig = signingConfigs.getByName("debug")` with a `TODO: Add your own signing config for the release build` | Play Console rejects debug-signed APKs/AABs. A production upload key + Play App Signing enrollment is mandatory. |
| B2 | **CI builds APK, not AAB** | `.github/workflows/build-apk.yml:57-72` runs `flutter build apk --release --split-per-abi` | Play Console requires an **Android App Bundle (`.aab`)** for new apps since August 2021. APKs are only accepted for closed testing/internal sharing. |
| B3 | **Default credentials published in release notes** | `build-apk.yml:104-106` exposes `superadmin@yantrago.com / password` and `customer phone / yantrago` in public GitHub releases | Critical security leak (audit SEC-01). Also violates Play policy on credential exposure. |
| B4 | **No privacy policy in-app** | `grep` of `mobile/lib/` finds no `privacy`/`Privacy` references; `profile_page.dart` has no privacy link | Play Policy PLAY-04 requires an **accessible, non-PDF, non-geofenced** privacy policy reachable before login and from Profile. |
| B5 | **No data-deletion-request path + backend only deactivates** | App is login-only with admin-provisioned accounts (no in-app or app-directed signup); backend `CustomerService.java:181-198` only deactivates, does not delete | Play's **in-app deletion button** requirement is **conditional** and does NOT apply to a purely login-only app with no in-app/app-directed signup. However, you still must: (a) answer the Data Safety "Data deletion" question truthfully, (b) publish a privacy policy with a retention policy + a deletion-request contact (email/web form), and (c) implement a real backend deletion workflow (not just deactivation) to honor erasure requests. **Caveat:** if the QR-based web onboarding flow (see `docs/Fencing Machine QR-Based Customer Onboarding Flow.md`) is launched, add a deletion-request path on that same web channel. |
| B6 | **Mobile Dio logger exposes tokens & personal data** | `mobile/lib/core/network/api_client.dart:23-33` installs an unconditional Dio logger with response bodies; login runs through it (audit SEC-08) | Logs auth tokens, passwords, coordinates, customer data on debug builds / compromised devices. Must be removed or redacted in release builds. |
| B7 | **Logout can leave local credentials** | `features/auth/providers/auth_provider.dart:130-145` awaits push cleanup before clearing storage; `FirebaseMessaging.deleteToken()` can throw and skip cleanup (audit SEC-16) | A failed logout leaves the user's tokens on-device. Must guarantee cleanup in a top-level `finally`. |
| B8 | **Map WebView loads remote JS from unpkg + sends exact coords to public Nominatim** | `machine_location_map_page.dart:84-86,151-168`; `reverse_geocoding_service.dart:87-129` (audit SEC-15) | Loads unreviewed remote executable code; sends farmer-linked precise coordinates to a public service with no throttle/consent. Privacy + policy risk. |
| B9 | **No production signing key / Play App Signing enrolled** | No `key.properties`, no upload keystore in repo | Required for first production upload. |
| B10 | **Backend P0/P1 security findings unresolved** | Audit SEC-01 … SEC-11 (shared customer password, missing customer-level object authorization, cross-tenant WebSocket, no session revocation, refresh-token race, rate-limiter spoofing, TCP admission, etc.) | A public launch exposes these to the internet. Remediate per the audit's phase plan (B, D, C, E, F, G, H, I, J). **Note:** SEC-01's shared-password mechanism is risk-accepted by the owner (see Phase A); the credential-in-release-notes part of SEC-01 still must be fixed. |

### 1.2 Verification gates — must be confirmed on the built artifact

| # | Gate | Status | Action |
|---|---|---|---|
| V1 | **Target API level** | Delegated to Flutter (`build.gradle.kts:11,27`) | Play now requires **Android 16 / API 36+** for new apps/updates since 31 Aug 2026. Verify the merged manifest of the final bundle. |
| V2 | **16 KB page support + 64-bit** | AGP 9.1.0 + NDK 28.2 promising but unverified | New apps targeting Android 15+ must support 16 KB pages on 64-bit from 1 Nov 2025. Validate every packaged native lib (Flutter + plugins). |
| V3 | **Data Safety form** | Not completed | Must declare: login data, user IDs, push tokens, Firebase installation ID, coordinates, IMEI/SIM, IP/timestamps. |
| V4 | **Permissions** | Manifest declares only INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS | Inspect **merged** manifest for SDK-added permissions; keep minimal. Do NOT add background location just to display a remote machine's GPS. |
| V5 | **App icon & branding** | `mipmap-xxxhdpi/ic_launcher.png` is a 192×192 colormap PNG (likely default Flutter icon); `android:label="yantrago"` (lowercase) | Replace with branded YantraGO adaptive icon; set `android:label="YantraGO"`. |
| V6 | **Crash-free core journeys on real devices** | Not device-tested | Verify login, machine list, command dispatch + ACK, alerts, notifications, offline/error behavior, notification denial, accessibility, translations. |
| V7 | **Reviewer access** | Most features need login + assigned hardware | Provide a stable, least-privilege test account with synthetic machines/history/notifications in Play Console test credentials. |

### 1.3 Soft / recommended (not strict blockers but should fix)

- `pubspec.yaml` version `1.0.0+2` — bump to a clean `1.0.0` with `versionCode 1` (or your chosen scheme) for the first Play release; the CI overrides `--build-number` to the GitHub run number which is fine, but be deliberate.
- `build-apk.yml:53-55` runs `flutter analyze` non-blocking (`|| true`) — make analyzer failures block release builds.
- Add a proper splash screen using `flutter_native_splash` (current `SplashPage` is a Dart widget, not a native splash — there will be a white flash on cold start).
- Add accessibility labels and minimum touch targets (Play quality bar).
- Add an in-app "About / Version / Contact support" section in Profile (currently only version label exists).
- Localize the Play Store listing text (EN/HI/MR) to match in-app languages.

---

## 2. What to implement before going live (ordered)

Follow the audit's phase order. Each phase is self-contained and independently testable per `AGENTS.md` rule 20.

1. **Phase A — Secure customer activation (SEC-01).** Two parts:
   - **(a) Shared customer password — RISK ACCEPTED BY OWNER (21 Oct 2026).** The product owner has explicitly decided to keep the current shared default customer password (`yantrago`) with no OTP, no activation link, and no forced password change on first login. This is the SEC-01 P0/Critical finding. The risk (one password leak compromises all customer accounts; phone number + known shared password = account takeover; command audit attribution weakened) has been explained to and accepted by the owner. No code change for this part. **Note:** this does NOT block Play Store submission — Google does not audit authentication mechanisms — but it remains a real security liability the owner has chosen to accept.
   - **(b) Credentials in public release notes — STILL MUST FIX.** `.github/workflows/build-apk.yml:104-106` publishes `superadmin@yantrago.com / password` and `customer phone / yantrago` in public GitHub release notes. This is a separate, clear credential leak and must be removed regardless of the password mechanism decision.
2. **Phase B — Central resource authorization + fail-closed tenant context (SEC-02, SEC-07).** Reusable machine-access policy across all REST endpoints.
3. **Phase D — Atomic token lifecycle & revocation (SEC-04, SEC-05).** Atomic refresh rotation, session version, revocation on password/status/tenant change.
4. **Phase C — WebSocket authorization & token transport (SEC-03, SEC-09).** Destination allowlists, no bearer token in URL.
5. **Phase E — Rate limiter & trusted proxy (SEC-06).**
6. **Phase F — Mobile logging & logout hardening (SEC-08, SEC-16).** Remove sensitive Dio logging; guarantee logout cleanup in `finally`.
7. **Phase G — TCP admission & resource bounds (SEC-10, SEC-11).**
8. **Phase H — Vulnerable web deps & CI gates (SEC-12).** Upgrade Next.js; gate releases on analyzer + security scans.
9. **Phase I — CORS, audit persistence, deployment trust boundaries, CSV formula injection (SEC-13, SEC-14, SEC-17, SEC-18).**
10. **Phase J — Map WebView & geocoding privacy (SEC-15).** Bundle map library, constrain navigation, approved geocoding provider, user consent.
11. **Play release engineering (this guide, Section 3):** signing key, AAB build, privacy policy, deletion flow, Data Safety, branding, device testing.

---

## 3. Step-by-step Play Store Upload Procedure

Once the blockers in Section 1.1 are resolved, follow these steps in order.

### Step 0 — Prerequisites (one-time)

- A **Google Play Developer account**. Choose the correct type:
  - **Organization account** (recommended for YantraGO) — requires D-U-N-S number, official contact email, website, and a $25 one-time fee.
  - Personal accounts created after 13 Nov 2023 require **12 testers enrolled for 14 days** in a closed test before production access is granted.
- A verified **contact email** and **physical address** (shown on the store listing).
- A production backend reachable at `https://yantrago.com` with TLS.

### Step 1 — Generate the upload signing key and enroll in Play App Signing

> Never commit the keystore or its passwords to the repo. Store them in GitHub Actions secrets or a secrets manager.

```bash
keytool -genkey -v -keystore yantrago-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias yantrago-upload
```

- Use a strong, unique password. Save the keystore + passwords in 1Password / GitHub secrets (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEY_PASSWORD`, `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`).
- In Play Console → **App signing** → opt into **Play App Signing**. Upload the upload key (or let Google generate one). Google holds the final app-signing key; you only sign bundles with the upload key.

### Step 2 — Configure release signing in the Flutter project

1. Create `mobile/android/key.properties` (gitignored — add to `.gitignore`):
   ```properties
   storePassword=*****
   keyPassword=*****
   keyAlias=yantrago-upload
   storeFile=../yantrago-upload.jks
   ```
2. Update `mobile/android/app/build.gradle.kts`:
   ```kotlin
   val keystoreProperties = Properties().apply {
       val f = rootProject.file("key.properties")
       if (f.exists()) load(FileInputStream(f))
   }

   android {
       // ...
       signingConfigs {
           create("release") {
               keyAlias = keystoreProperties["keyAlias"] as String?
               keyPassword = keystoreProperties["keyPassword"] as String?
               storeFile = file(keystoreProperties["storeFile"] as String?)
               storePassword = keystoreProperties["storePassword"] as String?
           }
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
           }
       }
   }
   ```
3. Replace the `TODO: Add your own signing config for the release build` block (lines 36-42).

### Step 3 — Bump version and finalize branding

- `mobile/pubspec.yaml`: set `version: 1.0.0+1` (or your scheme).
- `mobile/android/app/src/main/AndroidManifest.xml`: set `android:label="YantraGO"`.
- Generate a branded adaptive launcher icon:
  ```bash
  flutter pub add --dev flutter_launcher_icons
  # configure flutter_launcher_icons.yaml with the YantraGO logo
  dart run flutter_launcher_icons
  ```
- (Optional but recommended) Add a native splash with `flutter_native_splash`.

### Step 4 — Build a signed Android App Bundle (AAB)

Local build:
```bash
cd mobile
flutter clean
flutter pub get
flutter build appbundle --release \
  --dart-define=API_BASE_URL=https://yantrago.com \
  --dart-define=WS_BASE_URL=wss://yantrago.com/ws
```
Output: `mobile/build/app/outputs/bundle/release/app-release.aab`.

CI: replace the `flutter build apk` step in `.github/workflows/build-apk.yml` (or add a new `build-aab.yml`) with:
```yaml
- name: Build AAB
  working-directory: mobile
  run: |
    echo "$UPLOAD_KEYSTORE_BASE64" | base64 -d > android/yantrago-upload.jks
    flutter build appbundle --release \
      --dart-define=API_BASE_URL=https://yantrago.com \
      --dart-define=WS_BASE_URL=wss://yantrago.com/ws
  env:
    UPLOAD_KEYSTORE_BASE64: ${{ secrets.UPLOAD_KEYSTORE_BASE64 }}
    UPLOAD_KEY_PASSWORD: ${{ secrets.UPLOAD_KEY_PASSWORD }}
    UPLOAD_STORE_PASSWORD: ${{ secrets.UPLOAD_STORE_PASSWORD }}
    UPLOAD_KEY_ALIAS: ${{ secrets.UPLOAD_KEY_ALIAS }}
```
**Remove the credential-bearing release-notes block** (`build-apk.yml:104-106`) entirely.

### Step 5 — Test the release build on real devices before uploading

- Install the AAB-derived APK via `bundletool` on at least one arm64 device and one emulator.
- Walk through: cold start → splash → login → machine list → machine detail → command dispatch → ACK received → alerts → notifications (grant + deny) → analytics → profile → language switch (EN/HI/MR) → logout → logout-with-network-off.
- Confirm: no sensitive data in `adb logcat`, no white flash on cold start, branded icon, correct app name, offline error states are user-friendly, notification denial does not crash.

### Step 6 — Create the app in Play Console

1. Go to Play Console → **Create app**.
2. App name: **YantraGO**.
3. Default language: English (United States) — add HI/MR translations later.
4. App type: **App** (not Game).
5. Pricing: **Free**.
6. Declarations: confirm the US export laws and Play terms checkboxes.

### Step 7 — Complete the App content / Store listing

1. **Store listing**:
   - App name, short description (≤80 chars), full description (≤4000 chars).
   - App icon: 512×512 PNG.
   - Feature graphic: 1024×500 PNG.
   - Phone screenshots: minimum 2, recommended 4–8 (use the real screenshots you captured in Step 5 — at least 1080×1920, max 8MB each).
   - Add Hindi and Marathi localized listings.
2. **App category**: choose the most accurate (likely *Productivity* or *Tools*). Tag is optional.
3. **Contact details**: website (`https://yantrago.com`), support email, phone (optional).
4. **Privacy Policy URL**: `https://yantrago.com/privacy-policy` — public, non-PDF, non-geofenced. The page source is `infra/vultr/public-site/privacy-policy/index.html`; the apex Nginx block in `infra/vultr/nginx/nginx.conf` serves it (deploy: copy `public-site/` → `/var/www/yantrago-public/`). In-app links exist on the login screen and Profile page.
5. **Content rating**: complete the IARC questionnaire (no mature/violent content → likely "Everyone").
6. **Target audience**: select 18+ (do not select under 18 — fencing equipment control).
7. **Ads**: declare "No, my app does not contain ads".
8. **App access**: all features require login. Seed the dedicated reviewer account on the production database once — `REVIEWER_PASSWORD='<strong password>' ./infra/scripts/seed-play-reviewer.sh` (creates `play.review@yantrago.com`, customer role, dedicated synthetic org — see `database/seeds/V5__seed_play_reviewer.sql`). Then provide that email + password in Play Console → App access, with the note: "Login is the only account flow — accounts are provisioned by the operator; there is no self-signup by design."

### Step 8 — Complete the Data Safety form

For each data type, declare **collected** vs **shared**, purpose, required/optional, encryption, retention, deletion.

| Data type | Collected/Shared | Purpose | Required |
|---|---|---|---|
| Personal info — Email, Phone (login) | Collected | Account | Required |
| Personal info — Name, Phone (profile) | Collected | Account/Developer communications | Optional |
| User IDs (org/user/customer IDs) | Collected | Account, App functionality | Required |
| Device or other IDs — FCM token, Firebase installation ID | Collected | Communications (push) | Required |
| Approximate/precise location — **machine** GPS coordinates | Shared (OpenStreetMap tile CDN for map display; configured geocoding provider via the backend proxy only after in-app consent) | App functionality (map) | Required |
| App activity — commands, alert acks, notification preferences | Collected | App functionality, Security | Required |
| Personal content — notification/alert text | Collected | App functionality | Optional |
| Diagnostics — IP, timestamps, HTTP logs | Collected | Security/Analytics | Required |

- **Do not** answer "no data collected" — the app clearly transmits identifiers and coordinates.
- Phase J architecture (SEC-15): the map library (Leaflet 1.9.4) is bundled — no remote JS. Map tiles come from the OpenStreetMap tile CDN; reverse geocoding is proxied through the backend (`GET /api/v1/geocoding/reverse`), which throttles to ~1 req/sec, caches, rounds coordinates to ~11 m, and is configurable via `GEOCODING_PROVIDER_URL`. The app asks for affirmative map consent before any third-party transfer — declare the OSM tile CDN plus whichever geocoding provider the backend is configured with.
- If `GEOCODING_PROVIDER_URL` is changed to a different provider, update this form and the privacy policy recipients list to match.
- Enable the **Data deletion** answer: yes — deletion can be requested (a) in-app via Profile → Support & privacy → "Request account deletion" (calls authenticated `POST /api/v1/account/deletion-request`, recorded in `audit_logs` and notified to `PRIVACY_NOTIFY_EMAIL`), or (b) by emailing `privacy@yantrago.com`. An admin fulfils the request by deleting the customer/user — `UserErasureService` physically removes PII, sessions, tokens and notification data, retaining only pseudonymous audit/command records per the published retention policy. An in-app *self-serve delete* button is not required for a login-only app with admin-provisioned accounts and no in-app/app-directed signup, but this discoverable request path satisfies PLAY-06.

### Step 9 — Set up testing tracks (required before production)

1. **Internal testing** — add up to 100 internal testers (your team). Upload the AAB here first.
2. **Closed testing** — at least one closed track (e.g. "Beta"). If your account is personal and created after 13 Nov 2023, you must enroll **≥12 testers** and keep them opted in for **14 consecutive days** before you can apply for production access.
3. Each track upload is reviewed by Google (typically 1–3 days for first submission).

### Step 10 — Upload the AAB

1. Play Console → **Production** (or your testing track) → **Create release**.
2. Upload `app-release.aab`.
3. Play Console will show the signing fingerprint from Play App Signing — confirm it matches.
4. Enter **Release name** (e.g. `1.0.0`) and **Release notes** (no credentials!).
5. Select the countries where the app will be available.
6. Click **Review release** → fix any errors Play Console flags → **Start rollout**.

### Step 11 — Pass Google's review

- First-time reviews for organization accounts typically take **1–3 days**, sometimes longer.
- Common rejection reasons to pre-empt:
  - Privacy policy not accessible before login (B4).
  - Data Safety mismatch with actual SDK behavior (Step 8).
  - No account deletion (B5).
  - Login-only app with no test credentials provided (V7).
  - Target API too low (V1).
  - 16 KB page support missing (V2).
  - Sensitive permissions not justified (V4).
- If rejected, fix the issue, bump `versionCode`, rebuild the AAB, and re-upload.

### Step 12 — Post-release monitoring

- Watch **Android vitals** in Play Console for crash rate, ANR rate, and unresponsive events.
- Monitor backend logs for security events (after Phase I audit persistence).
- Respond to user reviews; update the listing translations.
- Plan versioned updates: bump `pubspec.yaml` `version: 1.0.1+3`, rebuild AAB, upload as a new production release.

---

## 4. Quick checklist (print-friendly)

```
[ ] B1  Release signing config in build.gradle.kts (not debug)
[ ] B2  CI builds .aab, not .apk
[ ] B3  Credentials removed from release notes
[ ] B4  Privacy policy page live + linked in-app (login + profile)
[ ] B5  Privacy policy discloses retention + deletion-request contact; backend supports real deletion (not just deactivation). In-app delete button NOT required for login-only admin-provisioned app.
[ ] B6  Dio logger removed/redacted in release
[ ] B7  Logout cleanup in top-level finally
[ ] B8  Map WebView bundled + approved geocoding provider + consent
[ ] B9  Upload keystore generated + Play App Signing enrolled
[ ] B10 Backend P0/P1 audit findings remediated (Phases A–I)
[ ] V1  Target API 36+ verified on merged manifest
[ ] V2  16 KB page support verified on packaged native libs
[ ] V3  Data Safety form completed and matches SDK behavior
[ ] V4  Merged manifest permissions reviewed (minimal)
[ ] V5  Branded adaptive icon + android:label="YantraGO"
[ ] V6  Core journeys tested on real devices, crash-free
[ ] V7  Test account + synthetic data provided to Play reviewers
[ ]     Play Console account (Organization, D-U-N-S verified)
[ ]     Internal testing → Closed testing (12 testers / 14 days if personal account)
[ ]     Production rollout
```
