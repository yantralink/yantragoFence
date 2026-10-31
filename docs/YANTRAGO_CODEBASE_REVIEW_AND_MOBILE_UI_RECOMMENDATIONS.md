# YantraGO — Codebase Review and Mobile UI Recommendations

**Review date:** 30 October 2026  
**Baseline:** commit `116628c`, local working tree.  
**Deliverable:** analysis and recommendations only; no application changes or deployment.

## 1. Assessment and scope

YantraGO has a substantial implementation: tenant-aware administration, device ingestion, remote fence control, alerts, push notifications, analytics, multilingual mobile screens, and scheduled ON/OFF operation. Keep the two-service architecture and existing Flutter design system. The highest-value next work is to make command outcomes and background processing dependable, close remaining access-control gaps, and make the mobile app communicate machine state more clearly.

This review inventories all major modules and traces representative workflows across controllers, services, repositories, RabbitMQ, gateway adapters, clients, migrations, tests, and CI. It is not an exhaustive line-by-line audit. Source findings below distinguish reproducible checks from consequences inferred from code. No production system, physical device, external delivery provider, or live database was exercised. Mobile recommendations come from widgets, routes, providers, and tests, not observation of the current app on a phone.

The older root `CODEBASE_AUDIT_AND_IMPLEMENTATION_PLAN.md` describes a September baseline. Several findings have since been addressed: command routing now resolves the bound device; ON/OFF packets have protocol-specific implementations; ACK handling and attempt rows exist; machine access uses a centralized policy; mobile refresh saves both rotated tokens; real FCM delivery and notification outbox processing exist. Do not treat that older audit's entire blocker list as current. This document also complements the existing design guidance in `docs/YANTRAGO_Mobile_UI.md`; it does not reset completed UI work.

**Priority:** P0 = address before expanding automated physical control; P1 = next reliability/security/release work; P2 = subsequent usability, performance, and maintenance work. **Evidence:** confirmed source behavior is not proof of an observed production incident. Proposed product improvements are explicitly recommendations.

## 2. Implemented functionality

“Implemented” here means a connected source path exists, not that every failure mode or deployment setting is verified.

| Area | Present in the codebase | Qualification / next improvement |
|---|---|---|
| Architecture | Java 17 / Spring Boot 3.2.5 API and separate TCP gateway, shared Java RabbitMQ contracts, PostgreSQL/PostGIS, Redis | Preserve service boundaries. Actual TCP servers use `ServerSocket` and executors; do not describe this as a verified Netty deployment. |
| Authentication and authorization | BCrypt login, JWT roles/permissions, refresh rotation/replay handling, session revocation, tenant context, rate limits, CORS, audit interception | Central machine policy is a strong improvement; collection endpoints still need the same assignment scope (R1). |
| Organization administration | Organizations, admins/users, customers, machine inventory, devices, organization/customer assignment, settings, audit APIs | Verify reassignment, inactive accounts, and customer-scoped lists in integration tests. |
| Machine monitoring | Machine list/detail, latest device state, battery/voltage/GSM/ignition, online detection and WebSocket telemetry | Preserve unknown values; improve per-reading freshness and fleet pagination (R8, R11). |
| Location and protection | GPS ingestion, current/history APIs, map and reverse geocoding, circular geofences, breach incidents, customer theft-protection settings | Protection depends on usable GPS and device reporting. Make its coverage and stale-location limits visible. |
| Manual control | Command API, server-owned device binding, RabbitMQ dispatch, Concox/fencing encoders, ACK/DONE results, attempt records, history | Correlation, publication durability, deadline recovery, and active mobile progress are incomplete (R2–R5). |
| Scheduled control | Per-machine times/days/timezone, enable switch, next-run display, run history, slot claims, bounded retries, missed-ON handling, OFF catch-up, execution switch | Valuable functionality; retry ownership and command/run atomicity need strengthening (R6). |
| Alerts | Rules, canonical incident lifecycle, acknowledgement/resolution, offline/expiry detection, telemetry/battery/movement/geofence events | Keep operational incident state separate from notification read state. |
| Notifications | User inbox, unread count, preferences, localized templates, event outbox, deduplication, FCM jobs/tokens, retries, DLQ replay and metrics | Real push implementation exists; processing can still lose work after a partial failure (R7). |
| Analytics and reports | Telemetry series, battery health, fault/activity timelines, report aggregation and PDF/CSV export services | Label inferred activity/charge estimates, bound expensive ranges, and replace static admin charts (R12–R13). |
| Mobile app | Flutter/Riverpod/Dio/GoRouter, secure token storage, shared components, light/dark themes, English/Hindi/Marathi, push integration | Existing component and widget tests are useful. Improve workflow clarity rather than replacing the stack. |
| Account/privacy | Privacy links, deletion-request flow, user erasure during customer removal, token cleanup and map consent | Distinguish requesting deletion from completed erasure; verify operational handling and retention policies. |
| Recharge records | Tenant-scoped SIM/device recharge records | This is record keeping, not demonstrated payment processing or carrier top-up. Input contracts need improvement (R12). |
| Admin web | Next.js 14.2.35, organization and platform screens, machines/customers, alerts, rules, geofences, commands | Some pages remain display scaffolding; authentication contracts and reports need attention (R10, R13). |
| Delivery and operations | Flyway V1–V56, partition scripts, systemd/SCP production workflow, Docker alternatives, monitoring, security/SBOM workflows, protocol simulator | CI and load-test definitions have concrete defects; live backups, recovery and capacity are unverified (R9, section 5). |

