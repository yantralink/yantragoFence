# YantraGO Mobile — Shared UI Design System and Implementation Prompts

## Document Status

- **Status:** Planning only.
- **Visual reference:** `docs/UI.svg`.
- **Scope:** Flutter mobile app presentation, shared theme, reusable components, and UI verification.
- **Implementation:** Not started.
- **Execution rule:** Implement one phase at a time. After completing and verifying each phase, STOP and ask the user whether to start the next phase.
- **Approval:** Saving or reviewing this document does not authorize implementation.

---

## 1. Objective

Apply the visual language of `docs/UI.svg` consistently across the YantraGO mobile application.

The implementation must use:

1. One centralized theme.
2. Shared design tokens.
3. A small reusable presentation-component library.
4. Feature-specific widgets composed from shared components.
5. Consistent loading, error, empty, unavailable, stale, and pending states.
6. Responsive layouts and accessible colors.
7. Existing business logic and navigation behavior.

Do not redesign every screen independently. Do not duplicate colors, card styles, buttons, typography, or spacing across features.

The goal is a production-ready design system, not a screenshot copied into one screen.

---

## 2. Visual Reference Analysis

The SVG depicts a Machine Details screen with:

- Very light slate background.
- White, bordered cards.
- Dark slate titles and values.
- Muted supporting text.
- Blue navigation links.
- Green healthy-state indicators.
- Amber warnings.
- Green ON and red OFF actions.
- Rounded corners.
- Minimal elevation.
- A vertical card layout with a two-column metric grid.
- System sans-serif typography.

### 2.1 Preserve these characteristics

| Element | Target treatment |
|---------|------------------|
| Screen background | Light slate |
| Cards | White with subtle borders |
| App bars | Flat, background-colored, left-aligned titles |
| Primary text | Dark slate |
| Supporting text | Muted slate |
| General primary actions | Blue |
| Machine ON action | Accessible green |
| Machine OFF action | Accessible red |
| Healthy status | Green plus text/icon |
| Warning status | Amber plus text/icon |
| Unknown/offline status | Neutral slate plus text/icon |
| Decoration | Restrained; no heavy shadows or gradients |

### 2.2 Do not copy these literally

- The rounded outer phone frame.
- The fixed SVG canvas dimensions.
- The smallest 10px labels.
- Sample machine names, addresses, readings, owners, or expiry dates.
- A healthy status without confirmed data.
- A zero fault count when fault data is unavailable.
- Amber expiry styling without a real severity policy.
- Emoji-style icons.
- Fixed-height cards that clip large text.

The reference defines a visual language, not a complete specification for every screen.

---

## 3. Recommended Design Tokens

### 3.1 Light palette

| Role | Value | Usage |
|------|-------|-------|
| Canvas | `#F8FAFC` | Screen background |
| Surface | `#FFFFFF` | Cards and sheets |
| Primary text | `#0F172A` | Titles and main values |
| Body text | `#334155` | Metadata and descriptions |
| Secondary text | `#64748B` | Supporting text |
| Decorative muted | `#94A3B8` | Nonessential decoration |
| Subtle border | `#E2E8F0` | Card boundaries |
| Divider | `#F1F5F9` | Decorative internal separators |
| Primary action | `#2563EB` | Links and general actions |
| Success foreground | `#15803D` | Healthy labels and ON buttons |
| Warning foreground | `#B45309` | Warning labels |
| Danger foreground | `#DC2626` | Errors and OFF buttons |
| Neutral foreground | `#64748B` | Unknown/offline states |

Define any pale status backgrounds centrally and test each foreground/background pairing.

### 3.2 Accessibility corrections

Calculated contrast against white:

| Color | Contrast | Decision |
|-------|----------|----------|
| `#94A3B8` | 2.56:1 | Not suitable for essential small text |
| `#16A34A` | 3.30:1 | Darken for small text and white-label buttons |
| `#D97706` | 3.19:1 | Darken for small warning text |
| `#64748B` | 4.76:1 | Suitable for normal text on white |
| `#15803D` | 5.02:1 | Recommended green |
| `#B45309` | 5.02:1 | Recommended amber |
| `#DC2626` | 4.83:1 | Suitable for white-label OFF buttons |
| `#2563EB` | 5.17:1 | Suitable for white-label primary buttons |

Requirements:

