Devin Implementation Prompts

Yantrago App — Machine Card UI Replacement

Purpose: Replace the existing Machine Card UI with the approved pale red/pink design reference, while preserving the current Machine List screen, data, APIs, and functionality. The location row is a required addition because it is not currently shown.

How to use this document

Give the prompts to Devin in order. Attach the current application screenshot and the final generated Machine Card reference image when starting implementation. Prompt 1 is analysis-only; the remaining prompts direct implementation and validation.

Prompt 1 — Analyze the Existing UI and Codebase

You are working on my existing Yantrago mobile application.

I want to UPDATE the existing Machine Card UI using the newly generated red/pink Machine Card design reference. This is a UI replacement task, not a new feature or a new screen.

Before modifying anything, analyze the codebase and identify:
1. The frontend framework and current Machines screen.
2. The existing Machine Card component and its styling.
3. The component responsible for machine icon, machine name, device ID, Online/Offline badge, fence status, confirmation, last-seen information, location, and navigation chevron.
4. The existing machine data model and API integration.
5. Current state management and machine status logic.
6. Existing card click handler and navigation destination.
7. Current theme, design system, and icon library.
8. Any download icon or floating download button associated with this screen.
9. Whether the Machine Card is reused on other screens.

Do not modify code during this analysis.

Return the exact files that need modification, reusable components, risks, and a concise implementation plan.

Important:
- We are replacing the existing Machine Card, not creating a second card.
- Do not create a new Machines screen.
- Do not duplicate existing functionality.
- Do not change backend APIs or database schema as part of the UI redesign.

Prompt 2 — Replace the Old Machine Card with the New Red/Pink Design

Now implement the redesigned Machine Card in the existing application.

Use the newly generated red/pink Machine Card screenshot as the FINAL visual reference. Replace the old card UI; do not keep the old card alongside the new one.

Card appearance:
- Pale blush-red background: #FFF5F5.
- Thin soft-red border: #F5B5B8.
- Red bottom accent: #EF4444.
- Subtle red-tinted shadow: rgba(239, 68, 68, 0.10).
- Rounded corners and a clean, modern, premium appearance.
- The background should have a soft red tint, not a strong red fill.

Card structure:

TOP SECTION
- Existing machine icon in its light-blue rounded container.
- Machine name.
- Device ID below the name.
- Online/Offline badge aligned on the right.

MIDDLE SECTION
- A subtle horizontal divider.
- Existing fence state and confirmation information, such as “Fence OFF · confirmed”.
- Last-seen information below it.

BOTTOM SECTION — REQUIRED LOCATION ROW
- A location-pin icon.
- The machine’s current location/address.
- This row must be visible in the card and must not be omitted.

Keep the existing navigation chevron and card click behavior.

Use actual API data. Example names, IDs, statuses, timestamps, and addresses in the reference image are for visual guidance only and must not be hardcoded.

Build the actual UI with the project’s native components and styling system. Do not use the screenshot as a background image.

Replace the old Machine Card component’s visual design. Do not create a duplicate card.

Prompt 3 — Add and Display the Machine Location

Our current Machine Card does not display the machine’s location. Add a dedicated, visible location row to the redesigned card.

Location row requirements:
- Place it near the bottom of the card, below fence status and last-seen information.
- Include a location-pin icon and the current machine location/address.
- Keep the row visible for both Online and Offline machines.
- Ensure the location does not overlap the navigation chevron.
- Support wrapping long addresses without horizontal overflow.
- Use a suitable fallback such as “Location unavailable” if no location is available; never fabricate an address.

Data source:
1. Inspect the existing machine API response and data model for a formatted address, location object, or latitude/longitude.
2. Reuse the current location source and any existing reverse-geocoding/location-resolution functionality.
3. If only coordinates are provided, use the app’s existing resolution mechanism if available.
4. Do not add a paid geocoding service or new backend endpoint without first checking the current implementation.
5. Do not hardcode the sample Bengaluru address.
6. Keep the displayed location synchronized with existing machine updates.

The location row is a mandatory part of the final Machine Card alongside machine name, device ID, connectivity status, fence status, and last-seen information.

Prompt 4 — Preserve Accenture Look and Feel and Existing Screen Elements

Perform a visual consistency pass on the redesigned Machine Card.

Only the Machine Card should receive the pale red/pink styling. Preserve the rest of the existing Accenture application.

Keep unchanged:
- Accenture green branding and header.
- MACHINES heading.
- Assigned machine count.
- Existing screen background.
- Existing bottom navigation: Machines, Notifications, Analytics, Profile.
- Active navigation state and notification badge.
- Existing navigation routes and screen layout.