Primary implementation locations: `backend/src/main/java/com/yantrago/api/{controller,service,security,queue,websocket}`, `device-gateway/src/main/java/com/yantrago/gateway/{service,tcp,queue}`, `shared/src/main/java/com/yantrago/shared/queue`, `mobile/lib/features`, `admin-web/src/app`, and `database/migrations`.

## 3. Verification performed

| Check | Result on this baseline | What it establishes |
|---|---|---|
| `./gradlew :backend:test :device-gateway:test :shared:build :simulator:compileJava --console=plain` | Passed: **662 backend tests + 106 gateway tests**, no failures/skips in XML reports | Existing Java tests pass; shared builds and simulator compiles. Some compilation tasks were up-to-date. Shared has no test sources. |
| Flutter 3.47.2 / Dart 3.13.2: `flutter test --no-pub --exclude-tags golden --reporter expanded` | **294 tests passed** | Existing mobile unit/widget suite passes; golden tests excluded. |
| `flutter analyze --no-pub` | Exit 1: **66 informational lint findings** | Analyzer gate is not clean. These are not 66 compilation errors. |
| Admin: `npm run type-check`, `npm run lint`, `npm run build` | All passed; Next.js generated 21 static pages | Current TypeScript, lint and production build pass with the existing local build environment. |
| Backend CI command with `--dry-run` | Failed: task `:backend` not found | Workflow command defect reproduced without running the workflow (R9). |

Gradle also reported deprecation/JVM warnings. No new tests were added for this documentation task. Existing passing tests do not prove durable queue delivery, SQL correctness against real PostGIS, physical command behavior, push reception, or application capacity. No actual database/broker integration suite was found among the inspected test annotations/dependencies; current coverage is primarily unit and MVC slices. No APK/AAB/iOS build or live visual/accessibility test was performed.

## 4. Prioritized engineering recommendations

### R1 — P1: Scope command-history collections to the customer's machines

**Confirmed:** `CommandService.listCommands()` queries only `findByOrganizationId(...)`. Individual commands and machine-filtered lists use `MachineAccessPolicy`, but the unfiltered list does not. `CommandController` accepts `command:read`, which V18 grants to customers, and mobile history calls this unfiltered endpoint. This permits same-organization command metadata to be listed outside the customer's assigned machines; it is distinct from cross-tenant access.

**Recommendation:** filter customer collections in the repository by both tenant and current assignment, using server-resolved identity. Preserve organization-wide history for authorized organization staff. Apply the same collection-versus-detail comparison to other sensitive APIs. **Acceptance:** two customers in one tenant cannot list each other's commands; a second tenant remains isolated; pagination totals reflect authorized rows. **Effort:** small–medium.

**Evidence:** `backend/.../service/CommandService.java` (`listCommands`, `listCommandsByMachine`), `backend/.../controller/CommandController.java`, `backend/.../repository/CommandRepository.java`, `database/migrations/V18__add_customer_role.sql`, `mobile/lib/features/commands/providers/command_provider.dart`. In this section, `backend/.../` expands to `backend/src/main/java/com/yantrago/api/`.

### R2 — P0: Make command-to-ACK correlation safe