- Target at least 4.5:1 for normal text.
- Test against the actual background.
- Use text and icons in addition to color.
- Validate focus indicators and essential control boundaries separately.
- Pale card borders are decorative, not the sole indicator of interactivity.

### 3.3 Typography

Use system fonts initially. Do not introduce a font dependency merely to match the SVG.

| Role | Suggested size | Weight |
|------|----------------|--------|
| Screen title | 20 | Semibold/bold |
| Card title | 16–18 | Semibold |
| Metric value | 22 | Bold |
| Body text | 14–16 | Regular |
| Metadata value | 14 | Medium |
| Section label | 12 | Semibold |
| Supporting status | 12–14 | Medium |
| Button label | 14–16 | Semibold |

Rules:

- Centralize styles through `TextTheme`.
- Respect platform text scaling.
- Never shrink text to force a screenshot match.
- Allow long addresses, identifiers, organization names, and translated text.
- Avoid uppercase for long descriptions.
- Consider tabular figures for changing readings if supported by the font.

### 3.4 Spacing and shape

Recommended spacing scale:

`4, 8, 12, 16, 24, 32`

Defaults:

| Property | Value |
|----------|-------|
| Phone page padding | 16 |
| Main card padding | 16 |
| Compact tile padding | 12–16 |
| Card gap | 16 |
| Grid gap | 12 |
| Main card radius | 16 |
| Metric card radius | 14 |
| Button/input radius | 12 |
| Button minimum height | 50 |
| Minimum interactive target | 48 × 48 |

Use content-driven heights.

Metric grids must switch to one column when width or text scaling makes two columns unreadable.

Do not constrain full-screen maps to the same narrow content width as forms.

### 3.5 Dark mode

Preserve the application's existing system-theme behavior.

Provide a deliberate dark counterpart rather than forcing light mode or mechanically inverting colors.

Suggested starting direction:

| Role | Starting value |
|------|----------------|
| Canvas | `#0F172A` |
| Surface | `#1E293B` |
| Primary text | `#F8FAFC` |
| Secondary text | `#CBD5E1` |
| Borders | `#475569` |

Choose accessible blue/status foregrounds and matching button foregrounds for dark surfaces.

These are proposed extensions, not colors extracted from the SVG. Validate all combinations.

Do not add a theme-selection screen unless separately requested.

---

## 4. Existing Codebase Integration

Recheck all paths and behavior against the latest branch before implementation.

### 4.1 Existing theme

- `mobile/lib/core/config/theme.dart`
- `mobile/lib/app.dart`

Evolve the existing `AppTheme`. Do not create a competing theme entry point.

### 4.2 Existing reusable feature widgets

Reuse and adapt:

- `MachineCard`
- `LocationCard`
- `OnOffButton`
- `CommandStatusWidget`
- Battery widget
- Voltage widget
- GSM widget
- Recharge widget
- Machine summary widget

### 4.3 Important boundaries

- Location UI already handles reverse-geocoding and coordinate fallback.
- Map navigation must remain functional.
- Machine commands must preserve existing provider calls and payloads.
- Command success must remain dependent on acknowledgement.
- Unknown telemetry must not be displayed as healthy.
- Several existing screens are not exposed by the inspected router.
- The inspected widget test is a generated counter test that does not match the real app entry point.

Do not expose unused routes or add missing backend functionality as part of UI styling.

---

## 5. Target Shared Architecture

### 5.1 Suggested structure

```text
mobile/lib/
  core/
    config/
      theme.dart
    theme/
      app_palette.dart
      app_spacing.dart
      app_radii.dart
      app_semantic_colors.dart
    widgets/
      app_page_body.dart
      app_surface_card.dart
      app_section_header.dart
      app_metadata_row.dart
      app_status_badge.dart
      app_metric_card.dart
      app_action_button.dart
      app_state_panel.dart

  features/
    machines/
      widgets/
        machine_card.dart
        location_card.dart
        on_off_button.dart
        machine_info_card.dart
        machine_telemetry_grid.dart
    dashboard/
      widgets/
        existing feature widgets
    commands/
      widgets/
        command_status_widget.dart
```

These are proposed targets, not instructions to create every file immediately.

Create components only when they have a real caller or demonstrated shared need.

### 5.2 Layer responsibilities

#### Theme layer

Owns:

- Colors.
- Typography.
- Shapes.
- Standard Material control themes.
- Semantic status appearance.

#### Shared presentation widgets

Own:

