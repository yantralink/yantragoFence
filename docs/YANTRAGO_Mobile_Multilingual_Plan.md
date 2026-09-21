# YantraGO Mobile — Multi-Language Implementation Plan (English, Hindi, Marathi)

**Prepared / reviewed:** 2026-09-10

**Status:** Planning and code review only; no implementation authorized or completed. Updated 2026-09-18 with always-English text policy and detailed per-phase implementation prompts. Updated 2026-09-21 after codebase re-verification: iOS and admin-web scoped out, SDK facts corrected, Phase 4 reframed as verify-and-harden (backend preference already exists), Phase 6 premise corrected (no backend notification localization exists), Phase 5 clearAll call sites enumerated.

**Scope:** Flutter-owned UI, formatting, account language preference, and localized inbox/push templates when the notification prerequisites are ready. English (`en`), Hindi (`hi`), Marathi (`mr`). **iOS is explicitly out of scope for this effort** (Android only; iOS is deferred to a future effort — do not spend phase effort on iOS device/build verification). **admin-web stays English-only** with no i18n framework; do not extend this plan to it.

**Authority:** Follow `AGENTS.md`, `YANTRAGO_PROJECT_STRUCTURE.md` sections 5 and 8, and the consolidated Notification Plan in `YANTRAGO_IMPLEMENTATION_PLAN.md` sections N1–N13. Keep the existing backend + TCP gateway architecture and shared RabbitMQ contracts.

**Execution rule:** Implement one phase at a time, test/build the affected modules, STOP, and ask the user to verify and explicitly approve the next phase. Reviewing or approving this document alone does not authorize implementation.

---

## 1. Review Findings and Current State

The app has no ARB-based UI localization, but it already uses `intl`. Findings below describe the inspected working tree, not a verified deployed release.

| Area | Verified finding | Required improvement |
|------|------------------|----------------------|
| Flutter setup | `mobile/lib/app.dart` has no localization delegates; `pubspec.yaml` has no `flutter_localizations` or generation setting | Use Flutter SDK `gen-l10n`, not a parallel translation library |
| Existing formatting | `intl: ^0.18.0` is declared, locked to 0.18.1, and used by `core/utils/date_utils.dart` | Replace locale-implicit static date formatters and English relative-time messages |
| SDK compatibility | `mobile-ci.yml` pins Flutter **3.10.0**, which cannot satisfy the lockfile (requires Flutter >=3.44.0 / Dart >=3.12.0) — that CI job is almost certainly already failing; `build-apk.yml` pins **3.47.2**. `pubspec.yaml`'s SDK constraint (`>=3.0.0 <4.0.0`) is looser than what the lockfile was resolved with | Standardize local dev, both CI workflows, and the pubspec constraint on one approved modern Flutter/Dart version before installing localization dependencies; do not guess an `intl` version. Flutter is not installed on this Windows workspace (`flutter` not on PATH), so local verification steps require an SDK install or another machine |
| Inventory | Original estimate was 120+ strings across 32 files, not an exhaustive extraction | Audit all user-visible text, including providers, helpers, API-derived labels, semantics and embedded map content |
| Storage | `SecureStorage.clearAll()` deletes every key and is called from three paths: `auth_service.dart`, `auth_provider.dart`, and the 401 path in `network/interceptors/auth_interceptor.dart` | Separate device language from credentials and account-scoped pending changes; never retain tokens to preserve a language. The interceptor path is the risky one: a transient backend blip surfacing as 401 would also wipe state (see Phase 5) |
| Profile | Backend preference already exists: migration `V39__user_preferred_locale.sql` adds `users.preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en'`; `PUT /api/v1/auth/me/locale` validates en/hi/mr; `GET /me` and login DTO return `preferredLocale`; the mobile `User` model carries it (default `'en'`) | Phase 4 is therefore verify-and-harden, not greenfield. Open conflict: the column is `NOT NULL DEFAULT 'en'` while section 2 policy says nullable = "not chosen"; "unset" is unrepresentable today — needs a decision (see Phase 4) |
| Backend content | Alerts display API `alertType`, `severity`, `message`; command UI displays command type/error | Translate stable codes in presentation; do not attempt to translate arbitrary English server strings |
| Notifications | Existing dispatch code still contains placeholder recipient handling; production inbox/FCM work is separately planned | Localization must extend the verified notification pipeline, not treat scaffolding as production-ready |
| Maps | Reverse geocoding fixes `Accept-Language` to `en` and caches by coordinates only | If localized addresses are approved, include locale in request/cache identity; map tiles and external app UI remain provider-controlled |

### Inventory checklist

The original screen list remains the starting scope: app/router, shared `core/widgets/`, auth, dashboard, machines (including location map), map, alerts, commands, history, profile and settings. Also inspect:

- `core/utils/date_utils.dart`, `core/utils/validators.dart`, `core/network/interceptors/error_interceptor.dart`, auth state/errors, and feature providers/models that produce display text.
- All titles, buttons, dialogs, snackbars, tooltips, accessibility/semantic labels, validation messages, loading/error/empty states, relative times, counts and telemetry units.
- API enum labels, route-not-found UI, HTML/WebView controls, marker popups, and strings composed with interpolation or concatenation. A `Text('...')` search alone is insufficient.
- Record the final key inventory during Phase 1; deduplicate only where meaning is identical. Counts in the earlier draft are sizing estimates, not acceptance criteria.

### Corrections to the original proposal

1. ARB message IDs must generate valid Dart members: use `machineTurnOn`, not `machine.turnOn`.
2. Add typed placeholders and ICU plurals while extracting English, rather than retrofitting them after translation.
3. Advertise only generated, complete locales. Phase 1 supports English; Hindi/Marathi become available after Phase 2 verification.
4. Storage rules mandate secure storage for **tokens**, not every preference. Reuse the existing wrapper for locale to avoid an unnecessary dependency, not because locale is secret.
5. Replace untracked “fire-and-forget with retry” with observable, account-scoped synchronization.
6. Move profile API/sync before localized notifications; perform font/layout checks and tests in each phase, not only at the end.
7. Keep paid channels deferred. Devanagari SMS does not have a universal 160-character limit.

---

## 2. Recommended Product and Technical Policies

These are recommendations for confirmation, not previously approved requirements.

| Topic | Recommended policy |
|-------|--------------------|
| First launch | Resolve the ordered device locale list to the first supported language, ignoring region (`hi-IN` → `hi`, `mr-IN` → `mr`); otherwise English |
| Explicit selection | Show `English`, `हिन्दी`, `मराठी` in Settings and an accessible pre-login picker; no compulsory onboarding screen |
| Device changes | Automatic device resolution applies only while no explicit selection exists. A “Follow device language” setting is deferred unless requested |
| Profile ownership | Language belongs to the authenticated user, not the machine/customer/organization. An existing server preference wins on login unless the same account has a pending local edit |
| Shared phones | Retain only a non-sensitive device/login-screen preference across logout. Never copy the previous account's cached preference or pending write into the next account |
| Missing server preference | Keep database language nullable to mean “not chosen”; render server notifications in English until a supported preference is saved. When first syncing an unset account, save the resolved local language |
| Offline behavior | UI switching and local persistence work offline. Clearly indicate when notification language has not synced; do not block machine viewing on a preference request |
| Formatting | Localized month names and relative-time phrases; recommend Latin digits for telemetry/identifiers in all three languages, pending product approval. Keep measurement units and precision unchanged |
| Historical notifications | Persist rendered language/template version with an inbox item; changing language affects newly created items, not existing history or already displayed OS pushes |
| Release boundary | A UI-only multilingual release can ship after Phase 3 and its applicable Phase 7 checks. Backend notification localization is a separate gated extension |

### Boundaries and safety

- Keep wire values, API keys, event/command enums, database values and protocol bytes unchanged. Translate their display labels only, including a safe localized unknown-state label.
- Preserve pending → ACK → DONE/FAILED semantics; translations must not call an accepted/pending command “successful.” Locale changes must not submit a command again or reset its state.
- Do not auto-translate machine/customer names, serial numbers, IMEIs, phone numbers, addresses or other user-entered content. Preserve identifiers for copying and support.
- Bundle ARB translations with the app. No runtime translation service, remote translation download, new microservice, or language claim in JWT is needed.
- Flutter controls app-owned text. Native permission prompts, app-store metadata, map tile labels, external maps and platform notification settings require separate platform/provider review; do not promise they follow an in-app switch.
- All three languages are LTR. Native speakers must review safety-critical command, voltage, battery and alert terminology before production; AI output is a draft, not approval.

