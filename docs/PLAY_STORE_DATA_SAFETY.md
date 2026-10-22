# YantraGO — Data Safety Form Answers

Grounded in code paths (mobile/lib). Use verbatim in Play Console →
App content → Data safety. Do NOT select "No data collected".

Legend: Collected = leaves the device to our backend.
Shared = sent to a third party (only OSM, consent-gated).

## Location

- **Approximate or precise location** — machine coordinates are
  displayed from backend data; on the machine map screen the app may
  send the *machine's* coordinates to our backend geocoding proxy,
  which forwards them to the map provider — **only after explicit
  in-app consent** (`map_consent_service.dart`, revocable from the
  map screen). Never the user's personal location.
  - Collected: Yes (ephemeral, for address resolution).
  - Shared: Yes — OpenStreetMap/Nominatim via our backend proxy.
  - Purpose: app functionality (address label on map).

## Personal info

- **Email address** — login credential, sent to backend on sign-in.
  Collected: Yes. Required: Yes. Purpose: account management.
- **User IDs** — JWT `userId` + `organizationId` claims in every
  authenticated API call. Collected: Yes. Purpose: app functionality.

## Financial info — none. Health & fitness — none. Contacts — none.
## Files, photos, audio, calendar — none. Web browsing — none.

## App activity

- **App interactions** — commands issued (ON/OFF), alert
  acknowledgements, map consent choice, language choice are stored on
  the backend as part of account state/audit (`machine_commands`,
  `command_attempts`, `audit_logs` tables). Collected: Yes.
  Purpose: app functionality + safety/audit.

## App info & performance

- **Crash logs / diagnostics** — no crash/analytics SDK (no Firebase
  Analytics, no Crashlytics). Answer: **Not collected**.

## Device or other IDs

- **Device or other IDs** — FCM token + `platform` + `appVersion`
  sent to `/api/v1/device-tokens` on push registration
  (`push_notification_provider.dart`). Firebase installation ID is
  created by firebase_messaging. Collected: Yes.
  Purpose: notifications. Shared: Firebase Cloud Messaging (Google).
- **IMEI / SIM** — of the *fence device*, not the phone: displayed
  from backend device records; never read from the phone (no
  READ_PHONE_STATE permission). Still, the app shows hardware IDs —
  declare as collected? Conservative answer: **Yes** under
  "Device or other IDs" — the backend stores device IMEI/SIM; the
  app displays it. If Play's questionnaire scopes this to phone-side
  collection only, answer accordingly — but stay consistent with the
  description "fence device identifiers are stored on our servers".

## Messages, photos/files — none collected.

## Data deletion

- Yes. Account deletion is requestable in-app: Profile → Support &
  privacy → "Request account deletion" (`account_service.dart`,
  POST /api/v1/account/deletion-requests), or via the email in the
  privacy policy. Public policy: https://yantrago.com/privacy-policy

## Security practices

- Encrypted in transit: yes (TLS only; HTTP blocked at nginx level).
- Deletable on request: yes (in-app request + email channel).
- Data shared for declared purposes only; nothing sold.

## SDK inventory (for the audit trail)

- firebase_messaging — FCM token + installation ID → Google.
- flutter_secure_storage — tokens on-device only, never transmitted.
- webview/Leaflet assets — bundled, no remote code; tiles from OSM.
- No analytics, no crashlytics, no ads SDK.