- Reusable layout.
- Visual composition.
- Accessibility behavior.
- Presentation states.

Accept:

- Display values.
- Semantic appearance.
- Loading/disabled state.
- Callbacks.

Must not import:

- API clients.
- Feature providers.
- Authentication services.
- Machine repositories.

#### Feature widgets

Own:

- Reading feature state.
- Feature-specific formatting.
- Mapping domain status to visual tone.
- Passing callbacks and values into shared components.

Example:

```text
Machine provider
  -> Machine telemetry feature widget
    -> AppMetricCard
      -> Theme tokens
```

### 5.3 Prefer native Material themes

Use Flutter themes for:

- App bars.
- Buttons.
- Inputs.
- Cards.
- Navigation bars.
- List tiles.
- Dialogs.
- Bottom sheets.
- Snackbars.
- Chips.
- Dividers.

Create a wrapper only when it adds genuinely shared composition or behavior.

### 5.4 Semantic status tones

Recommended presentation tones:

- Neutral.
- Information.
- Success.
- Warning.
- Danger.

Feature logic chooses the tone.

Examples:

| Domain state | Presentation |
|--------------|--------------|
| Low battery | Warning |
| Failed command | Danger |
| Pending command | Information |
| Unknown telemetry | Neutral |
| Confirmed healthy reading | Success |

A generic widget must not decide hardware thresholds.

“Not failed” must never automatically mean “successful.”

---

## 6. Shared Component Contracts

| Component | Responsibility | Required considerations |
|-----------|----------------|-------------------------|
| `AppPageBody` | Padding and optional width constraints | Safe areas, keyboard, scrolling |
| `AppSurfaceCard` | Surface, border, radius | Static/tappable/disabled |
| `AppSectionHeader` | Title and optional action | Long titles, accessibility |
| `AppMetadataRow` | Label/value layout | Wrapping, missing values |
| `AppStatusBadge` | Icon/dot and status text | Semantic tones |
| `AppMetricCard` | Label, value, unit, status, freshness | Loading, unavailable, stale, error |
| `AppActionButton` | Action variants and busy state | Focus, disabled, loading |
| `AppStatePanel` | Shared loading/error/empty presentation | Retry and compact variants |

Do not build a generic component with many unrelated boolean switches.

Prefer a small explicit API and composition.

---

## 7. Screen Migration Scope

| Screen/group | Target treatment |
|--------------|------------------|
| Splash | Slate canvas, restrained branding, themed progress |
| Login | White form surface, shared inputs, blue primary action |
| Machine list | Bordered cards and semantic state |
| Machine details | Closest implementation of the SVG layout |
| Machine location map | Themed chrome; preserve map engine |
| General map | Consistent surrounding UI; preserve behavior |
| Dashboard | Shared summary and metric presentation |
| Alerts | Clear list/card hierarchy and severity |
| Profile | Identity section and grouped metadata |
| Settings | Themed grouped controls |
| Activity history | Command rows with accurate status |
| Future notifications | Same cards, badges, controls, and state patterns |

Do not implement notification infrastructure in this UI migration.

---

# 8. Common Instructions for Every Phase

Paste this block before the selected phase prompt.

```text
Work only on the requested YantraGO mobile UI phase.

Follow AGENTS.md. Read docs/UI.svg and inspect the latest code before editing.
Preserve existing user changes.

Use the SVG as a visual reference, not as a fixed-size screen or a source
of production data.

Use the existing Flutter Material 3, Riverpod, Dio, and GoRouter stack.
Verify installed SDK/dependency compatibility before selecting APIs.

Centralize application-wide colors, typography, spacing, radii and control
styling. Use theme-aware semantic tokens.

Reuse existing components first. Shared visual components must not call APIs
or depend on feature providers. Keep business logic in its current layer.

Do not change backend APIs, TCP protocols, tenant isolation, authentication,
command payloads or command acknowledgement semantics.

Do not add unrequested screens, navigation destinations or dependencies.
If a dependency is genuinely needed, verify compatibility and choose a stable
release published at least seven days earlier.

Do not add or remove comments.
Keep changed functions and files focused and within project size standards
where applicable.

Handle loading, error, empty, unavailable, stale, disabled and pending states
where relevant. Never display fabricated readings or raw exceptions.

Use context.push for drill-down and context.go for tab switches.

Support light/dark themes, accessibility, text scaling, safe areas and keyboard
insets.

Add focused tests and run affected verification. Do not bypass failing checks.

Summarize changes and actual verification results.
Then STOP and ask whether to start the next phase.

Do not commit, deploy or proceed automatically.
```