Card styling:
- Background: #FFF5F5.
- Border: #F5B5B8.
- Bottom accent: #EF4444.
- Subtle red-tinted shadow.
- Existing light-blue machine icon container.
- Existing blue Online badge.
- Dark navy machine name.
- Gray device ID, last-seen information, and location text.
- Readable location-pin icon.

Do not apply the red/pink scheme to the whole screen.
Do not change the semantic meaning of status colors. The red card appearance is a visual design choice and does not mean the machine is Offline, faulty, or in an alarm state.

Match the generated reference while preserving the app’s original identity.

Prompt 5 — Preserve Machine Data, APIs, and Business Logic

Verify that the redesigned Machine Card continues to use existing data and business logic.

Preserve:
- Existing API endpoints and contracts.
- Existing machine model and state management.
- Machine name and device ID.
- Online/Offline connectivity status.
- Fence ON/OFF state.
- Fence confirmation state.
- Last-seen timestamp and formatting logic.
- Current machine location.
- Existing card navigation.
- Existing machine-control functionality.
- Existing refresh and real-time update behavior.
- Loading, empty, and error states.

Do not:
- Hardcode reference-image values.
- Add dummy data.
- Duplicate API requests.
- Introduce an unnecessary state management system.
- Change the backend or database schema for this UI task.

Online/Offline status and Fence ON/OFF state must remain separate. An Online machine can have Fence OFF. Do not infer fence state from connectivity or card color.

Ensure card data refreshes through the existing update mechanism.

Prompt 6 — Remove Unwanted Elements and Check Responsive Layout

After replacing the Machine Card, clean up the existing Machines screen.

Requirements:
- Remove obsolete old-card styling and unused code where safe.
- Do not render both old and new cards.
- Do not add a download icon or floating download button.
- Do not add new Live Tracking or Dashboard buttons unless they already exist in the application.
- Do not add vehicle-specific metrics such as speed, mileage, or weather.
- Do not change existing navigation or unrelated screens.

If a download control currently exists exclusively on this Machines screen, remove it from this screen only. Do not remove shared download functionality from other modules.

Responsive validation:
- Small and large Android screen widths.
- Long machine names and device IDs.
- Long location addresses.
- Online and Offline states.
- Multiple assigned machines.

Prevent text overflow, overlapping badges, clipped location, horizontal scrolling, excessive card height, and unnecessary blank space. Keep touch targets accessible and spacing consistent with the reference.

Prompt 7 — Test, Validate, and Report

Perform a complete validation of the updated Machine Card. The old card must be replaced by the new red/pink design without affecting existing functionality.

UI validation:
1. Old Machine Card is no longer rendered.
2. New pale red/pink card is displayed.
3. Border, red bottom accent, and subtle shadow are correct.
4. Online badge remains blue.
5. Machine icon retains its light-blue container.
6. Machine name and device ID are visible.
7. Fence status and confirmation are visible.
8. Last-seen information is visible.
9. Location row with location-pin icon is visible.
10. Location uses actual data and wraps correctly.
11. Navigation chevron remains visible.
12. No download icon or floating download button is added.
13. The result matches the supplied reference.

Functional validation:
- Actual API data is displayed.
- Online/Offline status works.
- Fence ON/OFF state works.
- Confirmation and last-seen information work.
- Location is populated and updates from existing data.
- Card navigation works.
- Existing machine control behavior is unaffected.
- Multiple machine cards render correctly.
- Loading and error states work.

Regression validation:
- Accenture branding, MACHINES heading, and assigned count are unchanged.
- Bottom navigation and notification badge are unchanged.
- Other screens remain unaffected.
- No unintended API or database changes were introduced.
- App builds successfully.

Provide:
1. Modified file list.
2. Components replaced or updated.
3. Summary of UI changes.
4. Confirmation that location is included and data-driven.
5. Confirmation that existing APIs and business logic are preserved.
6. Tests executed and actual results.
7. Remaining issues.

Implement directly in the existing project. Do not stop at a proposal. Never claim a test passed unless it was actually executed.

Final Acceptance Criteria

The existing Machine Card is replaced; no duplicate card or new screen is created.

The card has a pale blush-red background, soft red outline, subtle red shadow, and red bottom accent.

The Online badge remains blue and the machine icon remains in its light-blue container.

Machine name, device ID, fence status/confirmation, last-seen information, and location are visible.

Location is a required row populated from actual machine data, not hardcoded.

Accenture branding, MACHINES heading, assigned count, and bottom navigation remain unchanged.

No download icon or floating download button is added.

Existing APIs, navigation, machine controls, and business logic continue to work.

---

## Phase 1 implementation checkpoint — 31 October 2026