**Confirmed:** gateway `PendingCommandRegistry` stores only IMEI → command ID and overwrites an earlier command. `CommandDispatchService` writes to the socket before registering the command. `CommandResultService` removes the mapping by IMEI and publishes ACK then DONE. Consequently, a late reply can be attributed to a newer command, and a very fast reply can arrive before registration. Gateway restart also loses the pending mapping.

**Recommendation:** establish an in-flight record before sending, serialize commands per device, and correlate with protocol sequence/connection generation where supported. If firmware lacks a correlation field, explicitly handle late replies and ambiguous outcomes; serialization alone does not make an old reply identifiable. Add expiry and attempt identity to the shared contract, reject expired/replayed dispatches, and explicitly reject unsupported command protocols instead of falling through to Concox. Keep copied parser algorithms unchanged; use adapters around them.

**Acceptance:** ON then OFF, late/duplicate/early ACK, reconnect, restart and wrong-protocol cases cannot confirm the wrong request. An uncertain outcome stays uncertain. **Effort:** large. **Evidence:** `device-gateway/src/main/java/com/yantrago/gateway/service/{PendingCommandRegistry,CommandDispatchService,CommandResultService}.java`, `shared/src/main/java/com/yantrago/shared/queue/CommandMessage.java`. JT808 uses a separate connection registry; do not assume its ingestion support proves fence-control support.

### R3 — P1: Persist command publication intent and deadlines

**Confirmed:** `CommandService.dispatch()` saves a command and publishes in `afterCommit`. This fixes visibility-before-commit, but a crash or broker failure after commit can leave a persisted command without delivery. `CommandProducer` does not durably track publication completion. Gateway `CommandConsumer` catches dispatch exceptions without rethrowing. Scheduled runs have reconciliation; no equivalent general recovery sweep was found for manual commands.

**Recommendation:** commit a command dispatch outbox record with the command, publish with confirms/returns, and recover unpublished work after restart. Reuse the existing outbox approach with command-specific routing and idempotent consumption. Persist deadlines for manual and scheduled commands; quarantine invalid messages and retry transient infrastructure failures with bounded policy. Never blindly retry a physical action whose outcome is unknown.

**Acceptance:** broker outage, process death after commit, socket exception and lost ACK leave either recoverable intent or an explicit audited unresolved/terminal outcome. No command can remain silently pending forever. **Effort:** large. **Evidence:** `backend/.../service/CommandService.java`, `backend/.../queue/CommandProducer.java`, `device-gateway/src/main/java/com/yantrago/gateway/queue/CommandConsumer.java`, `backend/.../service/ScheduleExecutionService.java`.

### R4 — P1: Process command results atomically and idempotently

**Confirmed:** `CommandResultConsumer` invokes transactional transition and attempt recording as separate calls, then performs notification/schedule work separately. `CommandStateMachine` rejects repeated transitions, including DONE → DONE. A crash between these steps can leave state advanced without the corresponding attempt/event; redelivery can then fail before repairing the missing work.

**Recommendation:** one service transaction should lock/version the command, recognize duplicate and stale results, upsert the attempt, update schedule linkage when relevant, and write required outbox events. Broadcast only after commit. Give results a stable event/attempt identity. **Acceptance:** repeated SENT/ACK/DONE, out-of-order delivery, and crash between writes converge to one consistent audit history and notification intent. **Effort:** medium–large. **Evidence:** `backend/.../queue/CommandResultConsumer.java`, `backend/.../service/{CommandService,CommandStateMachine}.java`.

### R5 — P1: Connect mobile command progress to the active screen

**Confirmed:** `commandSocketProvider` is defined but has no consuming reference elsewhere in `mobile/lib`. `OnOffButton` disables actions only while the HTTP request runs, shows an awaiting-ACK snackbar, and then re-enables both buttons. The command provider tracks one global last command. This permits another request while the first is unresolved and leaves the detail screen without a persistent command lifecycle.

**Recommendation:** observe command state per machine and command ID; mount the command subscription in the appropriate screen/session, and reconcile via the status API on reconnect/resume. Show “Sending → Waiting for device → Confirmed / Failed / Not confirmed.” Keep controls constrained while unresolved, with an explicitly designed OFF override policy coordinated with R2. Clear account-scoped command state on logout/account change.