---

# 9. Phase 1 — Theme and Test Foundation

## Goal

Establish the shared design tokens and evolve the existing theme before changing feature layouts.

## Implementation Prompt

```text
Implement only UI Phase 1: the shared theme foundation.

1. Inspect:
   - mobile/lib/core/config/theme.dart
   - mobile/lib/app.dart
   - mobile/lib/main.dart
   - mobile/lib/routing/app_router.dart
   - existing reusable widgets
   - mobile/pubspec.yaml and the lockfile
   - mobile tests

2. Identify existing theme constants and all relevant callers.
   Evolve AppTheme rather than introducing another theme entry point.

3. Introduce centralized palette, spacing, radius and semantic-color
   definitions where they provide shared value.

4. Use ColorScheme for standard Material roles.
   Use a ThemeExtension for success, warning, neutral and domain action
   appearance where standard roles are insufficient.

5. Apply the SVG-inspired light palette and an intentional dark counterpart.
   Preserve existing system theme behavior.

6. Configure shared themes for:
   - Text
   - App bars
   - Cards
   - Filled, outlined and text buttons
   - Inputs
   - Navigation bars
   - List tiles
   - Dialogs and sheets
   - Snackbars
   - Chips and dividers

7. Make app bars flat, background-colored and left-aligned.
   Preserve back-button and navigation behavior.

8. Do not add a theme-selection screen or a custom font dependency.

9. Repair the stale generated counter test to exercise the real application
   structure using controlled providers.
   Tests must not contact production APIs, maps or sockets.

10. Add tests for token values, semantic-theme availability and selected
    foreground/background contrast pairs.

Do not redesign feature layouts in this phase.

Report global appearance changes caused by the new theme and any
SDK compatibility issues.
```

## Acceptance Criteria

- One theme entry point.
- Both brightness modes are supported.
- Existing screens still load and navigate.
- Theme APIs match the installed SDK.
- The test harness no longer assumes a counter app.
- Focused tests and affected checks pass.

**STOP and request Phase 2 approval.**

---

# 10. Phase 2 — Shared Presentation Components

## Goal

Create reusable components before migrating screens.

## Implementation Prompt

```text
Implement only UI Phase 2: shared presentation components.

1. Inspect existing widgets before adding abstractions.

2. Implement the smallest useful versions of:
   - AppSurfaceCard
   - AppMetadataRow
   - AppSectionHeader
   - AppStatusBadge
   - AppMetricCard
   - AppActionButton
   - AppStatePanel

3. Add a page-body padding/constraint helper only where it avoids real
   duplication. Do not create another navigation scaffold.

4. Source appearance from the theme and shared tokens.
   Do not embed repeated hex colors, radii or text styles in components.

5. Keep shared widgets presentation-only.
   Accept values, semantic tones, states and callbacks.

6. Preserve Material ink, focus, accessibility semantics, disabled behavior
   and minimum touch targets.

7. Make metadata rows wrap or stack when necessary.
   Allow metric cards to grow with content and text scaling.

8. Distinguish:
   - Loading
   - Available
   - Unavailable
   - Stale
   - Error

   Unavailable is not zero.
   Stale is not confirmed healthy.

9. Add a test-only component gallery for repeatable visual checks.
   Do not add a production gallery route.

10. Add widget and golden tests for:
    - Light and dark themes
    - Long labels and values
    - 200% text scaling
    - Narrow width
    - Busy/disabled actions
    - All status tones
    - Unavailable/stale metrics

Do not migrate all feature screens yet.
```

## Acceptance Criteria

- Shared widgets have no network or feature-provider dependency.
- Components have small, explicit APIs.
- No overflow in narrow layouts.
- Buttons retain proper semantics and touch targets.
- Tests cover visual and interaction states.

**STOP and request Phase 3 approval.**

---

# 11. Phase 3 — Machine Details Reference Screen

## Goal

Use Machine Details as the visual approval gate before migrating the rest of the app.

## Implementation Prompt

