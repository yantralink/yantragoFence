# YantraGO Mobile — Multi-Language Implementation Plan (English, Hindi, Marathi)

**Prepared / reviewed:** 2026-09-10

**Status:** Planning and code review only; no implementation authorized or completed.

**Scope:** Flutter-owned UI, formatting, account language preference, and localized inbox/push templates when the notification prerequisites are ready. English (`en`), Hindi (`hi`), Marathi (`mr`).

**Authority:** Follow `AGENTS.md`, `YANTRAGO_PROJECT_STRUCTURE.md` sections 5 and 8, and the consolidated Notification Plan in `YANTRAGO_IMPLEMENTATION_PLAN.md` sections N1–N13. Keep the existing backend + TCP gateway architecture and shared RabbitMQ contracts.

**Execution rule:** Implement one phase at a time, test/build the affected modules, STOP, and ask the user to verify and explicitly approve the next phase. Reviewing or approving this document alone does not authorize implementation.

---

## 1. Review Findings and Current State

The app has no ARB-based UI localization, but it already uses `intl`. Findings below describe the inspected working tree, not a verified deployed release.

| Area | Verified finding | Required improvement |
|------|------------------|----------------------|
| Flutter setup | `mobile/lib/app.dart` has no localization delegates; `pubspec.yaml` has no `flutter_localizations` or generation setting | Use Flutter SDK `gen-l10n`, not a parallel translation library |
| Existing formatting | `intl: ^0.18.0` is declared, locked to 0.18.1, and used by `core/utils/date_utils.dart` | Replace locale-implicit static date formatters and English relative-time messages |
| SDK compatibility | Mobile CI pins Flutter 3.10.0; APK CI pins 3.24.5; the inspected lockfile requires Flutter >=3.44.0 / Dart >=3.12.0 | Resolve the SDK/lockfile mismatch with approval before installing localization dependencies; do not guess an `intl` version |
| Inventory | Original estimate was 120+ strings across 32 files, not an exhaustive extraction | Audit all user-visible text, including providers, helpers, API-derived labels, semantics and embedded map content |
| Storage | `SecureStorage.clearAll()` deletes every key and is called by logout/session failure paths | Separate device language from credentials and account-scoped pending changes; never retain tokens to preserve a language |
| Profile | Mobile reads `GET /api/v1/auth/me`; `profileProvider` exposes `currentUserProvider`; inspected user model/migrations have no language field | Extend the existing profile read contract and add a narrowly scoped self-service language update |
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

---

## Phase 2 — Hindi/Marathi Translations and Rendering Quality

**Goal:** Complete reviewed language catalogs that are safe to expose to users.

1. Add `app_hi.arb` and `app_mr.arb` with matching message IDs and placeholder names/types. Translate whole messages and appropriate plural forms; every ICU plural has an `other` branch. Validate apostrophe/brace escaping with `gen-l10n` rather than assuming Flutter and Java template syntax are interchangeable.
2. Maintain a glossary and native-speaker review record in this plan or the existing translation workflow. Suggested drafts: `machineTurnOn` → `मशीन चालू करें` (Hindi), `मशीन चालू करा` (Marathi); use `भाषा` for the language section and native language names for choices. Review command acknowledgement/completion wording separately.
3. Verify Devanagari conjuncts, vowel marks, line height, bold weights and mixed Latin/Devanagari text on Android **and iOS**, light/dark themes, narrow screens and large accessibility text. Use flexible layouts/wrapping instead of shrinking text or disabling text scaling. Critical action labels and warnings must remain fully readable; list previews may truncate only when full content is accessible.
4. Test existing font fallback offline. If insufficient, bundle a licensed Devanagari font with required weights and configure fallback in the existing theme; include its license and check app size. Do not download fonts at runtime.
5. Add a catalog check for missing/extra/duplicate keys, invalid message identifiers, placeholder mismatches and untranslated messages. `gen-l10n` fallback alone is not a completeness gate; new languages must not silently ship English gaps. Add only complete catalogs to advertised/generated supported locales.

**Acceptance:** All three locales generate and pass catalog, formatting and representative screen/semantics tests; no missing glyphs or inaccessible clipped controls. Hindi and Marathi safety-critical wording has recorded native-speaker approval before production exposure.

**STOP:** Ask the user to verify Phase 2 and explicitly approve Phase 3.

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

---

## Phase 4 — Backend Self-Service Language Preference

**Goal:** An independently deployable account preference API, ready before notification rendering.

1. Add an additive Flyway migration under `database/migrations/`, choosing the next unused version at implementation time. Proposed field: nullable `users.language VARCHAR(10)` with a database check restricting non-null values to `en`, `hi`, `mr`. Null means unset; effective notification fallback is English. Do not backfill everyone to explicit English or edit applied migrations.
2. Extend the user entity and immutable DTO mapping, existing `GET /api/v1/auth/me`, and login user DTO with nullable `language`. Keep changes additive for older clients and ensure new mobile parsing accepts a missing field from older servers. Follow Controller → Service → Repository; do not copy existing controller-level repository access into new logic.
3. Add idempotent `PUT /api/v1/users/me/language` with request/response `{"language":"mr"}`. Require a non-null supported code with `@Valid`/Bean Validation; reject blank, unknown, region-form and oversized values. Mobile normalizes device locales before calling this canonical-code API.
4. Resolve user and organization only from validated authentication/security context. Require an explicit self-profile write permission in the existing `resource:action` RBAC convention, scoped to the current user, with reviewed permission grants/authority loading. Do not require broad administrator user-edit rights or accept target user/organization IDs from the body. Define null-organization platform-user handling using existing tenant policy, without bypassing tenant guards.
5. Use domain exceptions and `GlobalExceptionHandler` for the standard error shape; log safe actor/context and preference changes without tokens or message bodies. Verify authentication, RBAC and tenant checks on the extended profile-read path as well. Keep locale out of JWT authorization logic.
6. Test valid updates, repeated updates, null/invalid input, unauthenticated/missing-permission requests, cross-user/tenant tampering, inactive users and old DTO compatibility. Test migration on fresh and representative existing PostgreSQL data; review the AGENTS.md Major-change checklist.