**Acceptance:** navigating away/back, two machines, socket loss, app backgrounding and double taps retain truthful progress without optimistic machine-state changes. Add a widget integration test that actually mounts the subscription, rather than only testing its controller. **Effort:** medium. **Evidence:** `mobile/lib/features/machines/widgets/on_off_button.dart`, `mobile/lib/features/machines/pages/machine_detail_page.dart`, `mobile/lib/features/commands/providers/{command_provider,command_socket_provider}.dart`.

### R6 — P1: Strengthen scheduled-run recovery and retry ownership

**Confirmed:** initial schedule slots use a unique claim, which is good. However, command dispatch commits/publishes before `processSlot()` saves the command ID to its run. The retry sweep reads candidates without a row claim/lease and dispatches before updating the run. A crash can separate the command from its run; multiple backend instances can select the same retry. Initial slot uniqueness does not protect these later operations.

**Recommendation:** atomically link the run, command and publication intent; claim retry work with a lease or conditional update; preserve the chosen schedule version/slot semantics. Recheck enabled/ownership state under the agreed policy. Preserve bounded late-ON handling and the documented pending-OFF behavior. Failure copy should say “State not confirmed” when a command may have executed but its ACK was lost; current “UNCHANGED” wording is too certain for that case.

**Acceptance:** crash before/after dispatch and two simultaneous schedulers produce one intended attempt; disabling/editing a schedule and late ACKs do not cause stale actuation. **Effort:** large. **Evidence:** `backend/.../service/{ScheduleExecutionService,ScheduleCommandScheduler}.java`, `backend/.../repository/ScheduleRunRepository.java`, `database/migrations/V55__machine_schedules.sql`.

### R7 — P1: Make notification processing durable through partial failures

**Confirmed:** `NotificationEventConsumer` inserts `processed_events` before recipient/inbox work without an encompassing transaction. It catches per-recipient and push-enqueue failures. Redelivery then sees “already processed” even if the inbox or push job was never created. WebSocket notification also runs before push enqueue, so a broadcast exception can prevent the durable push job.

**Recommendation:** commit deduplication, inbox rows and delivery jobs together, or use durable per-recipient work states. Treat transient failures as retryable; distinguish intentional suppression from incomplete work. Move socket invalidation after commit. Keep the existing outbox confirms, unique inbox key, recipient revalidation, encrypted tokens and real FCM provider.

**Acceptance:** DB failure after dedup insert, broadcast failure and push-job insertion failure all recover without losing recipients or duplicating inbox items. **Effort:** medium–large. **Evidence:** `backend/.../queue/NotificationEventConsumer.java` (`handleAlertTransition`, `createInboxItem`), `backend/.../queue/OutboxPublisher.java`, `database/migrations/V30__notification_inbox_unique_key.sql`.

### R8 — P1: Prevent silent ingestion loss and stale state regressions

**Confirmed:** `TelemetryConsumer` and `LocationConsumer` catch broad processing failures. `LocationPersistenceService` drains its in-memory batch before the database insert and only logs an insert failure. A crash can lose buffered, already-consumed history. Telemetry “batches” contain one reading per message, and latest device updates do not compare measurement time before overwriting. Delayed messages can therefore regress latest state.

**Recommendation:** acknowledge only after a durable write/staging step; batch durable records, retain failed batches safely, and use bounded retry/DLQ policies. Define event identities to make redelivery safe. Update latest state only with acceptable newer observations, while retaining valid older observations in history. Batch telemetry across messages where measured volume justifies it.

**Acceptance:** DB outage/restart/redelivery produces no silent loss or duplicate logical readings; an older observation cannot replace a newer state. **Effort:** large. **Evidence:** `backend/.../queue/{TelemetryConsumer,LocationConsumer}.java`, `backend/.../service/LocationPersistenceService.java`.

### R9 — P1: Repair CI gates and make release/load evidence meaningful

**Reproduced:** backend CI runs `./gradlew :backend :shared :device-gateway :simulator build`, which fails because module paths are not task paths. Gateway CI has the same pattern. Backend path filters omit `database/**`, even though migrations are build inputs. Mobile CI's analyze command encounters the current 66 lint findings.

**Confirmed source:** load testing selects `*-SNAPSHOT.jar` while simulator version is `1.0.0`, and `|| true` masks failure. Production deployment rebuilds JARs with `-x test` and has no test-job dependency in that workflow; repository branch/environment protections were not inspected.