```text
Implement only UI Phase 3: Machine Details visual migration.

1. Read:
   - machine_detail_page.dart
   - LocationCard
   - OnOffButton
   - CommandStatusWidget
   - relevant machine, location, telemetry and command models/providers

2. Compose the page using:
   - Background-colored app bar
   - Machine information card
   - Location card with a separated blue map action
   - Responsive metric tiles where supported by real data
   - ON/OFF actions
   - Explicit command progress and status

3. Extract feature-specific MachineInfoCard and MachineTelemetryGrid only
   where useful. Build them from shared presentation components.

4. Preserve available machine fields.
   Do not fabricate company, owner, fault count, battery, voltage or SIM expiry.

5. Reuse valid data from existing providers.
   If a visual requires missing backend functionality, show a truthful
   unavailable state or omit the unsupported tile and report the dependency.

6. Preserve location behavior:
   - Reverse-geocoding loading state
   - Coordinates fallback
   - Missing-location state
   - In-app map navigation

7. Keep command payloads and provider interactions intact.
   ON/OFF button colors describe actions, not proof of current state.

8. Preserve pending, acknowledged and failed distinctions.
   Prevent accidental duplicate submissions without inventing optimistic
   success or changing the command lifecycle.

9. Keep the page scrollable and responsive.
   Stack metric tiles or action buttons when width/text scaling requires it.

10. Add widget and visual tests for:
    - Available data
    - Missing and stale readings
    - Long identifiers and addresses
    - Command pending/failure
    - Map and back navigation
    - Light/dark themes
    - Large text

Use accessibility-adjusted colors and typography rather than copying the
SVG's smallest labels exactly.
```

## Acceptance Criteria

- The screen clearly resembles the SVG’s visual language.
- No sample values enter production UI.
- Map behavior is preserved.
- Command success is not shown before acknowledgement.
- User approves the reference screen.

**STOP and request Phase 4 approval.**

---

# 12. Phase 4 — Machine List, Dashboard and Metrics

## Goal

Demonstrate reuse across machine overview and detail screens.

## Implementation Prompt

```text
Implement only UI Phase 4: machine list and dashboard presentation.

1. Migrate MachineCard to shared card, typography and status patterns.

2. Preserve list providers, authorization, refresh behavior and detail routes.

3. Standardize loading, error, empty and retry presentation.

4. Refactor existing battery, voltage, GSM and recharge widgets to compose
   AppMetricCard instead of defining independent card styles.

5. Preserve valid feature-specific formatting and domain status semantics.

6. Distinguish battery charging/recharge state from SIM validity.
   Do not relabel an unrelated boolean as a SIM expiry date.

7. Update the existing dashboard using shared layout patterns.
   Do not add a route, backend capability or placeholder production data.

8. Keep large lists lazily built.
   Avoid unnecessary rebuilds of unrelated expensive content during metric
   updates.

9. Test:
   - Machine card navigation
   - Pull-to-refresh
   - Empty/error lists
   - Null metrics
   - Long labels
   - Dark mode
   - Responsive metric layout

Do not create separate, duplicated DashboardMetricCard and MachineMetricCard
visual implementations.
```

## Acceptance Criteria

- Shared metrics are reused across applicable screens.
- Unknown values remain neutral and truthful.
- Lists remain efficient.
- Existing route exposure and navigation are unchanged.

**STOP and request Phase 5 approval.**

---

# 13. Phase 5 — Authentication, Profile and Navigation

## Goal

Extend the design system to forms and account screens.

## Implementation Prompt

```text
Implement only UI Phase 5: splash, login, profile and navigation styling.

1. Restyle splash and login using shared canvas, typography, form surfaces,
   inputs, buttons and feedback.

2. Preserve:
   - Email/phone login
   - Validation
   - Secure storage
   - Authentication listeners
   - Redirects
   - Loading/pending behavior

3. Make login usable with:
   - Keyboard open
   - Narrow screens
   - Large text
   - Long validation messages

4. Migrate profile identity and metadata sections to shared components.

5. Apply NavigationBarTheme and common app-bar styling to the existing shell.
   Preserve Machines, Alerts and Profile destinations.

6. Avoid double padding, duplicate app bars and nested navigation bars.

7. Preserve logout behavior.
   Use clear labeling rather than presenting logout as a machine fault.

8. Test:
   - Validation presentation
   - Busy login
   - Password visibility interaction
   - Missing profile fields
   - Logout
   - Tab switching
   - Detail/back navigation
   - Light/dark themes

Do not add authentication features, profile editing or navigation tabs.
```

## Acceptance Criteria

- Forms share the same visual language as machine screens.
- Keyboard and large text do not hide required controls.
- Authentication and token handling are unchanged.
- Navigation remains correct.