### Always-English text policy (user-approved carve-out)

The following text **stays in English in every locale** and must NOT be wrapped in ARB messages or translated. These are excluded from the translation inventory entirely. Rationale: brand consistency, technical readability, support/debugging, and avoiding confusion when users switch languages.

**Brand and product identity**
- "YantraGO" (brand name, app title, splash, organization name display)
- Product taglines if any are added later — review before translating

**Identifiers and reference codes (never translated, only displayed)**
- Machine IDs (`YG000001`), serial numbers, IMEI, SIM numbers, phone numbers
- Customer/organization names (user-entered content)
- Addresses (reverse-geocoded; separate geocoding locale decision is in Phase 3)
- Alert/incident IDs, command IDs, audit references
- Firmware versions, protocol type strings (`CONCOX_V5`, `JT808`)

**Telemetry units and numeric suffixes**
- `%`, `V`, `/4`, `km/h`, `min`, `h`, `d` and any future unit tokens
- Numeric values themselves stay in Latin digits across all three languages (per section 2 formatting policy)

**Technical status codes and enums (display labels may be translated; the code itself stays English)**
- API/protocol enum values: `ACTIVE`, `IN_STOCK`, `FENCING_ON`, `FAULT`, `OFFLINE`, `PENDING`, `ACK`, `DONE`, `FAILED`, `TIMEOUT`
- Alert types: `LOW_BATTERY`, `DEVICE_OFFLINE`, `MACHINE_MOVING`, `GEOFENCE_BREACH`
- Command types: `FENCING_ON`, `FENCING_OFF`
- HTTP/error codes when shown raw to support staff
- These appear in English in logs, support screens, and any "technical detail" expandable section. The user-facing badge/label for these (e.g. "Online", "Fault", "Turn ON") IS translated; the underlying code shown in a detail/developer view is NOT.

**Developer/support-facing strings**
- Stack traces, exception class names, log output
- Any future developer settings screen content

**What IS translated (for clarity)**
- All button labels (Turn ON/OFF, Save, Cancel, Log out, Login, Retry, Apply, Previous, Next, etc.)
- Screen titles, section headers, field labels
- Status badge display text (Online/Offline/Fault/In stock)
- Telemetry tile labels (Machine Status, Battery, Charging Status, Fence Fault, Device Signal, Voltage, Recharge)
- Error messages shown to end users (network failures, validation, permission denied)
- Empty-state messages, snackbars, dialogs
- Date/time relative phrases ("just now", "5 min ago", "2 h ago")
- Notification titles and bodies (Phase 6)
- Profile/Settings menu labels (Account Details, Notification, Language Settings, Theft Protection Settings, Command History)
- Bottom navigation tab labels (Machines, Alerts, Profile)
- Dashboard section headers (Farm overview, Telemetry, Your machines) and status card labels (Total, Online, Fencing, Fault, Offline)
- Login page: "Email or Phone" label, "Password" label, hint text, "Customers: log in with your phone number. Admins: log in with your email." info text
- Splash page tagline: "Machine Management Platform" (already exists — needs translation)
- Alert type display labels (Low Battery, Voltage Drop, Device Offline, etc.) — translated in the widget layer, NOT in the model
- Command status display labels (Awaiting ACK, Acknowledged, Done, Failed, Timed Out) — translated in the widget layer
- Theft protection card text ("Active", "Not Protected", "Speed alert active", "Tap to view on map", "Enable after installing on your farm…")
- Filter sheet labels ("Filter Notifications", "Unread only", "Alert Type", filter chips, "Apply", "Page X of Y")
- All snackbar messages ("Command sent. Waiting for device acknowledgement…", "Failed to send command. Please try again.", "Settings saved")

**Special cases (neither fully translated nor always-English)**
- **Language names** ("English", "हिंदी", "मराठी") — always shown in their native script regardless of current app language. A Hindi user sees "English / हिंदी / मराठी", not "अंग्रेज़ी / हिंदी / मराठी". These are hardcoded per-language, not wrapped in ARB.
- **Placeholder symbols** ("--" for null dates, "N/A" for missing account fields) — stay as-is in all languages. They are symbols, not text.
- **"Machine state changes only after device confirmation."** — currently in the always-English list. This is a user-visible note on the machine detail screen. **Decision needed:** keep English (technical caveat) or translate (user-facing guidance)? Default: translate, since it's visible to end users, not just developers.

**Implementation rule:** Strings in the "always-English" list are hardcoded as English string literals in Dart (or backend resources) and never passed through `AppLocalizations`. They are tagged with a `// always-en:` comment so future contributors do not accidentally extract them. The catalog completeness check in Phase 2 must not flag these as missing translations.

### Non-widget layer strings (critical architecture decision)

Several user-visible English strings live in non-UI layers that **cannot access `AppLocalizations`** (no `BuildContext`). These must be refactored to return typed codes/enums, with translation done in the widget layer:

| File | Current behavior | Required refactor |
|------|-----------------|-------------------|
| `core/utils/validators.dart` | Returns English strings ("Email is required", "Password must be at least 6 characters", "IMEI must be 15 digits", etc.) | Return typed validation error codes (enum: `ValidationErrorCode.emailRequired`, `.emailInvalid`, `.passwordTooShort`, `.imeiInvalid`, etc.). Widgets call `AppLocalizations` to convert the code to a localized message in the `validator:` callback. |
| `core/network/interceptors/error_interceptor.dart` | Returns English strings ("Connection timed out. Please check your network.", "Cannot connect to server. Please check your internet.", "Unauthorized. Please log in again.", etc.) | Return typed error codes (enum: `NetworkErrorCode.connectionTimeout`, `.connectionError`, `.unauthorized`, `.forbidden`, `.notFound`, `.serverError`, etc.). Store the code on `DioException` (custom property or `error` field). Widgets/providers translate the code to a localized message at presentation time. |
| `models/notification_inbox.dart` (`alertTypeLabel` getter) | Maps alert type codes to English display labels ("Low Battery", "Voltage Drop", "Device Offline", etc.) | Remove the English string mapping from the model. Return the raw `alertType` code. Create a widget-layer helper (`AlertTypeLabels.of(context, alertType)`) that uses `AppLocalizations` to map codes to localized display labels. |
| `models/alert.dart` | Similar severity/type display logic | Same approach — keep raw codes in the model, translate in the widget layer. |
| `features/commands/widgets/command_row.dart`, `command_status_widget.dart`, `notification_card.dart`, `machine_card.dart`, `alert_card.dart` | Each has a duplicate `_relative()` method returning English ("just now", "5 min ago", etc.) — 5 files total (verified; `notification_detail_page.dart` has none) | Consolidate ALL 5 duplicate `_relative()` methods into a single locale-aware helper in `core/utils/date_utils.dart` that accepts a `BuildContext` or `AppLocalizations` and returns a localized relative-time string. |

### WebView / HTML strings (map popups)

The machine location map page (`machine_location_map_page.dart`) builds Leaflet.js HTML with hardcoded English strings inside JavaScript:
- `bindPopup('Machine Location')` — marker popup label
- `bindPopup('Geofence ($geofenceRadiusMeters m)')` — geofence popup label