**Recommendation:** use explicit tasks such as `:backend:build :shared:build :device-gateway:build :simulator:build`; include migration/wrapper inputs in CI triggers; resolve analyzer findings without indiscriminately disabling lints. Promote tested artifacts and serialize deployments. Fix simulator artifact selection, distinguish expected timeout from failure, and assert throughput/error/latency thresholds. **Acceptance:** a migration-only PR runs relevant checks; failed tests/load startup fail the gate; the deployed digest is traceable to passing checks. **Effort:** small–medium. **Evidence:** `.github/workflows/{backend-ci,gateway-ci,mobile-ci,load-test,deploy-production}.yml`, `simulator/build.gradle.kts`.

### R10 — P1: Finish client session failure handling

**Confirmed:** mobile refresh now serializes concurrent work and saves both tokens, but any refresh exception clears secure storage, including a network outage. Its refresh `Dio()` lacks the normal client's explicit timeouts. Admin refresh is not serialized; logout omits the backend-required refresh-token body; the default admin base URL lacks `/api/v1` although requests use `/auth/*` and `/machines`. Deployment configuration may supply the missing prefix; the default mismatch is confirmed.

**Recommendation:** preserve sessions on temporary connectivity errors, set refresh timeouts, synchronize account state on actual invalidation, and test rotation/account-switch races. Normalize the web base-path contract, serialize refresh, revoke on logout, and clear account-scoped query caches. Review browser token storage/XSS exposure as a separate hardening decision; do not simply move tokens to insecure cookies.

**Acceptance:** offline resume keeps the session recoverable; concurrent 401s and two refresh cycles work; logout revokes the server token; a second user never sees cached first-user data. **Effort:** medium. **Evidence:** `mobile/lib/core/network/interceptors/auth_interceptor.dart`, `admin-web/src/lib/{api-client,auth}.ts`, `backend/.../controller/AuthController.java`.

### R11 — P1/P2: Complete fleet pagination and reading freshness

**Confirmed:** mobile `machineListProvider` and admin `useMachines()` retain only page content, without fetching subsequent pages or preserving totals. Mobile renders all returned cards in a `Column`. Its telemetry socket merges old field values into incoming partial frames and refreshes one common timestamp/received time; a GPS-only frame can make an old battery value appear newly received. Existing five-minute freshness checks are useful but do not establish per-field age.

**Recommendation:** implement authorized server pagination/search/filter/sort and lazy rendering; distinguish loaded count from total. Track field observation times or measurement groups, show stale labels, refresh REST on reconnect, and use a lightweight clock for age-driven UI updates. **Acceptance:** fleets larger than one page remain discoverable; GPS-only updates do not refresh battery freshness; a silent socket becomes visibly stale. **Effort:** medium. **Evidence:** `mobile/lib/features/machines/providers/{machine_provider,telemetry_socket_provider}.dart`, `mobile/lib/features/machines/{pages/machine_list_page,widgets/machine_telemetry_grid}.dart`, `admin-web/src/hooks/use-machines.ts`.

### R12 — P2: Tighten API contracts, bounds and error semantics

**Confirmed:** recharge creation parses an unconstrained `Map`, returns an entity and does not validate positive amounts in the inspected service. Analytics endpoints accept from/to without a common ordering/range bound. `GlobalExceptionHandler` maps generic “not found” `IllegalArgumentException`s to 400, making missing resources and malformed requests difficult to distinguish.

**Recommendation:** typed request/response DTOs, Bean Validation, business checks and suitable non-destructive schema constraints; bounded history/analytics/report ranges and aggregation for large windows; domain exceptions for 404/409/422 as appropriate within a documented API policy. Use UTC instants for event boundaries and explicit schedule timezones. **Acceptance:** invalid amounts, missing fields, inverted/oversized ranges and missing resources produce stable documented responses. **Effort:** medium. **Evidence:** `backend/.../controller/{RechargeController,AnalyticsController}.java`, `backend/.../service/RechargeService.java`, `backend/.../config/GlobalExceptionHandler.java`.

### R13 — P2: Make reporting and delivery capabilities honest

**Confirmed:** the organization admin Reports page charts a fixed January–June array and displays export text without an export action. The legacy notification service disables PUSH/SMS and calls email delivery with a null recipient. A separate real FCM pipeline exists; these are different paths. `SmsService` itself still returns a generated mock ID, but the inspected legacy dispatcher does not call it.