**STOP and request Phase 6 approval.**

---

# 14. Phase 6 — Alerts, Settings and Activity History

## Goal

Standardize list-heavy and status-heavy screens.

## Implementation Prompt

```text
Implement only UI Phase 6: alerts, settings and history styling.

1. Apply shared cards/list styling, typography, badges and async-state
   presentation to the existing alerts screen.

2. Preserve current API capabilities.
   Do not implement notification delivery, read tracking or acknowledgement
   behavior unless those capabilities already exist.

3. Migrate settings controls to themed grouped sections.
   Do not implement unfinished settings actions or new backend calls.

4. Migrate activity history to readable command rows with consistent
   timestamps and explicit status badges.

5. Do not color every non-failed command green.
   Pending, queued and sent states must remain distinct from confirmed success.

6. Preserve current notification preference behavior.
   Do not claim an existing local toggle controls future FCM delivery.

7. Ensure future notification screens can compose the shared components.
   If notification screens do not exist yet, do not create placeholder
   production screens to demonstrate reuse.

8. Test:
   - Loading/error/empty lists
   - Unknown severities
   - Long alert messages
   - Timestamp formatting and layout
   - Pending/confirmed/failed commands
   - Large text
   - Dark mode

Do not introduce routes for currently unexposed screens.
```

## Acceptance Criteria

- Existing screens in scope use shared styles.
- No notification backend feature is introduced.
- Status presentation remains truthful.
- User-visible errors are friendly.

**STOP and request Phase 7 approval.**

---

# 15. Phase 7 — Maps and Remaining UI Surfaces

## Goal

Complete the migration without changing map functionality.

## Implementation Prompt

```text
Implement only UI Phase 7: map chrome and remaining presentation surfaces.

1. Inspect both:
   - The WebView-based machine location map
   - The general map screen

2. Preserve map engines, coordinates, marker assets, interactions, attribution
   and provider configuration.

3. Theme surrounding app-owned UI:
   - App bars
   - Address panels
   - Buttons
   - Loading/error states
   - Sheets and overlays

4. Do not place interactive maps inside unsuitable nested scrolling layouts.

5. Do not apply CSS color inversion or claim third-party map tiles are
   automatically dark-mode compliant.
   Theme app chrome and report map-style limitations separately.

6. Audit remaining:
   - Dialogs
   - Bottom sheets
   - Snackbars
   - Command feedback
   - Route-error screens
   - Custom controls

7. Replace duplicated presentation values with shared tokens where
   appropriate. Preserve legitimate map/content-specific values.

8. Add tests with platform-view fakes where necessary.
   Perform device/emulator smoke checks for actual map behavior.

Do not replace a map package, change a provider or request new services.
```

## Acceptance Criteria

- Map functionality is preserved.
- Attribution remains visible.
- App-owned UI is consistent in light/dark modes.
- Remaining feedback surfaces use shared styling.

**STOP and request Phase 8 approval.**

---

# 16. Phase 8 — Cross-App Verification

## Goal

Verify one consistent design system across the app.

## Implementation Prompt

```text
Implement only UI Phase 8: final verification and scoped UI corrections.

1. Review every routed screen.
   Test existing unrouted screen widgets directly without adding routes.

2. Run the full mobile analyzer and test suite.
   Build an Android debug APK.

3. Compare screenshots against:
   - The approved Machine Details reference
   - Approved shared-component baselines

4. Test:
   - Narrow phones
   - Typical phones
   - Large phones/tablets
   - Landscape
   - Keyboard-visible forms
   - 100%, 150% and 200% text scaling

5. Verify:
   - Light/dark themes
   - Contrast
   - Touch targets
   - Focus behavior
   - Screen-reader labels
   - Long content
   - Color-independent status recognition

6. Recheck:
   - Navigation
   - Refresh
   - Loading/error/empty states
   - Missing/stale telemetry
   - Command pending/ACK/failure
   - Account switching

7. Audit repeated color literals, radii, spacing and typography outside the
   design-system layer.
   Review legitimate exceptions manually; do not blindly replace all numbers.

8. Confirm shared components have real callers.
   Do not leave an unused component library or a parallel legacy theme.

9. Record actual test/build results and unresolved platform limitations.

10. Present a final screen checklist and STOP for user verification.

Do not deploy or begin unrelated feature work.
```

## Acceptance Criteria