These cannot use `AppLocalizations` directly (they're inside an HTML string loaded into a WebView). The approach:
1. Pass localized strings as parameters to `_buildLeafletHtml()` from the widget layer (which has `BuildContext`).
2. Interpolate them into the HTML/JavaScript string.
3. The `m` unit in "Geofence (200m)" stays English (always-English unit policy).
4. Map tile labels and attribution are provider-controlled (OpenStreetMap) — not translated.

---

## Phase 1 — Localization Foundation and Complete English Extraction

**Goal:** A buildable English baseline with localization-aware presentation and no behavior changes.

1. Record local Flutter/Dart versions and reconcile them with both CI workflows, declared constraints and the lockfile. Request approval for SDK/project configuration changes. Select an approved stable SDK and its compatible SDK-pinned `intl`; avoid dependency overrides or weakened CI gates to force resolution.
2. Add the SDK `flutter_localizations` dependency and `flutter: generate: true`. Configure `mobile/l10n.yaml` with `arb-dir: lib/l10n`, `template-arb-file: app_en.arb`, `output-localization-file: app_localizations.dart`, and an explicit generated source directory such as `lib/l10n/generated`.
3. Use the chosen SDK's supported generation options and a direct source import such as `package:yantrago/l10n/generated/app_localizations.dart`; do not assume the legacy `package:flutter_gen` synthetic package exists. Generate in CI before analysis/tests; document whether generated output is tracked and never edit it manually.
4. Create English ARB messages with feature-prefixed lowerCamelCase IDs (`navMachines`, `loginIdentifierLabel`, `machineTurnOn`, `commandAwaitingAck`, `errorPageNotFound`). Add translator descriptions, typed placeholders and representative examples; do not construct sentences from translated fragments.
5. Wire `AppLocalizations.localizationsDelegates`, generated `supportedLocales` and a Riverpod locale controller into `MaterialApp.router`. Keep locale infrastructure in `core/`, selection UI in `features/settings/`; preserve the GoRouter instance, navigation stack, form input and live subscriptions on locale changes.
6. Load persisted locale before rendering localized interactive content, with a bounded recovery path for missing/corrupt/unavailable storage. Validate stored values and fall back to supported English during this phase; do not hang startup or require a network request. Design device preference versus account-scoped storage explicitly before changing credential cleanup.
7. Extract the full inventory, including validation and semantics. Pass localization into presentation helpers or return typed failure codes from non-UI layers; do not introduce `BuildContext` into repositories, API clients or domain models. Revalidate visible field errors on language change without clearing entered input.
8. Map known HTTP/domain failures to safe localized messages at presentation time. Preserve structured failure categories instead of caching already translated strings in providers. Never show `exception.toString()`, raw backend `message`, or command diagnostic text; use a localized generic fallback for unknown codes. Any needed backend error-code extension must be additive and separately verified, not English-text matching.
9. Consolidate duplicated relative-time helpers and make date/number formatting explicitly locale-aware. Initialize locale data as required by the selected SDK. Keep timezone handling separate from language: interpret known UTC/offset timestamps correctly and display in the agreed device timezone; do not silently reinterpret timezone-less backend timestamps. Test null/future timestamps and clock boundaries with a controllable clock.

Example English ARB structure (not a final translation inventory):

```json
{
  "@@locale": "en",
  "machineTurnOn": "Turn ON",
  "@machineTurnOn": {"description": "Requests machine power on; does not imply device acknowledgement."},
  "machineAssignedCount": "{count, plural, =0{No machines assigned} =1{1 machine assigned} other{{count} machines assigned}}",
  "@machineAssignedCount": {
    "description": "Number of machines assigned to the current user.",
    "placeholders": {"count": {"type": "int", "example": "3"}}
  },
  "errorPageNotFound": "The page \"{path}\" could not be found.",
  "@errorPageNotFound": {
    "description": "Unknown navigation path; exclude query strings and sensitive values.",
    "placeholders": {"path": {"type": "String", "example": "/app/missing"}}
  }
}
```

**Acceptance:** English generation, analysis, unit/widget tests and Android build pass. App-owned strings, validations and display-code mappings use localization; server-authored historical messages are explicitly outside this extraction. Auth, routing and command behavior remain unchanged. Unit tests cover locale resolution/storage failures, formatting, plural boundaries and safe error fallbacks; widget tests install localization delegates.

**STOP:** Ask the user to verify Phase 1 and explicitly approve Phase 2.

### Detailed implementation prompt — Phase 1

> **Context:** YantraGO is a Flutter mobile app (Riverpod 2.4, GoRouter, Dio) for a fencing-machine IoT platform. The app currently has NO localization — all strings are hardcoded English. We need to add the Flutter l10n foundation and extract all user-visible English strings into ARB files, with zero behavior change. Backend is Spring Boot 3 / Java 17. Repo root: `D:\CascadeProjects\yantrago`, mobile at `mobile/`.
>
> **Prerequisite to resolve first:** There is an SDK mismatch — CI pins Flutter 3.10/3.24.5 but the lockfile requires >=3.44.0. Check `mobile/pubspec.yaml`, `mobile/pubspec.lock`, `.github/workflows/build-apk.yml`, and any other CI workflow. Propose a single stable Flutter/Dart version that satisfies both the lockfile and CI, and update all of them consistently. Do NOT weaken CI gates or use dependency overrides. Ask the user to approve the SDK choice before proceeding.
>
> **Tasks:**
> 1. Add `flutter_localizations` to `pubspec.yaml` dependencies. Set `flutter: generate: true`. Create `mobile/l10n.yaml` with `arb-dir: lib/l10n`, `template-arb-file: app_en.arb`, `output-localization-file: app_localizations.dart`, `output-dir: lib/l10n/generated`.
> 2. Create `mobile/lib/l10n/app_en.arb` with English messages for ALL user-visible strings. Use feature-prefixed lowerCamelCase IDs (e.g. `navMachines`, `loginIdentifierLabel`, `machineTurnOn`, `commandAwaitingAck`, `errorPageNotFound`). Add `@key` metadata with description and typed placeholders. Use ICU plurals where counts are shown.
> 3. **Do NOT extract strings in the "Always-English text policy" section of this plan** (brand name, identifiers, units, enum codes, developer strings). Mark those with a `// always-en:` comment in the Dart source so they are clearly excluded.
> 4. Wire `AppLocalizations.localizationsDelegates` and `supportedLocales` into `MaterialApp.router` in `mobile/lib/app.dart`. Add a Riverpod `localeControllerProvider` in `core/` that holds the current `Locale` (defaults to `en` for this phase).
> 5. Replace every hardcoded user-visible English `Text('...')`, `label: '...'`, `title: '...'`, `message: '...'`, snackbar content, dialog text, validation messages, empty-state messages, and relative-time phrases with `AppLocalizations.of(context)!.key` calls. Search exhaustively — not just `Text(` but also `label:`, `title:`, `message:`, `content:`, `hintText:`, `helperText:`, `validator:` return values, and string interpolation.
> 6. **Refactor non-widget layer strings** (see "Non-widget layer strings" section): `validators.dart` → return typed `ValidationErrorCode` enum instead of English strings; `error_interceptor.dart` → return typed `NetworkErrorCode` enum instead of English strings; `notification_inbox.dart` `alertTypeLabel` → remove English string mapping, return raw code, translate in widget layer. Never pass `BuildContext` or `AppLocalizations` into models, validators, or interceptors.
> 7. **WebView/HTML strings**: pass localized strings as parameters to `_buildLeafletHtml()` in `machine_location_map_page.dart` for marker popup ("Machine Location") and geofence popup label. The `m` unit stays English.
> 8. Consolidate ALL 5 duplicate `_relative()` methods (in `command_row.dart`, `command_status_widget.dart`, `notification_card.dart`, `machine_card.dart`, `alert_card.dart`) into a single locale-aware helper in `core/utils/date_utils.dart` that accepts `AppLocalizations` and returns localized relative-time strings. Delete the 5 duplicates.
> 9. Map HTTP/domain failures to safe localized messages at presentation time using the new `NetworkErrorCode` enum. Never show `exception.toString()` or raw backend `message`. Translate error codes in the widget layer, not in the interceptor.
> 10. Run `flutter gen-l10n`, `flutter analyze`, `flutter test`, and `flutter build apk --debug`. All must pass. Add unit tests for locale resolution, storage failure fallback, date formatting, plural boundaries, validation error code mapping, and network error code mapping.
>
> **Verification:** The app looks and behaves exactly as before (all English), but every translatable string now flows through `AppLocalizations`. The "always-English" strings are still hardcoded with `// always-en:` comments. No new dependencies beyond `flutter_localizations` and the SDK-compatible `intl`.

---

## Phase 2 — Hindi/Marathi Translations and Rendering Quality

**Goal:** Complete reviewed language catalogs that are safe to expose to users.

1. Add `app_hi.arb` and `app_mr.arb` with matching message IDs and placeholder names/types. Translate whole messages and appropriate plural forms; every ICU plural has an `other` branch. Validate apostrophe/brace escaping with `gen-l10n` rather than assuming Flutter and Java template syntax are interchangeable.
2. Maintain a glossary and native-speaker review record in this plan or the existing translation workflow. Suggested drafts: `machineTurnOn` → `मशीन चालू करें` (Hindi), `मशीन चालू करा` (Marathi); use `भाषा` for the language section and native language names for choices. Review command acknowledgement/completion wording separately.
3. Verify Devanagari conjuncts, vowel marks, line height, bold weights and mixed Latin/Devanagari text on **Android** (iOS is deferred — out of scope for this effort), light/dark themes, narrow screens and large accessibility text. Use flexible layouts/wrapping instead of shrinking text or disabling text scaling. Critical action labels and warnings must remain fully readable; list previews may truncate only when full content is accessible.
4. Test existing font fallback offline. If insufficient, bundle a licensed Devanagari font with required weights and configure fallback in the existing theme; include its license and check app size. Do not download fonts at runtime.
5. Add a catalog check for missing/extra/duplicate keys, invalid message identifiers, placeholder mismatches and untranslated messages. `gen-l10n` fallback alone is not a completeness gate; new languages must not silently ship English gaps. Add only complete catalogs to advertised/generated supported locales.

**Acceptance:** All three locales generate and pass catalog, formatting and representative screen/semantics tests; no missing glyphs or inaccessible clipped controls. Hindi and Marathi safety-critical wording has recorded native-speaker approval before production exposure.

**STOP:** Ask the user to verify Phase 2 and explicitly approve Phase 3.

### Detailed implementation prompt — Phase 2

> **Context:** Phase 1 is complete. The app has a working English ARB file (`mobile/lib/l10n/app_en.arb`) and `AppLocalizations` is wired into `MaterialApp.router`. All user-visible strings flow through localization except the "always-English" carve-out. Now we add Hindi (`hi`) and Marathi (`mr`) translations.
>
> **Tasks:**
> 1. Create `mobile/lib/l10n/app_hi.arb` and `mobile/lib/l10n/app_mr.arb` with the same message IDs and placeholder names/types as `app_en.arb`. Translate every message. Use whole-message translation (never fragment composition). Every ICU plural must have an `other` branch.
> 2. Add `hi` and `mr` to `supportedLocales` in `MaterialApp.router` (or confirm `gen-l10n` picks them up from the ARB files). Add locale data initialization for `hi` and `mr` at startup (`initializeDateFormatting('hi')`, `initializeDateFormatting('mr')`).
> 3. Create a glossary file `mobile/lib/l10n/GLOSSARY.md` documenting the approved translation for each key term (e.g. machine → मशीन/मशीन, fence → बाड़/बाड, battery → बैटरी/बॅटरी, command → कमांड/कमांड). Safety-critical terms (Turn ON/OFF, Fault, Low Battery, Charging, Theft Protection) must have a native-speaker review note. Mark AI-generated drafts as "pending native review."
> 4. Test Devanagari rendering: conjuncts, vowel marks (matras), line height, bold weights, mixed Latin/Devanagari (e.g. "YG000001 मशीन चालू"). Check on narrow screens (320px), large text scaling (1.5x), light and dark themes. Fix clipping by using flexible layouts/wrapping, not by shrinking text or disabling text scaling.
> 5. Check font fallback: does the default Android font render all Devanagari conjuncts correctly? If not, bundle a licensed Devanagari font (e.g. Noto Sans Devanagari) with required weights, configure fallback in `app.dart` theme, include its license in `licenses/`. Do NOT download fonts at runtime.
> 6. Add a catalog completeness test (`test/l10n/catalog_completeness_test.dart`) that loads all three ARB files and asserts: no missing keys in `hi`/`mr` vs `en`, no extra keys, no duplicate keys, placeholder names/types match, every ICU plural has an `other` branch, no message is identical to the English key name (catches untranslated entries). This test must fail if any language is incomplete.
> 7. Run `flutter gen-l10n`, `flutter analyze`, `flutter test` (including the new catalog test), `flutter build apk --debug`. All must pass.
>
> **Verification:** Switch the app to Hindi and Marathi (temporarily, via the locale controller) and visually confirm every screen renders correctly. The catalog test guarantees no English gaps ship. "Always-English" strings (brand, identifiers, units, codes) remain in English in all three locales.

---

## Phase 3 — Language Selection and Local Persistence

**Goal:** Usable multilingual UI independent of backend notifications.

1. Add a reusable accessible picker to Settings and, if the recommendation is approved, the login page. Show native language names, current selection and localized save/error feedback. Widgets delegate state/storage work to the locale provider; no direct API calls.
2. Switch UI immediately and await local persistence. On failure, keep the session usable but show that saving failed and offer retry; never claim restart persistence succeeded when storage failed. Coalesce/serialize rapid selections so stale completions cannot restore an older choice.
3. Keep the non-sensitive device preference through logout/session invalidation while still clearing all tokens and account data. Account-specific sync is introduced in Phase 5. Resolve region variants/device locale lists, unsupported languages, cold start and no explicit selection as specified in section 2.
4. Verify switching on a detail page, during loading, with visible validation errors, and while a command is pending. Do not recreate WebViews, resubmit network actions, discard map position or reset navigation solely to change labels. Locale-sensitive presentation must rebuild without restarting business operations.
5. Localize app-owned map controls/popups. Localized reverse-geocoded addresses are optional: if approved, pass the supported locale through the provider/service and include it in the coordinate cache key, deduplicate lookups and respect provider request limits. Preserve attribution, safely encode text inserted into HTML/JavaScript, and keep a usable address/coordinate fallback.

**Acceptance:** Settings/login choice works offline and persists on restart; storage errors are visible; logout does not lose the login-screen choice or retain credentials. Provider/widget tests verify race handling, restoration and navigation/command-state preservation. No backend sync is claimed yet.

**STOP:** Ask the user to verify Phase 3 and explicitly approve Phase 4, or approve a UI-only release using the applicable Phase 7 checks. Do not silently skip to backend work.

### Detailed implementation prompt — Phase 3

> **Context:** Phases 1–2 are complete. The app has full English/Hindi/Marathi ARB catalogs, `AppLocalizations` is wired, and the locale controller exists but defaults to English. Now we add the user-facing language picker and local persistence so the user can switch the UI language and it survives app restart — all offline, no backend calls yet.
>
> **Tasks:**
> 1. Extend the existing `mobile/lib/features/profile/pages/language_settings_page.dart` (Profile → Language Settings) to use the new `localeControllerProvider`. Show three options with native names: `English`, `हिन्दी`, `मराठी`. The current selection is highlighted. Save and Cancel buttons already exist — wire Save to call `localeControllerProvider.setLocale(code)` which immediately switches the UI and persists to `SharedPreferences` (NOT secure storage — locale is not a secret). Cancel reverts to the previously saved locale. Note: the `SharedPreferences` device preference is **provisional until Phase 5** — treat it as the effective UI locale only; do not build account-sync logic on it in this phase.
> 2. On app cold start (`app.dart` initialization), load the saved locale from `SharedPreferences` before building `MaterialApp`. If missing/corrupt/unsupported, fall back to the device locale resolved to a supported language (e.g. `hi-IN` → `hi`), or English if the device locale is not one of the three. This must not hang startup or require a network request.
> 3. On logout, `SecureStorage.clearAll()` runs (clears tokens). The locale preference in `SharedPreferences` must SURVIVE logout — it is a device-level preference, not account-scoped. Verify that `SecureStorage.clearAll()` does NOT touch `SharedPreferences`. Account-scoped sync comes in Phase 5.
> 4. Verify locale switching works on every screen: switch while on the machine detail page (with telemetry loaded), while a command is pending, while a form has validation errors, while a dialog is open. The UI must rebuild with new labels WITHOUT: recreating WebViews, resubmitting network actions, discarding map position, resetting navigation, or resetting command state. Use `Locale` change via the Riverpod provider so widgets rebuild naturally.
> 5. Localize map controls/popups if any app-owned text exists on the map screen. Reverse-geocoded addresses: pass the current locale's language code to the geocoding API's `Accept-Language` header and include locale in the coordinate cache key. If the geocoding provider doesn't support the language, fall back to English addresses. Preserve attribution.
> 6. Add provider tests: locale resolution (device locale → supported, unsupported → English), storage failure fallback, rapid selection coalescing (latest wins), persistence across simulated restart, logout preserves locale. Add widget tests: switching on detail page doesn't reset state, picker shows correct selection.
> 7. Run `flutter analyze`, `flutter test`, `flutter build apk --debug`. All must pass.
>
> **Verification:** User opens Language Settings, picks हिन्दी, taps Save → entire app switches to Hindi immediately. Kills and reopens the app → still Hindi. Logs out and back in → still Hindi (device preference survives). "Always-English" strings (YG IDs, IMEI, units, brand) remain English.

---

## Phase 4 — Backend Self-Service Language Preference

**Goal:** An independently deployable account preference API, ready before notification rendering.

**Verified starting point (2026-09-21):** most of this phase already exists — migration `V39__user_preferred_locale.sql` (`users.preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en'`), `PUT /api/v1/auth/me/locale` in `AuthController` validating en/hi/mr, and `preferredLocale` returned by `GET /me` and the login DTO. This phase verifies and hardens that work; it is not greenfield.

1. **Resolve the nullable conflict first (decision needed):** the existing column is `NOT NULL DEFAULT 'en'`, but section 2 policy says null means "not chosen". Either (a) add a follow-up Flyway migration making `preferred_locale` nullable with `CHECK (preferred_locale IS NULL OR preferred_locale IN ('en','hi','mr'))`, or (b) keep `NOT NULL DEFAULT 'en'` and revise the section 2 policy to drop the "unset" concept. Option (a) is recommended; never edit the applied `V39` migration and do not backfill.
2. Verify the user entity, immutable DTO mapping, `GET /api/v1/auth/me`, and login user DTO expose `preferredLocale` consistently (they currently do). Keep changes additive for older clients and ensure mobile parsing accepts a missing field from older servers. Follow Controller → Service → Repository; do not copy existing controller-level repository access into new logic.
3. Harden the existing `PUT /api/v1/auth/me/locale` to be idempotent with request/response `{"language":"mr"}` (align the request DTO field name with the mobile contract). Require a non-null supported code with `@Valid`/Bean Validation; reject blank, unknown, region-form and oversized values. Mobile normalizes device locales before calling this canonical-code API.
4. Resolve user and organization only from validated authentication/security context. Require an explicit self-profile write permission in the existing `resource:action` RBAC convention, scoped to the current user, with reviewed permission grants/authority loading. Do not require broad administrator user-edit rights or accept target user/organization IDs from the body. Define null-organization platform-user handling using existing tenant policy, without bypassing tenant guards.
5. Use domain exceptions and `GlobalExceptionHandler` for the standard error shape; log safe actor/context and preference changes without tokens or message bodies. Verify authentication, RBAC and tenant checks on the extended profile-read path as well. Keep locale out of JWT authorization logic.
6. Test valid updates, repeated updates, null/invalid input, unauthenticated/missing-permission requests, cross-user/tenant tampering, inactive users and old DTO compatibility. Test migration on fresh and representative existing PostgreSQL data; review the AGENTS.md Major-change checklist.

**Acceptance:** Authorized users can change only their own language and read it after login/restart. Old clients still work; migration and JUnit 5/service/security tests plus backend build pass. Notifications may still use English until Phase 6.

**STOP:** Ask the user to verify Phase 4 and explicitly approve Phase 5.

### Detailed implementation prompt — Phase 4

> **Context:** Phases 1–3 are complete. The mobile app can switch UI language and persist locally. The backend already has `users.preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en'` (migration `V39__user_preferred_locale.sql`), `PUT /api/v1/auth/me/locale` in `AuthController` (validates en/hi/mr), and `preferredLocale` in `GET /me` and the login DTO. This phase makes the backend preference API robust and self-service — the mobile app will sync with it in Phase 5. Most of the work exists; verify and harden rather than rebuild.
>
> **Tasks:**
> 1. Resolve the nullable conflict per the phase body: current column is `NOT NULL DEFAULT 'en'`, but the policy is "null means unset, fall back to English." If the nullable policy is approved, add a Flyway migration (`V{next}__preferred_locale_nullable_check.sql`) making the column nullable and adding `CHECK (preferred_locale IS NULL OR preferred_locale IN ('en', 'hi', 'mr'))`. Do NOT backfill nulls to 'en' or edit the applied `V39` migration. Also align the backend `User` entity default (`private String preferredLocale = "en"`) — drop the hard default if the column becomes nullable.
> 2. Verify `PUT /api/v1/auth/me/locale` accepts `{"language": "mr"}` (or `{"preferredLocale": "mr"}` — check the current request DTO). Ensure it uses `@Valid` + Bean Validation: non-null, one of `en`/`hi`/`mr`, reject blank/unknown/region-form/oversized values. Normalize device locales (`hi-IN` → `hi`) on the mobile side before calling.
> 3. Verify the endpoint resolves the user from JWT/security context only — never from the request body. Verify RBAC: the user can change only their own preference. Verify `GlobalExceptionHandler` maps validation failures to the standard error shape. Add logging of actor/context (user ID, old → new locale) without tokens or bodies.
> 4. Verify `GET /api/v1/auth/me` and the login response (`LoginResponse.UserInfo`) both return `preferredLocale` (or `language` — match the existing field name). They already do after the recent DTO fix — confirm and add a test for the null case (unset preference returns null, not "en").
> 5. Add JUnit 5 tests: valid update, repeated update (idempotent), null/invalid/blank/unknown-code rejection, unauthenticated rejection, missing-permission rejection, cross-user tampering rejection (user A cannot set user B's locale), inactive user handling, old DTO compatibility (missing field from older server → mobile treats as null/English).
> 6. Run `.\gradlew.bat :backend:build` and `.\gradlew.bat :backend:test`. All must pass.
>
> **Verification:** An authenticated user calls `PUT /api/v1/auth/me/locale` with `{"language": "hi"}` → 204 No Content. Calls `GET /api/v1/auth/me` → response includes `preferredLocale: "hi"`. Invalid codes return 400. Unauthenticated returns 401. Another user's locale is unchanged.

---

## Phase 5 — Reliable Mobile Profile Synchronization

**Goal:** Eventual consistency between UI language and the server preference without cross-account leakage.

1. Extend immutable mobile user parsing and existing auth/profile providers; reuse the authenticated Dio client. Separate the effective UI locale, persisted preference, and sync state (`saved`, `pending`, `failed`) instead of treating an optimistic UI switch as server confirmation.
2. On login/resume, fetch the existing profile. A pending local edit for the **same** account takes precedence; otherwise use the valid server preference. If the server preference is unset, persist/sync the locally resolved language. A guest/login-screen choice must not silently overwrite an existing account preference; the user can change it after login. The current session check clears storage on any profile-fetch exception: distinguish transient network failure from confirmed invalid authentication so offline startup does not erase pending intent. Preserve authorization/refresh restrictions; cached locale is never proof of a valid session.
3. On an authenticated selection, update UI, durably save the latest desired language and an account-scoped pending marker, then attempt the PUT through the provider/service. Scope pending data to backend environment + organization + user. Do not claim sync/persistence on local-write failure; expose retry and keep any in-session intent separate from durable state.
4. Serialize/coalesce writes per account so the latest selection wins; use a local revision/session guard so old responses cannot change current UI or clear a newer pending value. Clear pending only for the exact confirmed revision. On ambiguous timeouts, reconcile with the profile and retry the latest intent, not an obsolete value.
5. Use bounded retries/backoff for transient network/5xx failures; honor retry hints for throttling. Retry pending work on authenticated resume/reconnect or manual action without introducing a new connectivity dependency unless needed. Stop automatic retries for validation/permission errors; handle token expiry via the existing auth flow. Show a localized “App language changed; notification language has not synced” state when appropriate.
6. On logout/account switch, cancel retries and clear account-scoped pending/cached state. Never replay a prior user's write with the next user's credentials. A request already accepted may update only its originally authenticated account; ignore its late response in a different session.
7. Recommend last server-accepted preference as the cross-device policy; refresh on login/resume without polling or adding another socket channel. Two devices may temporarily differ while one is offline. Test concurrent device writes and document that an offline change takes effect server-side when it is eventually accepted.

**Acceptance:** Tests cover offline selection/restart/reconnect, storage failure, missing old-server field, rapid changes, delayed/time-out responses, permanent rejection, token expiry, shared-phone switching and two devices. UI remains usable while sync is pending; notification language is never falsely reported as saved.

**STOP:** Ask the user to verify Phase 5 and explicitly approve Phase 6. If notification prerequisites are absent, record the blocker and wait for an explicit deferral/release decision.

### Detailed implementation prompt — Phase 5

> **Context:** Phases 1–4 are complete. The mobile app switches UI language and persists locally (Phase 3). The backend has a self-service `PUT /api/v1/auth/me/locale` API (Phase 4). Now we connect them: when the user picks a language in the app, it syncs to the server so notifications are also sent in that language. The sync must be reliable — handle offline, retries, race conditions, and shared phones.
>
> **Tasks:**
> 1. Extend the mobile `localeControllerProvider` (or a new `localeSyncProvider`) to track three states per the user's account: `saved` (server confirmed), `pending` (local save done, server sync in progress), `failed` (server rejected or network error). The UI shows the current sync state — e.g. "App language changed; notification language has not synced" when pending/failed.
> 2. On language selection (Save button in Language Settings): (a) immediately switch the UI locale, (b) persist to `SharedPreferences` (device preference, survives logout), (c) if authenticated, mark the account-scoped preference as `pending` and call `PUT /api/v1/auth/me/locale` via the existing authenticated Dio client. On success, clear `pending` → `saved`. On transient failure (network/5xx), keep `pending` and retry with bounded backoff. On permanent failure (400 validation/403 permission), stop retrying and show the error.
> 3. On login/resume: fetch `GET /api/v1/auth/me`. If the server has a `preferredLocale` and there is no pending local edit for the SAME account, use the server value. If there is a pending local edit for the same account, the pending edit takes precedence (the user just changed it, the server hasn't caught up). If the server preference is null (unset), persist/sync the locally resolved language. A guest/login-screen choice must NOT overwrite an existing account preference — the user can change it after login.
> 4. Serialize/coalesce writes per account: if the user picks Hindi, then immediately picks Marathi before the Hindi PUT completes, only the Marathi PUT should go through. Use a local revision guard so an old Hindi response cannot overwrite the newer Marathi UI state.
> 5. On logout/account switch: cancel all pending retries and clear account-scoped pending/cached state. Never replay a prior user's write with the next user's credentials. The device-level locale preference (SharedPreferences) survives; the account-scoped pending state does not.
> 6. Distinguish transient network failure from confirmed invalid authentication in the session check. There are **three** `SecureStorage.clearAll()` call sites — `auth_service.dart`, `auth_provider.dart`, and the 401 path in `auth_interceptor.dart` — and none of them may erase a pending locale write. Only clear on confirmed 401/403 (valid token rejected), never on network timeout/5xx; audit all three sites, especially the interceptor, where a transient backend blip surfacing as 401 would otherwise wipe pending state.
> 7. Add provider tests: offline selection + restart + reconnect syncs, storage failure shows error, missing server field → English, rapid changes (latest wins), delayed/timeout response reconciliation, permanent rejection stops retry, token expiry triggers auth flow, shared-phone switching clears pending, two-device divergence. Run `flutter test`.
>
> **Verification:** User picks Marathi offline → UI switches immediately, "not synced" indicator shows. Comes online → PUT fires, indicator clears to "saved." Kills app, reopens, logs in → server preference (Marathi) loads. Another user logs in on the same phone → their preference loads, not the previous user's Marathi.

---

## Phase 6 — Localized Inbox and Push Templates

**Goal:** Render newly created notifications in the recipient's saved language without changing delivery/security semantics.

**Dependencies:** Multilingual Phases 4–5; verified **Notification Phase 3 — Inbox/Preferences/APIs** for backend inbox rendering, Notification Phase 4 for mobile inbox acceptance, and **Notification Phase 5 — FCM Delivery and Mobile Push** before claiming push delivery. These are the separately gated phases in `YANTRAGO_IMPLEMENTATION_PLAN.md`, not this document's phase numbers. Do not implement their missing infrastructure inside localization work.

1. Extend the notification plan's existing template/rendering boundary. Prefer version-controlled UTF-8 backend message resources and a service resolving `(eventType, channel, language)`; do not introduce a template database/editor without a demonstrated requirement. Flutter ARB and Java formatting syntax are separate contracts: choose/test the backend formatter explicitly, including named-versus-positional placeholders and escaping.
2. Resolve preference at inbox-item creation and persist effective locale, template version and rendered title/body. Delivery retries reuse the frozen locale/version and appropriate channel rendering so a preference change does not mutate history or create duplicate inbox items. Cache templates, not stale user preferences; batch recipient preference reads where appropriate.
3. Provide reviewed templates for every enabled event, including recovery/expiry variants. Missing/unsupported stored language falls back to English; missing translation falls back to its English template. Missing English/template parameters must produce an approved generic safe notification, record a sanitized diagnostic/metric, and never leak placeholders or crash processing. CI must reject incomplete enabled template catalogs.
4. Preserve assignee-only routing, recipient/current-access checks, tenant isolation, outbox/idempotency, expiry and attempt tracking. Background processing uses the established recipient/tenant context; never trust organization IDs from a mobile preference request. Keep payload contracts in `shared/` if a contract change is actually necessary; do not add locale to gateway protocol messages.
5. Server-render generic localized push text because a background/terminated app cannot rely on Flutter widget localization. Follow the notification plan's minimal lock-screen payload: opaque notification ID, schema version and safe text, without machine names, precise readings or location. Retrieve authorized detail after opening. Foreground presentation must not produce a duplicate notification.
6. Keep historical inbox content in its saved language; localize surrounding controls, type/severity labels and timestamps in the current UI language. Existing raw alert text is not magically translated: use reviewed templates with structured event data for new content, and a safe localized summary where legacy text is unstructured/unsafe. Do not silently rewrite stored history.

Example draft for **authorized inbox detail only**, not lock-screen push (native review required):

```java
// Example: LOW_BATTERY alert
// en: "Machine {machineName} battery is low ({value}%). Please charge."
// hi: "मशीन {machineName} की बैटरी कम है ({value}%)। कृपया चार्ज करें।"
// mr: "मशीन {machineName} ची बॅटरी कमी आहे ({value}%)। कृपया चार्ज करा।"
```

**Deferred SMS/email:** Do not enable these channels as part of localization. If separately approved, validate provider Unicode support, consent, cost/segment caps and applicable Indian DLT/template registration requirements. GSM-7 typically permits 160 septets for one SMS and 153 per concatenated segment; Unicode/UCS-2 typically permits 70 code units for one and 67 per concatenated segment, subject to provider/route exceptions. Devanagari combining marks consume encoding units even when displayed as one glyph. Measure the final rendered text, not visible character count or UTF-8 bytes; do not silently transliterate/truncate safety messages to reduce cost.

**Acceptance:** JUnit 5 template/fallback tests and integration tests cover three locales, stable history/retries, null preferences, missing parameters, cross-tenant/wrong-recipient rejection and privacy-safe payloads. Where FCM prerequisites exist, approved staging devices verify foreground/background/terminated presentation and duplicate handling. Do not equate provider acceptance with confirmed device delivery or send production notifications without explicit approval.

**STOP:** Ask the user to verify Phase 6 and explicitly approve Phase 7.

### Detailed implementation prompt — Phase 6

> **Context:** Phases 1–5 are complete. The mobile UI is fully localized (en/hi/mr) and the user's language preference syncs to the backend. **Verified (2026-09-21, Phase 6 audit): the premise below changed** — the backend DOES have notification localization: `NotificationTemplateService` renders from a versioned `notification_templates` table seeded with en/hi/mr rows (V24/V32/V34/V36/V40/V42/V44/V48), the inbox freezes locale/template-version/rendered text at creation, per-recipient locale resolves from `users.preferred_locale` with null→en fallback, and `PushDeliveryService`/`FcmPushProvider` deliver server-rendered localized frozen text. Notification Plan Phases 3/5 are therefore complete, and Phase 6 work reduced to: catalog completion (V52), the CI completeness gate, and placeholder-leak hardening. This phase depends on the separately-gated Notification Plan phases (inbox APIs + FCM delivery) being complete.
>
> **Prerequisite check:** Verify that Notification Phase 3 (inbox/prefs/APIs) and Phase 5 (FCM delivery) from `YANTRAGO_IMPLEMENTATION_PLAN.md` are complete. If not, STOP and record the blocker — do not build notification infrastructure inside this localization phase.
>
> **Tasks:**
> 1. Audit the existing `NotificationContentService` / `LocalizationService` / `PreferredLocaleNotifier`. Verify it resolves `(eventType, channel, language)` → rendered title/body using version-controlled UTF-8 backend message resources (NOT a template DB/editor). Confirm the formatter handles named placeholders (`{machineName}`, `{value}`) and escaping correctly for both Java and any FCM payload JSON.
> 2. At inbox-item creation time, persist the effective locale, template version, and rendered title/body in the notification record. Delivery retries reuse the frozen locale/version — a preference change must NOT mutate existing history or create duplicate items. Cache templates, not stale user preferences.
> 3. Provide reviewed templates for EVERY enabled event type in all three languages, including recovery/expiry variants. Missing/unsupported stored language falls back to English. Missing translation falls back to the English template. Missing English/template parameters must produce an approved generic safe notification, log a sanitized diagnostic, and never leak placeholders or crash.
> 4. Add a CI check that rejects incomplete enabled template catalogs — `gen-l10n` fallback alone is not a gate. Every enabled event type must have `en`, `hi`, `mr` templates or the build fails.
> 5. Server-render generic localized push text for lock-screen display (a background/terminated app cannot use Flutter widget localization). Follow the notification plan's minimal lock-screen payload: opaque notification ID, schema version, safe text — NO machine names, precise readings, or location. Detail is fetched after the user opens the app.
> 6. Foreground push presentation must not produce a duplicate notification. Historical inbox content stays in its saved language; the surrounding inbox controls (type/severity labels, timestamps) localize to the current UI language.
> 7. Preserve assignee-only routing, recipient/access checks, tenant isolation, outbox/idempotency, expiry, and attempt tracking. Background processing uses established recipient/tenant context — never trust org IDs from a mobile preference request. Do NOT add locale to gateway protocol messages.
> 8. Add JUnit 5 tests: three-locale template rendering, fallback to English on missing translation, fallback on null preference, missing parameter → safe generic notification, cross-tenant/wrong-recipient rejection, privacy-safe payload (no machine names in push), retry uses frozen locale, historical content unchanged after preference change. Run `.\gradlew.bat :backend:build :backend:test`.
>
> **Verification:** User with `preferredLocale=hi` triggers a LOW_BATTERY alert → inbox item is in Hindi ("मशीन {name} की बैटरी कम है"). FCM push shows safe Hindi text without machine name. User changes to `mr` → new alerts are in Marathi; the old Hindi inbox item stays Hindi. User with null preference → English. Missing template parameter → generic safe notification, no placeholder leak.

---

## Phase 7 — End-to-End Verification and Controlled Release

**Goal:** Validate the complete approved release scope; this phase consolidates tests, not postpones them.

| Area | Required verification |
|------|-----------------------|
| Catalogs | Valid JSON/ICU, duplicate/missing/extra keys, typed placeholders, 0/1/many counts, approved glossary, no untranslated enabled messages |
| Mobile | All screens/states in en/hi/mr, cold start, persisted/device fallback, invalid storage, live switch, dialogs/validation, maps, stable back navigation |
| Accessibility | Android only (iOS deferred to a future effort), narrow and large screens, light/dark theme, large text, TalkBack labels, Devanagari shaping and mixed-script names |
| Domain safety | Pending/ACK/DONE/FAILED wording, no command resend/reset, unchanged identifiers/units/precision, locale/timezone separation |
| Preference | Missing server language, offline/restart/retry, rapid edits, timeout reconciliation, auth expiry, permanent failure, logout/account switch, two devices |
| Backend | Validation + JWT/RBAC + tenant isolation, self-only writes, DTO compatibility, migration upgrade/fresh install, template completeness/fallback |
| Notifications | Saved historical language, new recipient-language messages, retries, inbox/push privacy, denied push permission and background/terminated state where available |
| Regression | Existing tests pass; no auth, navigation, telemetry, protocol or notification-routing behavior changes |

**Planned commands, not executed for this document:** From `mobile/`, run `flutter --version`, `flutter pub get`, `flutter gen-l10n`, `flutter analyze`, `flutter test`, and `flutter build apk --debug` using the approved SDK. Include catalog-completeness checks in CI; generation alone may only report missing translations. Verify signed release artifacts before rollout. Note: Flutter is not installed on the current Windows workspace — install the approved SDK locally or run these on a machine that has it. iOS checks are out of scope (deferred).

For backend phases, from the repository root run `.\gradlew.bat :shared:build :backend:build`; include `:device-gateway:build` only if shared/gateway integration changes, and run the applicable migration/integration tests. Record actual commands, results and blockers per phase; no weakened security/dependency policies or ignored analysis failures.

**Rollout:** Complete native review, deploy additive schema and compatible API first, then mobile sync, then enable translated templates only after notification prerequisites are verified. A UI-only release must explicitly mark deferred backend/push acceptance as not applicable rather than passed. Monitor template fallback, preference-sync failures and layout defects without logging tokens, raw personal content or high-cardinality user labels. Roll back application/template versions compatibly; retain language preferences/history and do not drop columns or purge queues.

**Acceptance:** All applicable tests/builds pass, native-speaker/device review is recorded, and remaining provider/platform or deferred-scope limits are explicit. The user approves the release scope.

**STOP:** Ask the user to verify rollout. Do not start deferred features without separate approval.

### Detailed implementation prompt — Phase 7

> **Context:** All implementation phases (1–6) are complete. This phase consolidates end-to-end verification across all three languages and all screens. No new implementation — only testing, fixing, and release validation.
>
> **Tasks:**
> 1. **Catalog verification:** Run the catalog completeness test from Phase 2. Confirm: valid JSON/ICU, no duplicate/missing/extra keys, typed placeholders match across `en`/`hi`/`mr`, every ICU plural has an `other` branch, approved glossary attached, no untranslated enabled messages.
> 2. **Mobile screen-by-screen verification:** For each language (en, hi, mr), walk through EVERY screen: login, dashboard, machine list, machine detail (with telemetry, ON/OFF, location), command history, alerts/inbox, profile, account details, notification settings, language settings, theft protection settings. Verify: cold start, persisted locale loads, device-locale fallback works, invalid storage falls back to English, live switch rebuilds all screens, dialogs/validation messages translate, map controls translate, back navigation is stable after switch.
> 3. **Accessibility:** Test on Android (and iOS if available): narrow screens (320px), large text scaling (1.5x), light/dark theme, TalkBack/VoiceOver labels. Verify Devanagari shaping (conjuncts, matras), mixed Latin/Devanagari text (e.g. "YG000001 मशीन चालू"), no clipped controls, no missing glyphs.
> 4. **Domain safety:** Verify command ACK wording is correct in all languages — pending/ACK/DONE/FAILED must never be mistranslated as "successful." Verify no command resend or state reset on locale change. Verify identifiers (machine ID, IMEI, serial), units (%, V, /4), and enum codes remain English. Verify timezone handling is unaffected by locale.
> 5. **Preference sync:** Test missing server language (null → English), offline/restart/retry, rapid edits (latest wins), timeout reconciliation, auth expiry triggers auth flow, permanent failure stops retry, logout clears account pending, account switch does not replay previous user's write, two devices diverge then converge.
> 6. **Backend:** Run `.\gradlew.bat :backend:build :backend:test`. Verify validation + JWT/RBAC + tenant isolation, self-only writes, DTO compatibility (old client with missing field), migration upgrade/fresh install, template completeness/fallback.
> 7. **Notifications:** Verify saved historical language is preserved, new recipient-language messages render correctly, retries use frozen locale, inbox/push privacy (no machine names in lock-screen push), denied push permission handling, background/terminated state (if FCM prerequisites met).
> 8. **Regression:** All existing tests pass. No auth, navigation, telemetry, protocol, or notification-routing behavior changes. Run `flutter analyze`, `flutter test`, `flutter build apk --debug`, `.\gradlew.bat :backend:build`. Record actual commands, results, and blockers.
> 9. **Rollout:** Deploy additive schema and compatible API first, then mobile sync, then enable translated templates only after notification prerequisites are verified. Monitor template fallback, preference-sync failures, and layout defects. Do NOT log tokens, raw personal content, or high-cardinality user labels.
>
> **Verification:** All applicable tests/builds pass. Native-speaker review is recorded for Hindi and Marathi safety-critical wording. Remaining provider/platform limits (iOS, SMS, email, OS-level per-app language) are explicitly documented as deferred. User approves the release scope.

---

## 3. Decisions to Confirm Before Implementation

1. **Locale policy:** Approve device-language fallback, native-name Settings + login picker, nullable server preference, and the account/server precedence described above?
2. **Terminology/formatting:** Who approves Hindi/Marathi translations? Confirm Latin telemetry digits, date/time conventions and command/alert glossary.
3. **Always-English carve-out:** Approve the list in section 2 ("Always-English text policy")? Specifically: brand name, identifiers, units, enum codes, developer strings stay English. Add or remove items before implementation begins.
4. **"Machine state changes only device confirmation." note:** Keep English (technical caveat) or translate (user-facing guidance)? Default recommendation: translate, since it's visible to end users on the machine detail screen.
5. **Scope (partially confirmed 2026-09-21):** iOS is **out of scope** for this effort (deferred to a future effort); admin-web remains **English-only**. Still to confirm: ship UI-only first or continue through profile sync and notification localization? Keep SMS/email, admin editing, historical retranslation, OS-level per-app language integration and localized geocoding deferred unless selected.
6. **Toolchain:** Which supported Flutter/Dart version should local development and both CI workflows use? `mobile-ci.yml` pins Flutter 3.10.0, which cannot satisfy the lockfile (>=3.44.0) — that job is already broken; `build-apk.yml` uses 3.47.2. Standardizing on one modern version (and tightening the `pubspec.yaml` constraint to match) is a prerequisite to resolve before Phase 1, not a reason to install packages blindly. Note: Flutter is not installed on the current Windows workspace.
7. **Non-widget layer refactor:** Approve refactoring `validators.dart`, `error_interceptor.dart`, and `notification_inbox.dart` to return typed error codes instead of English strings? This is required for proper localization and cannot be skipped.

## 4. Phase Summary and Implementation Tracker

Phase numbering is revised from the original draft: backend preference API is now Phase 4, mobile sync Phase 5, notification templates Phase 6; font/layout work moves into Phase 2 and remains a release gate. Every implementation phase includes tests.

| Phase | Scope | Dependencies | Status / approval gate |
|-------|-------|--------------|------------------------|
| 1 | Foundation, inventory, English extraction, formatting/errors | Approved SDK/configuration | **Implemented 2026-09-21** (Flutter 3.47.2 approved; CI is the verification gate — Flutter not installed locally). Verify before Phase 2 |
| 2 | Hindi/Marathi, glossary, font/layout quality | Phase 1 | **Implemented 2026-09-21** (AI-draft catalogs `app_hi.arb`/`app_mr.arb` with parity-verified structure; `GLOSSARY.md` marks all translations PENDING NATIVE REVIEW; catalog completeness test added; Devanagari rendering/layout checks deferred to on-device verification — Android ships Noto Sans Devanagari, so no bundled font unless device testing shows gaps). Verify before Phase 3 |
| 3 | Picker, offline persistence, switching | Phases 1–2 | **Implemented 2026-09-21** (Settings Save → immediate switch + SharedPreferences persistence with failure banner/retry; pre-login picker on login; logout survives by store separation — `SecureStorage.clearAll()` never touches SharedPreferences; locale-aware cached date formatters; localized reverse geocoding with language in the cache key). Verify before Phase 4 or UI-only release |
| 4 | Harden existing preference API (V39/PUT /me/locale) + resolve nullable-vs-NOT NULL decision | Phase 3 approval; independent of notifications | **Implemented 2026-09-21** — nullable decision resolved in favor of nullable: `V51__user_preferred_locale_nullable.sql` (DROP DEFAULT/DROP NOT NULL + CHECK constraint; no backfill, V39 untouched). `PUT /me/locale` hardened: Bean Validation on `UpdateLocaleRequest` (@NotBlank/@Pattern, standard 400 error shape), logic extracted to `AuthService.updatePreferredLocale` (idempotent, logged old→new, JWT-principal-only). Admin path `UpdateUserRequest.preferredLocale` now `@Pattern`-validated. Entity default `'en'` removed. 22 new JUnit 5 tests pass; full backend suite has 5 PRE-EXISTING failures (Command/TheftProtection/Notification switches — reproduced on unmodified HEAD). Verify before Phase 5 |
| 5 | Account-scoped mobile sync | Phases 3–4 | **Implemented 2026-09-21** — `localeSyncProvider` (pending/synced/failed states, serialized writes with revision guard, account-scoped durable pending marker in SharedPreferences, bounded backoff retries + resume/manual retry, generation guard cancels on account switch); session-resolve precedence (pending edit > server preference > first sync of unset account); `_checkExistingSession` now distinguishes confirmed 401/403 (clears storage) from transient failures (keeps tokens + pending intent); 401-interceptor teardown clears sync state; mobile `preferredLocale` now nullable. Verify before Phase 6 |
| 6 | Localized inbox/push templates | Phases 4–5 + applicable Notification Phases 3–5 | **Implemented 2026-09-21** — prerequisite audit confirmed Notification Phases 3/5 are ALREADY complete (DB-backed `NotificationTemplateService` with en/hi/mr seeds via V24/V32/V34/V36/V40/V42/V44/V48; inbox freezes locale/template_version/rendered text at creation; per-recipient locale with null→en fallback at 3 layers; FCM push renders frozen localized text). Gaps closed: `V52` adds hi/mr COMMAND_FAILED/TIMEOUT + ESCALATED variants for LOW_BATTERY/BATTERY_CRITICAL/GSM_SIGNAL_LOW (11 rows); CI gate `NotificationTemplateCatalogTest` (migration-scan: every enabled event × every seeded state × {en,hi,mr} must exist, plus locale-code and blank-text checks); placeholder-leak hardening in `NotificationTemplateService` (unresolved `{tokens}` → safe substitute, logged, unit-tested). **Decision recorded:** lock-screen push keeps rich text (machine names/readings) — the privacy-minimal clause was explicitly NOT selected (2026-09-21). Deferred to notification plan: inbox expiry columns, persistent/persistent cross-restart duplicate suppression, data-only FCM handling. hi/mr template wording pending native review. Verify before Phase 7 |
| 7 | End-to-end release validation | All phases in the explicitly approved release scope | **Verification executed 2026-09-22** (see Phase 7 record below). Release sign-off pending user |

**References checked:** [Flutter internationalization](https://docs.flutter.dev/ui/accessibility-and-internationalization/internationalization) and [SMS encoding/segment limits](https://www.twilio.com/docs/glossary/what-sms-character-limit). Recheck generator options against the selected SDK and limits against the selected SMS provider if that deferred channel is approved.

**Current action completed by this document:** review and planning improvements only. No dependency installation, application implementation, migration execution, deployment, notification send, or build is part of this task.

---

## 5. Phase 7 Verification Record (2026-09-22)

**Executed on this workspace (Windows, no Flutter SDK — mobile checks were static; backend via Gradle):**

- **Backend builds:** `:shared:build`, `:device-gateway:build`, `:backend:build -x :backend:test` — all green. Full `:backend:test`: **383 tests, 5 failures** — the exact set proven pre-existing on unmodified `HEAD` (CommandServiceTest ×3, NotificationFeatureSwitchesTest, TheftProtectionServiceTest; unrelated to localization; tracked for separate cleanup). All 27 localization-specific tests (Phase 4: 22, Phase 6: 10, of which 5 overlap suites) pass.
- **Catalog gates:** backend `NotificationTemplateCatalogTest` (every enabled event × seeded state × {en,hi,mr}) passes; mobile ARB parity re-verified programmatically (271 messages × 3 locales, zero failures).
- **Domain safety (audited):** command lifecycle wording in en/hi/mr never equates pending/ACK with success ("डिवाइस की पुष्टि का इंतज़ार" / "awaiting device acknowledgement"); confirmed states read "पुष्टि प्राप्त". `setLocale` is reachable only from the two pickers and the sync controller — no command or network path depends on locale; a switch rebuilds labels only. Timezone parsing in `date_utils` is locale-independent (`Intl.defaultLocale` touches formatters only). Identifiers, units and raw codes remain hardcoded English per the always-en carve-out.
- **CI wiring:** `mobile-ci.yml` (Flutter 3.47.2, `pub get` → `gen-l10n` → `analyze` → `test`) is the PR gate; `build-apk.yml` on 3.47.2; `backend-ci.yml` runs `:backend:test` which includes the template catalog gate.
- **Security/privacy sweep:** no secrets in localization files; preference endpoints self-scoped from JWT principal; no tokens or raw personal content in logs.

**Release scope (per section 2 decisions):** Android UI-only + backend additive schema/API (V51, V52, hardened `/me/locale`), notification-language sync, and localized inbox/push (rich lock-screen text — privacy-minimal clause NOT selected). **Out of scope / deferred:** iOS (future effort), admin-web (English-only), SMS/email channels, historical notification retranslation, OS-level per-app language, inbox expiry columns, data-only FCM handling, cross-restart duplicate suppression.

**Rollout order:** 1) Deploy backend (Flyway applies V51→V52 on startup — verify logs once). 2) Release mobile build (signed APK via `build-apk.yml`). 3) Monitor template-fallback warnings (`unresolved placeholder`, `No template found`), preference-sync failures, and layout defects — without logging tokens or raw personal content. 4) Roll back by application version only; never drop columns or purge queues.

**Remaining blockers to production sign-off (user-side):**
1. CI green run of the mobile workflow (gen-l10n/analyze/test) — Flutter not installed on this workspace.
2. On-device verification: tri-language screen walkthrough (Phase 7 table, "Mobile" row), Devanagari rendering (conjuncts, 320px, 1.5x scale, dark mode), locale-switch-without-state-loss, airplane-mode sync flow, two-device convergence.
3. **Native-speaker review of Hindi/Marathi wording** (app ARBs + notification templates) — PENDING per GLOSSARY.md; required before exposing hi/mr to production users.