**Recommendation:** connect charts/downloads to authorized reports or show an explicit unavailable state. Inventory legacy notification callers and deprecate or complete that path; never advertise SMS as delivered without provider evidence. Label external battery percentage as a voltage-based estimate and analytics sessions by their evidence source: DONE commands versus ACC observations. Do not equate a command-derived interval with continuously measured fence operation.

**Acceptance:** displayed totals match API data; empty/error states remain distinct; exported rows are complete; UI/help accurately identify estimates and enabled channels. **Effort:** medium. **Evidence:** `admin-web/src/app/(admin)/reports/page.tsx`, `backend/.../service/{NotificationService,SmsService,AnalyticsService}.java`, `mobile/lib/features/dashboard/widgets/external_battery_mapper.dart`.

## 5. Architecture, database, operations and maintenance

| Recommendation | Why it matters / evidence | Verification before treating it as complete |
|---|---|---|
| Keep two application services | Backend and gateway already depend on shared contracts. More microservices would not solve the transaction gaps above. | Shared-contract compatibility tests and an end-to-end simulator scenario. |
| Document supported protocol capabilities | JT808 ingestion uses its own registry; default command encoding falls back to Concox. | A protocol × hardware × firmware capability matrix, with golden packets and controlled ACK tests. Preserve reused parsing code per AGENTS.md. |
| Plan gateway/API scaling explicitly | Socket ownership is process-local; competing command consumers do not route to the gateway owning a device. Backend uses a process-local simple STOMP broker. | Before adding instances, prove device-owner routing and cross-instance realtime fanout within the existing architecture. |
| Measure before replacing TCP transport | Servers use fixed thread pools and blocking sockets. Thread/memory/backpressure limits need measurement. | Idle connections, login floods, reconnect storms, partial frames and shutdown under staging load; change transport only with documented need. |
| Operationalize partition maintenance | V6/V7 create partitions through December 2027 plus defaults; `database/partitions/create_monthly_partitions.sql` requires scheduling outside the migration flow. | Monitor future-partition coverage/default-partition rows, verify the actual job, test partition creation when default rows overlap, and verify indexes. Do not claim imminent partition failure. |
| Define retention and prove restoration | Archive scripts and deployment instructions are not proof of tested backups. Audits, telemetry, processed events and outbox data have different retention needs. | Restore drill, recovery-time/data-loss objectives, documented retention and explicit approval for destructive DDL. |
| Add real infrastructure integration tests | Unit/MVC tests pass but cannot validate Flyway/PostGIS, RabbitMQ redelivery or transaction boundaries. | PostgreSQL 15/PostGIS + RabbitMQ + Redis fixtures for R1–R8, fresh install and upgrade through V56. |
| Improve operational signals | Existing Prometheus rules emphasize uptime, memory and HTTP latency; notification metrics already exist. | Dashboards/alerts for command age, ACK latency, retry/timeout counts, outbox age, DLQ depth, ingestion lag, stale devices and missed schedule runs. |
| Verify network enforcement | `SecurityConfig` permits metrics endpoints and relies on infrastructure restrictions. | Test actual public ingress/firewall behavior and production profile handling; source alone cannot prove external exposure. |
| Reduce maintenance concentration | Examples exceed the project's 300-line guidance: schedule execution, machine service, map page, web alert-rule/geofence pages. | Split by responsibility while working on those features, with existing tests retained. Do not refactor copied protocol parsers merely to meet a line target. |
| Keep one current feature register | Older audits/plans coexist with newer implementations and stale comments. | Record current feature state, owner, tests, configuration prerequisites and remaining gaps; link historical plans rather than duplicating conflicting checklists. |

## 6. Mobile UI direction

### Preserve the foundation

Keep the existing four destinations: **Machines, Alerts, Analytics, Profile**. Keep Material 3, `AppPalette`, `AppSemanticColors`, spacing/radius tokens, `AppSurfaceCard`, `AppMetricCard`, `AppStatusBadge`, `AppStatePanel` and existing light/dark themes. Localization and large-text widget tests already exist. A wholesale visual rewrite would spend effort without fixing the main usability problems.

Aim for an outdoor operational tool: readable values, obvious machine identity, trustworthy freshness, short action labels, and a clear response after a command. Blue should indicate general interaction; green/amber/red should communicate a defined status or action, always accompanied by text/icon. Do not let “online,” “fence ON,” “machine running/ACC,” “charging,” and “healthy” share one ambiguous status.