- All migrated screens share the approved design language.
- No unapproved behavior changes.
- No fabricated values.
- No text clipping or inaccessible required actions.
- Tests, analysis and build pass, or blockers are explicitly reported.
- Final user verification is complete.

---

## 17. Verification Commands

Run from `mobile/` during implementation:

```powershell
flutter analyze
flutter test
flutter build apk --debug
```

Format changed Dart files with the project's formatter.

Use the installed SDK and current repository verification instructions.

Do not claim these commands passed unless they were actually run successfully.

### 17.1 Test coverage

- Theme token tests.
- Semantic color tests.
- Contrast checks.
- Shared-component widget tests.
- Golden tests.
- Navigation tests.
- Authentication presentation regression tests.
- Command-state presentation regression tests.
- Null/stale/error/empty-state tests.
- Large-text and narrow-layout tests.

### 17.2 Golden test stability

Use controlled:

- Fonts.
- Viewport dimensions.
- Dates and times.
- Provider data.
- Locale.
- Platform views.
- Network images.

Do not automatically approve changed golden baselines without visual review.

Use deterministic substitutes for map/platform-view screenshots and real
device checks for map interaction.

### 17.3 Suggested verification widths

| Width | Purpose |
|-------|---------|
| 320 | Narrow phone |
| 360–390 | Typical phone |
| 430 | Large phone |
| 600–800 | Tablet/resizable layout |

These are test sizes, not fixed application dimensions.

---

## 18. Non-Goals

This UI migration does not authorize:

- Backend endpoint changes.
- Database migrations.
- TCP protocol changes.
- New command types.
- New command success rules.
- New notification delivery infrastructure.
- New Firebase integration.
- New navigation destinations.
- Map-engine replacement.
- New authentication functionality.
- New business thresholds.
- Paid external services.
- Production deployment.

Report missing capabilities as separate dependencies.

---

## 19. Future-Screen Reuse Rule

Every future mobile screen should:

1. Use the existing AppTheme.
2. Use semantic theme colors instead of local hex values.
3. Use shared spacing and radius tokens.
4. Prefer themed Material controls.
5. Compose existing shared cards, status badges and state panels.
6. Keep feature providers out of generic visual widgets.
7. Handle all relevant asynchronous and unavailable states.
8. Preserve navigation and authorization requirements.
9. Include widget tests.
10. Pass accessibility and responsive-layout checks.

Create a new shared component only after checking whether an existing one can
meet the requirement without becoming overly complex.

---

## 20. Final Definition of Done

- [x] SVG-inspired slate/white/blue styling is consistent.
- [x] Accessible green/amber variants are used where necessary.
- [x] One centralized theme controls the app.
- [x] Shared components replace repeated visual structures.
- [x] Generic components do not contain feature business logic.
- [x] Authentication behavior is preserved.
- [x] Map behavior is preserved.
- [x] Command success still requires acknowledgement.
- [x] Missing data is never represented as healthy data.
- [x] Light and dark modes are usable.
- [x] Large text and narrow screens do not overflow.
- [x] Navigation behavior and route exposure are unchanged.
- [x] Loading/error/empty/stale states are consistent.
- [x] Tests and analyzer pass (excluding pre-existing stomp_dart_client issue).
- [x] Android build succeeds (debug APK built: 163 MB).
- [x] Platform-specific limitations are documented in the review.
- [x] Each phase has user approval before the next starts.
- [x] Final user verification is complete.

---

## 21. Phase Tracker

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Theme and test foundation | Complete |
| 2 | Shared presentation components | Complete |
| 3 | Machine Details reference screen | Complete |
| 4 | Machine list, dashboard and metrics | Complete |
| 5 | Authentication, profile and navigation | Complete |
| 6 | Alerts, settings and history | Complete |
| 7 | Maps and remaining surfaces | Complete |
| 8 | Cross-app verification | Complete |

**Recommended sequence:** Complete the theme and shared components first.
Use Machine Details as the visual approval gate before migrating other screens.

**Current status:** All 8 phases implemented. 121 widget tests pass. The only
analyzer errors are the pre-existing `stomp_dart_client` 0.4.4 API mismatch in
`lib/core/network/websocket_client.dart`, which was not modified during the UI
migration and predates Phase 2. No raw `Colors.*`, raw `Card(`, or
`Center(child: CircularProgressIndicator)` patterns remain in
`mobile/lib/features/`.