The first implementation phase after the analysis is complete, pending user verification. This phase includes the card replacement and required location wiring together so the delivered card is self-contained.

- Replaced `machine_card.dart` with the blush surface, soft border, red bottom accent, blue connectivity badge/icon container, divider, fence/clock icons and existing navigation.
- Added `machine_card_theme.dart` and `machine_card_location.dart`. Styling is local to machine-list cards, with a dark-mode counterpart.
- Reused the existing location/address providers and backend endpoints. Coordinates are the fallback when geocoding is unavailable or consent has not been granted; absent GPS displays the existing localized unavailable message.
- Offline cards label their location as last known, translated into English, Hindi and Marathi. Long names and addresses wrap.
- Machine-list rendering is lazy to avoid requesting addresses for the entire downloaded list at once. Pull-to-refresh refreshes location and dependent address data.
- Removed the permanent client address cache; the backend retains coordinate-based caching. Concurrent identical requests are coalesced, and account changes cancel old geocoding requests.
- Corrected the reviewed status-label defect: connectivity alone no longer implies “Fence OFF · confirmed.” Explicit `FENCING_ON` / `FENCING_OFF` states produce confirmed labels; other states retain fault/inventory/unknown wording. The screenshot's OFF text will therefore appear only when supported by actual state.
- No search box, fleet filter chips, download control, new backend endpoint or schema change was introduced.

Validation performed:

- `flutter test --no-pub --exclude-tags golden --reporter expanded`: **313 passed**.
- Machine-card golden tests: **2 passed** after generating and visually inspecting the light/dark baselines.
- Tests cover location fallbacks, consent, refresh, request coalescing, account changes, navigation, and English/Hindi/Marathi at 320px width and 200% text scale.
- `flutter build apk --debug --no-pub`: **passed**. Artifact: `mobile/build/app/outputs/flutter-apk/app-debug.apk`.
- `flutter analyze --no-pub`: **66 pre-existing informational findings**, exit 1; no new warnings/errors in the final analysis. Build output also retains existing Java/Kotlin plugin warnings.

Reviewable card renders: `mobile/test/components/goldens/machine_card_light.png` and `machine_card_dark.png`. These use test fixture data, not production readings.

Physical-device/live-API visual verification remains outstanding. Stop here for user verification before starting any further implementation phase.

## Phase 1 re-verification — 31 October 2026

**Revised conclusion:** the card presentation is implemented and tested, but full functional acceptance still has the limitations below. The earlier completion statement did not establish a working confirmed-state contract with live backend data.

Reproduced with failing regression tests, then fixed:

1. Refreshing a moved machine during an outstanding address request reused the old lookup. Request coalescing now includes the coordinate snapshot, so late old responses cannot replace the refreshed result.
2. Riverpod retained the old address during refresh/error. The card now displays coordinates until a fresh successful address is available.
3. Map/geocoding consent was cached without account identity. Consent now belongs to a user and organization, resets on account changes, rejects stale asynchronous grants, and blocks geocoding while the next account's consent loads. Existing unscoped consent requires opting in again through the existing map screen; no consent is granted automatically.

Outstanding gaps / limits:

| Item | Evidence and implication | Next action |
|---|---|---|
| Confirmed fence-state data | `MachineService.toDto` copies machine lifecycle status; assignments set `ACTIVE` / `IN_STOCK`. `CommandService.transitionCommand` updates command records, and the inspected result flow does not populate `FENCING_ON` / `FENCING_OFF` on machines. These states in card tests are fixtures, not proof of the live contract. Real assigned machines can correctly display **State unknown**. | Design an authoritative, ACK-backed fence-state contract separately. Do not infer OFF from connectivity, lifecycle status or ACC ignition. Backend changes remain outside this UI-only phase. |
| Fleet completeness (pre-existing) | `machineListProvider` requests one page and discards pagination metadata; the heading counts downloaded machines. Lazy rendering does not implement pagination. | Separate pagination/total-count work for fleets larger than one page. |
| Live and physical-device acceptance | Automated tests use controlled API data. The reference image is visually matched in structure/colors, not validated pixel-for-pixel on a physical phone. | Check a real account with GPS/address data, no-location/offline states, consent, long text and navigation on the target phone. |

Latest verification supersedes the earlier test counts: **321 non-golden mobile tests passed**, plus **2 machine-card golden comparisons passed** against unchanged baselines. The debug APK was rebuilt successfully at the same path. Analysis retains **66 pre-existing informational findings**; no new findings remain from this recheck.

No further implementation phase has started. Phase 1 should not be described as fully signed off for live confirmed-state behavior until the contract gap and user verification are addressed.