### Recommended screen changes

| Screen | Current source-backed observation | Recommended experience |
|---|---|---|
| Machines | Cards show identity, status, last seen; no search/filter and only the first API page is retained. | Add search by name/machine ID, compact All / Needs attention / Offline filters, authorized totals and pagination. Put faults/offline machines first when that sort is selected. Keep the one-machine experience simple; do not add a redundant dashboard tab. |
| Machine detail | Metadata and six telemetry tiles precede ON/OFF. At large text the grid stacks, pushing actions lower despite the page comment's initial-viewport goal. | First show machine identity, confirmed fence state, connectivity and freshness. Follow with the control panel and pending result. Then show urgent faults, next schedule action, key readings, location and expandable technical details. |
| Control panel | Equal ON/OFF actions plus a short snackbar; pending state is not observed by the widget. | Persistent machine-specific action/progress panel. State “Last confirmed OFF · 2 min ago” separately from “Turn ON requested.” For energizing, use a concise machine-named confirmation if usability testing supports it. Do not introduce delayed or hidden OFF controls; define override behavior with R2. |
| Telemetry | Battery/fault/charging/voltage/GSM/ACC concepts occupy several tiles; some values are derived from hardware-specific semantics. | Group “Fence,” “Power,” and “Connection.” Emphasize actual field needs: fence fault, external power, device battery and signal. Explain estimated charge on tap, keep volts visible, and attach observation time/staleness to readings. Validate names against the hardware manual before relabeling. |
| Schedule setup | Times/days, overnight handling, next/last runs and history already exist. | Lead with “Automatic fence control.” Show an explicit timezone, plain-language preview (“Mon–Fri, ON 6:00 PM, OFF 6:00 AM next day”), enabled state and next action. Make “schedule saved” distinct from “fence changed.” Explain pending OFF behavior when disabling. |
| Alerts/inbox | The Alerts route currently contains notifications only; its former active-alert tab was removed. | Choose clear naming: “Notifications” for the inbox, or add an intentional “Needs attention” view for current incidents. Show severity, machine, event time and read state separately. Reading/mark-all-read must not imply acknowledgement or resolution. |
| Analytics | Machine selector, range chips, battery health, faults and activity timelines exist. | Begin with 2–3 useful summaries for the selected machine/range, followed by timelines. Show units, gaps and evidence labels; use “No readings” instead of zero. Add tap details and a textual alternative for charts. Avoid unsupported battery-life/runtime promises. |
| Location/theft protection | Map, location consent, address and protection settings exist, but some protection controls live under Profile. | Make per-machine protection discoverable from its detail/location screen. Show location age, boundary/radius, protection status, and a text address fallback. Preserve consent and explain why protection cannot be enabled without usable GPS. |
| Profile/login | Language, preferences, account details, command history and deletion/help-related flows exist. | Keep account-wide settings here. Add a machine-scoped activity/history entry from detail; distinguish it from all accessible commands. Keep language selection easy to find, password-manager/autofill support, and clear deletion-request status. |

These changes should compose existing components. Keep APIs in providers, use `context.push()` for detail navigation and `context.go()` for tab switches, and preserve authenticated deep links from push notifications.

### Suggested machine-detail hierarchy

```text
< Machines                 North Field
YG001 · Connected          Updated 20 sec ago

FENCE: OFF                 Last device confirmation
[ Turn ON ]  [ Turn OFF ]
Waiting for device...      Only while a request is unresolved

Needs attention            Shown only for actual issues
Automatic control          Next: ON today at 6:00 PM

Power                      Connection
12.4 V · charge estimate   Signal / last report

Location and protection    View map / protection status
Recent activity            Manual and scheduled commands
Machine information        Expand identifiers / SIM details
```

This is a proposed information hierarchy, not a screenshot or a guarantee that every item fits above the fold. Adapt controls to narrow screens and large text. A sticky control area is optional: test that it does not obscure content, keyboard, accessibility focus or bottom navigation. If freshness is unknown, replace the sample confirmation text with an honest unknown state.

### Shared interaction and accessibility requirements