**Acceptance:** Authorized users can change only their own language and read it after login/restart. Old clients still work; migration and JUnit 5/service/security tests plus backend build pass. Notifications may still use English until Phase 6.

**STOP:** Ask the user to verify Phase 4 and explicitly approve Phase 5.

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

---

## Phase 7 — End-to-End Verification and Controlled Release

**Goal:** Validate the complete approved release scope; this phase consolidates tests, not postpones them.

| Area | Required verification |
|------|-----------------------|
| Catalogs | Valid JSON/ICU, duplicate/missing/extra keys, typed placeholders, 0/1/many counts, approved glossary, no untranslated enabled messages |
| Mobile | All screens/states in en/hi/mr, cold start, persisted/device fallback, invalid storage, live switch, dialogs/validation, maps, stable back navigation |
| Accessibility | Android/iOS, narrow and large screens, light/dark theme, large text, TalkBack/VoiceOver labels, Devanagari shaping and mixed-script names |
| Domain safety | Pending/ACK/DONE/FAILED wording, no command resend/reset, unchanged identifiers/units/precision, locale/timezone separation |
| Preference | Missing server language, offline/restart/retry, rapid edits, timeout reconciliation, auth expiry, permanent failure, logout/account switch, two devices |
| Backend | Validation + JWT/RBAC + tenant isolation, self-only writes, DTO compatibility, migration upgrade/fresh install, template completeness/fallback |
| Notifications | Saved historical language, new recipient-language messages, retries, inbox/push privacy, denied push permission and background/terminated state where available |
| Regression | Existing tests pass; no auth, navigation, telemetry, protocol or notification-routing behavior changes |

**Planned commands, not executed for this document:** From `mobile/`, run `flutter --version`, `flutter pub get`, `flutter gen-l10n`, `flutter analyze`, `flutter test`, and `flutter build apk --debug` using the approved SDK. Include catalog-completeness checks in CI; generation alone may only report missing translations. Verify signed release artifacts before rollout. iOS build/device checks require a configured macOS host and cannot be completed on this Windows workspace.

For backend phases, from the repository root run `.\gradlew.bat :shared:build :backend:build`; include `:device-gateway:build` only if shared/gateway integration changes, and run the applicable migration/integration tests. Record actual commands, results and blockers per phase; no weakened security/dependency policies or ignored analysis failures.

**Rollout:** Complete native review, deploy additive schema and compatible API first, then mobile sync, then enable translated templates only after notification prerequisites are verified. A UI-only release must explicitly mark deferred backend/push acceptance as not applicable rather than passed. Monitor template fallback, preference-sync failures and layout defects without logging tokens, raw personal content or high-cardinality user labels. Roll back application/template versions compatibly; retain language preferences/history and do not drop columns or purge queues.

**Acceptance:** All applicable tests/builds pass, native-speaker/device review is recorded, and remaining provider/platform or deferred-scope limits are explicit. The user approves the release scope.

**STOP:** Ask the user to verify rollout. Do not start deferred features without separate approval.

---

## 3. Decisions to Confirm Before Implementation

1. **Locale policy:** Approve device-language fallback, native-name Settings + login picker, nullable server preference, and the account/server precedence described above?
2. **Terminology/formatting:** Who approves Hindi/Marathi translations? Confirm Latin telemetry digits, date/time conventions and command/alert glossary.
3. **Scope:** Ship UI-only first or continue through profile sync and notification localization? Keep SMS/email, admin editing, historical retranslation, OS-level per-app language integration and localized geocoding deferred unless selected.
4. **Toolchain:** Which supported Flutter/Dart version should local development and both CI workflows use? The inspected SDK/lockfile mismatch is a prerequisite to resolve, not a reason to install packages blindly.

## 4. Phase Summary and Implementation Tracker

Phase numbering is revised from the original draft: backend preference API is now Phase 4, mobile sync Phase 5, notification templates Phase 6; font/layout work moves into Phase 2 and remains a release gate. Every implementation phase includes tests.

| Phase | Scope | Dependencies | Status / approval gate |
|-------|-------|--------------|------------------------|
| 1 | Foundation, inventory, English extraction, formatting/errors | Approved SDK/configuration | Not started; verify before Phase 2 |
| 2 | Hindi/Marathi, glossary, font/layout quality | Phase 1 | Not started; verify before Phase 3 |
| 3 | Picker, offline persistence, switching | Phases 1–2 | Not started; verify before Phase 4 or UI-only release |
| 4 | Language migration + self-service API | Phase 3 approval; independent of notifications | Not started; verify before Phase 5 |
| 5 | Account-scoped mobile sync | Phases 3–4 | Not started; verify before Phase 6 |
| 6 | Localized inbox/push templates | Phases 4–5 + applicable Notification Phases 3–5 | Not started; verify before Phase 7 |
| 7 | End-to-end release validation | All phases in the explicitly approved release scope | Not started; verify before expansion |

**References checked:** [Flutter internationalization](https://docs.flutter.dev/ui/accessibility-and-internationalization/internationalization) and [SMS encoding/segment limits](https://www.twilio.com/docs/glossary/what-sms-character-limit). Recheck generator options against the selected SDK and limits against the selected SMS provider if that deferred channel is approved.

**Current action completed by this document:** review and planning improvements only. No dependency installation, application implementation, migration execution, deployment, notification send, or build is part of this task.