| Concern | Concrete recommendation / acceptance criterion |
|---|---|
| Type and spacing | Retain the current scale; prefer 16sp for main reading text, 14sp for supporting text, and larger key values. Avoid fixed text heights. Use existing spacing tokens consistently rather than new per-screen numbers. |
| Touch and contrast | Minimum 48dp interactive targets; measure text contrast at 4.5:1 for normal text and 3:1 for large text, with appropriate non-text contrast. Test light/dark and outdoor readability; no color-only status. |
| Large text and languages | Test English/Hindi/Marathi at 320/360/412 logical-pixel widths and 100/150/200% text scale. Wrap long names/statuses, localize time/day summaries, and preserve meaningful screen-reader order. |
| Loading/empty/error | Reuse `AppStatePanel`; retain safe last-known data during refresh with a visible stale indicator. Offer Retry on detail failures. Distinguish no assignment, no reading, permission denied and connectivity failure. |
| Weak connectivity | Show connection/freshness status and manual refresh. Consider cached read-only snapshots with timestamps; never silently queue ON/OFF for later execution when the phone reconnects. |
| Command feedback | Announce meaningful progress/result changes to assistive technology. A toast/snackbar is supplementary; pending/unknown outcomes remain visible and retrievable. |
| Navigation and state | Preserve list scroll/filter and selected analytics machine where useful. Test back navigation, auth expiry, account switch, push links and app resume; use stateful tab branches only if needed. |
| Motion and performance | Prefer restrained transitions, respect reduced motion, lazy-build lists, and avoid continuous animations/polling when the app is backgrounded. |

## 7. Suggested product enhancements after reliability work

| Enhancement | User value | Dependency / priority |
|---|---|---|
| Machine activity feed | One place for manual commands, scheduled runs, faults and acknowledgement history | Correct event ordering/permissions first; P2. Reuse existing history rather than inventing another audit source. |
| Attention summary | Quickly find offline devices, faults, missed schedules and expiry issues | Accurate totals across all pages and a defined severity policy; P2. |
| Support diagnostics | Share a redacted diagnostic summary: app version, machine ID, last report and command reference | Explicit user action, no tokens or unnecessary location/customer data; P2. |
| Clear onboarding | Explain assignment, first device report, protection setup and notification permissions | Useful for current admin-assigned devices; P2. QR onboarding remains a separate planned feature, not demonstrated current mobile functionality. |
| Service/renewal reminders | Make SIM expiry and maintenance actions easier to understand | Reuse real expiry/recharge data; payment/carrier integration requires separate scope. |

## 8. Proposed implementation sequence

These are proposed phases, not implementation started by this review. Follow AGENTS.md: complete and verify one phase, stop for user verification, and explicitly ask before starting the next. Effort labels are relative complexity, not delivery estimates.

| Phase | Deliverable | Exit evidence |
|---|---|---|
| 1 — Access and trustworthy gates | R1 and R9: customer command-list scope, valid CI tasks/triggers, analyzer cleanup, honest load-job failures | Two-customer/two-tenant tests; CI command dry-run and green relevant checks; failed simulator startup fails the job. |
| 2 — Command and schedule reliability | R2–R4 and R6: safe correlation, durable dispatch, idempotent results, general deadlines, safe retry ownership | Broker/process failure injection, late ACK and duplicate delivery tests, multi-instance schedule tests, controlled device verification. |
| 3 — Ingestion and notification recovery | R7–R8 plus essential metrics/partition checks | Real DB/broker integration tests, restart/replay proof, alerts for stranded work and ingest lag. |
| 4 — Mobile control experience | R5, mobile part of R10, freshness part of R11, detail/control/schedule hierarchy | Mounted UI-to-provider tests for pending/ACK/timeout/resume; translated narrow/large-text screens; no optimistic success. |
| 5 — Fleet and supporting screens | Pagination/search, inbox semantics, analytics clarity, map/protection discoverability, web session/report fixes, R12–R13 | More-than-one-page scenarios; complete exports; account-switch isolation; accessibility and real-device usability checks. |
| 6 — Capacity and operations | Measured gateway/API limits, ownership/fanout design if scaling is needed, recovery/retention drills | Recorded staging load results with thresholds; restore evidence; documented supported deployment topology. |

Before UI implementation, validate the proposed detail hierarchy with a small set of actual users on their typical phones: can they identify the correct machine, tell whether data is current, request OFF, understand an unconfirmed result, and understand the next scheduled action without assistance? Measure task completion and errors before/after; choose performance budgets from observed target-device and network baselines.
