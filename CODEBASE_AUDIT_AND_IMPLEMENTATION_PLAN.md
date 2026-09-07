# Codebase Audit and Implementation Plan

## 1. Executive Summary

**Audit checkpoint: STATIC REVIEW COMPLETE. Runtime/build/load verification remains outstanding. BLOCK RELEASE.**

- Review date: 2026-09-06; baseline commit `9f7cf2b` plus existing working-tree changes. No saved audit output or completed checkpoint from the lost conversation was present. Findings are freshly inspected, not reconstructed memory.
- Scope: `docs/YANTRAGO_Review_Astra.md` and `AGENTS.md`. Only this report was created/modified. No application fixes, installations, migration execution, deployment, external-provider calls or production testing.
- **CONFIRMED:** directly supported by source/configuration. **LIKELY:** strongly indicated consequence needing execution. **UNKNOWN:** missing runtime, infrastructure or business evidence. A confirmed source defect is not a claim of a demonstrated live exploit.
- Coverage: backend authentication/authorization, CRUD, commands, queue consumers, time-series SQL, notifications/reports, gateway adapters and shared contracts, client auth/commands/routing/reports, CI/deployment/monitoring, simulator and test infrastructure. Copied binary parser algorithms were not re-audited for byte-level correctness. No exhaustive line-by-line guarantee, dependency CVE scan, physical-device test, accessibility certification or live-infrastructure inspection is implied.
- Implementation-plan checkboxes are not verification. No remediation phase is started or approved by this document.

The intended two-service architecture is worth keeping, but essential workflows are disconnected: permission checks are missing, commands use a serial number as IMEI and placeholder bytes, device ACKs are not correlated, telemetry inserts have null tenant IDs, and alert delivery includes placeholders. Client/backend contracts and production packaging also disagree. These are correctness/security blockers before capacity tuning.

The master findings table is in section 23; release-gate summary: **SEC-001/002, CMD-001/002, REL-002 and DEV-001 are P0**. Prioritize their containment with regression tests, followed by remaining P1 security, durability and deployment work. Quantified 20,000-user capacity remains UNKNOWN.

## 2. Repository Overview

| Area | Actual repository implementation | Verification boundary |
|---|---|---|
| Backend | Java 17, Spring Boot 3.2.5, REST, Spring Security, JPA/JDBC, AMQP, STOMP | Static review; no running API/DB |
| Gateway | Separate Spring Boot JVM; ServerSocket/executor TCP adapters, protocol handlers, Redis mapping, AMQP | Not a demonstrated Netty/NIO deployment |
| Shared | Java queue names and message DTO library | Both services depend on it |
| Mobile | Flutter, Riverpod, Dio, secure storage, STOMP | No build/device profiling |
| Admin | Next.js 14.2.5, React Query, Zustand, Axios, Leaflet/STOMP | No browser/build verification |
| Database | PostgreSQL/PostGIS schema, V1–V20 Flyway SQL, monthly/quarterly partitions | No migrations or EXPLAIN executed |
| Operations | Docker Compose and alternative Vultr/systemd scripts; GitHub Actions, Prometheus/Grafana | Live deployment model UNKNOWN |
| Simulator | Java Spring Boot application and per-protocol simulation classes | Not proof of executed traffic |

Evidence: `settings.gradle.kts:16-21`, `build.gradle.kts:1-4`, `backend/build.gradle.kts:10-54`, `admin-web/package.json:5-36`, `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5TcpServer.java:28-79`. Credentials and real environment values were not sought or reproduced. Declared dependencies are not claimed vulnerability-free. Existing changes in mobile Android/theme/map/dependencies, APK workflow and AGENTS.md were left intact.

## 3. Current Architecture

The API owns application business logic and canonical writes. The gateway owns TCP sockets and protocol adapters, publishes messages and uses DB/Redis device mapping. RabbitMQ connects the two; shared contracts prevent independent message definitions. API authentication establishes a UUID principal and request-thread tenant context; permission and customer-ownership enforcement is incomplete. Backend WebSocket delivery uses a process-local simple broker, not a cross-instance broker relay.

Keep two application services. The simulator, Redis, PostgreSQL and RabbitMQ are not reasons to split business logic into additional microservices. The principal defects are unfinished integration, transaction boundaries and mismatched contracts, not an intrinsically unsuitable language or database. See CMD-001/002, REL-002/003 and SCALE-001.

## 4. Architecture Diagram

```text
Flutter / Next.js
   | REST Bearer JWT                 | STOMP/WebSocket (currently mismatched)
   v                                 v
Backend API --------------------> in-process /topic broker
   | Security -> controllers -> services -> JPA/JDBC -> PostgreSQL + PostGIS
   |                          |                 |
   | command publish          | consumers       +-- partitioned history/audit
   v                          ^
RabbitMQ (shared/ contracts: command, result, telemetry, location, alert, device)
   | commands                 | telemetry / replies / events
   v                          |
TCP gateway ------------------+
   | device identity lookup -> Redis cache -> PostgreSQL
   | local socket registries + protocol adapters
   v
Concox / JT808 / fencing devices (or simulator)

CI -> Docker build/registry -> Compose staging file (production workflow)
Alternative operational path: Vultr/systemd deployment scripts
```

Arrows describe code paths, not evidence that each path succeeds. Push/SMS are mock implementations; SMTP receives an unresolved recipient in the current notification path.

## 5. End-to-End Data Flow

| Flow | Trace | Break / cost |
|---|---|---|
| Login/session | UI -> client -> `/api/v1/auth/login` -> BCrypt -> user/roles -> JWT + hashed refresh row -> client storage; refresh -> rotation | Shared onboarding password, non-atomic rotation, inactive account bypass on refresh; mobile fails to save rotated token (SEC-002/004, MOB-002) |
| Inventory/customer | UI -> controller role checks on platform writes -> service -> machines/devices/customers/assignment rows -> DTO | Tenant-sensitive endpoints lack permissions; MAX+1 identity race, unscoped customer mapping scans, per-machine DTO queries (SEC-001, DB-003, PERF-001) |
| Command | Mobile button -> Dio -> validated POST `/api/v1/commands` -> tenant guard -> PENDING row -> publish before commit -> gateway socket lookup/packet -> result consumer | Mobile enum mismatch; serial/IMEI confusion; placeholder bytes; no QUEUED transition, reply correlation or durable attempt history (MOB-001, CMD-001–003) |
| Telemetry/location | TCP -> shared message -> listener -> service -> JDBC/time-series -> broadcast | Missing trusted tenant/machine resolution, request ThreadLocal used in worker, null broadcast destination (REL-002) |
| Current/history reads | GET locations/telemetry -> machine tenant guard -> parameterized JDBC -> DTO | Current location passes null device ID; JDBC timestamp casts, unbounded history (API-001, PERF-001) |
| Alerts/notifications | Shared alert event -> two listeners on one queue -> alert or placeholder notification -> manual dispatch | Competing consumption is not fan-out; unresolved recipient; mock delivery; threshold service not invoked (REL-001) |
| Reports | UI -> controller/date parsing -> parameterized tenant aggregates -> in-memory JSON/PDF/CSV | Admin chart uses static data; missing bounds/permission checks, single-page/truncated PDF (WEB-002, API-002/003, PERF-001) |
| Recharge | UI/API -> Map parsing -> device tenant guard -> JPA recharge record | Missing validation/permissions. No carrier top-up or payment integration is implemented here (API-002) |

## 6. Mobile Application Audit

### MOB-001 [P1] — Mobile command DTOs do not match the API

**Area:** Mobile / API.
**Confidence:** CONFIRMED.
**Location:** `mobile/lib/features/machines/widgets/on_off_button.dart:26-47,55-65`; `mobile/lib/features/commands/providers/command_provider.dart:21-36`; `mobile/lib/models/command.dart:32-44`; `backend/src/main/java/com/yantrago/api/dto/command/CommandRequest.java:17-18`; `backend/src/main/java/com/yantrago/api/dto/command/CommandResponse.java`.
**Current Behavior:** Buttons send FENCING_ON/FENCING_OFF, while API validation allows ON/OFF. Response parsing requires an IMEI absent from CommandResponse; history expects a List rather than Spring Page.content.
**Problem / Evidence:** These are incompatible enum, field and envelope contracts. The backend can reject the request before dispatch; fixing only the enum leaves response parsing broken.
**Production Impact:** The mobile command screen cannot reliably issue/track commands or load history.
**Recommendation:** Match the existing validated command API; device routing remains server-owned.
**Implementation Approach:** Send ON/OFF and machine UUID, parse real response fields and pagination, model deviceId separately from IMEI (never substitute one for the other), track terminal status by command ID.
**Testing:** DTO fixtures from the API, multi-page history, request rejection, ACK/timeout and double-tap behavior. Preserve the useful waiting-for-ACK UX rather than reporting HTTP success as machine success.
**Risk of Change:** Medium.

### MOB-002 [P1] — Refresh loses the rotated token and races concurrent requests

**Area:** Mobile / Authentication.
**Confidence:** CONFIRMED.
**Location:** `mobile/lib/core/network/interceptors/auth_interceptor.dart:36-64`; `backend/src/main/java/com/yantrago/api/service/AuthService.java:134-152`.
**Current Behavior:** A fresh Dio instance refreshes on each 401, saves only accessToken, retries the original request and clears all storage on any caught failure.
**Problem / Evidence:** Backend returns a new one-time refresh token; the client keeps the revoked old token. No shared in-flight refresh coordination exists; transport policy differs from the normal client.
**Production Impact:** Subsequent refresh fails, parallel calls conflict, and transient network failures can force logout.
**Recommendation:** Single-flight refresh with atomic token-pair persistence and explicit invalid-session versus temporary-network handling.
**Implementation Approach:** Reuse a controlled refresh client with timeouts, serialize rotation, retry each request at most once, reconcile auth provider state and cancel account-scoped pending work on logout.
**Testing:** Two consecutive expiry cycles, concurrent 401s, refresh timeout, revoked session, offline resume, account switch and storage failure.
**Risk of Change:** Medium; coordinate with SEC-004.

Mobile uses secure-storage APIs rather than browser localStorage; keep that design. Offline durability, push registration/deep links, accessibility, large-map rendering, device compatibility and crash rates remain UNKNOWN. No claim of complete offline support or push delivery is justified by static UI code. TEST-001 covers the stale test and DEV-003 the inconsistent build toolchain.

## 7. Admin Web Application Audit

### WEB-001 [P1] — Browser session handling and default API configuration are inconsistent

**Area:** Admin / Authentication.
**Confidence:** CONFIRMED code mismatch; effective deployed base URL UNKNOWN.
**Location:** `admin-web/src/lib/auth.ts:12-34`; `admin-web/src/lib/api-client.ts:3-17,34-64`; `admin-web/next.config.js:4-13`; `backend/src/main/java/com/yantrago/api/controller/AuthController.java:26,47-50`.
**Current Behavior:** Client paths are `/auth/*` and `/machines`; the default base URL lacks `/api/v1`. Refresh concatenates that same base. Logout sends no body though the backend requires refreshToken, then clears browser storage regardless. Concurrent 401s rotate independently.
**Problem / Evidence:** Default-origin configuration targets the wrong API namespace; local logout need not revoke the server refresh row. Prefix-bearing environment overrides can fix routing but not the missing body or refresh race.
**Production Impact:** Environment-dependent login/API failures and server sessions surviving apparent logout.
**Recommendation:** One explicit API base-path convention, single-flight refresh, and server-confirmed revocation with honest offline logout behavior.
**Implementation Approach:** Normalize origin versus prefix once, add typed auth contracts, include refreshToken on logout, clear React Query/Zustand account data and coordinate concurrent requests. Audit cache keys across user/tenant changes.
**Testing:** Actual production-build URL, login/me/refresh/logout, two expiry cycles, concurrent requests, revoked token and account-switch cache isolation.
**Risk of Change:** Medium.

### WEB-002 [P2] — Reports show invented data and machine lists discard pagination

**Area:** Admin / UX.
**Confidence:** CONFIRMED.
**Location:** `admin-web/src/app/(admin)/reports/page.tsx:5-20`; `admin-web/src/hooks/use-machines.ts:10-17`; `admin-web/src/app/super-admin/layout.tsx:29-33`.
**Current Behavior:** The reports page charts a constant six-month array and only displays export text. The machine hook returns page.content without preserving total/page navigation metadata. The super-admin layout checks authentication rather than role.
**Problem / Evidence:** Fake operational data is not labeled as a demo; users cannot infer completeness from a first-page-only hook. UI navigation checks do not mirror platform authorization.
**Production Impact:** Misleading reporting and incomplete fleet views; inappropriate menus/failed operations for non-platform users. UI role gating is not a substitute for SEC-001 server authorization.
**Recommendation:** Use real reports or explicitly unavailable states, expose server pagination, and mirror effective role permissions in navigation.
**Implementation Approach:** Fetch bounded report data, implement actual export interaction after API fixes, propagate Page metadata and loading/error/empty states, add role-aware layouts.
**Testing:** Zero/multiple pages, totals, API error versus no data, export download, non-super route navigation and keyboard interactions.
**Risk of Change:** Low to Medium.

Browser token storage/CORS are covered in SEC-006; STOMP integration in SEC-003; build-time configuration in DEV-003. React Query/Zustand/Axios are suitable existing tools: consolidate contracts instead of replacing the client stack.

## 8. Backend / API Audit

### CMD-001 [P0] — Command routing uses the wrong identity and sends placeholder bytes

**Area:** Backend / Gateway / Machine safety.
**Confidence:** CONFIRMED code; physical effect UNKNOWN.
**Location:** `backend/src/main/java/com/yantrago/api/service/CommandService.java:71-99`; `backend/src/main/java/com/yantrago/api/service/MachineService.java:109-126`; `device-gateway/src/main/java/com/yantrago/gateway/service/CommandDispatchService.java:46-108`; `device-gateway/src/main/java/com/yantrago/gateway/tcp/jt808/JT808TcpServer.java:31-37`.
**Current Behavior:** CommandService treats machine.serialNumber as device IMEI, despite distinct stored fields. Dispatch sends identical fixed bytes for ON and OFF, without device protocol selection. JT808 uses a different socket registry from the generic dispatcher.
**Problem / Evidence:** Server-owned machine-to-device binding is bypassed; the packet does not encode the requested distinction. If serialNumber matches another connected IMEI, routing can target that identity rather than the bound device; this impact is conditional on actual data.
**Production Impact:** Unroutable/ineffective commands and potential wrong-device actuation. Do not test against live hardware without a controlled safety procedure.
**Recommendation:** Resolve and validate the bound active device/protocol under the authorized tenant; use verified existing encoders/adapters and correct registry.
**Implementation Approach:** Reject missing/ambiguous bindings, retain immutable dispatch identity, wire protocol-specific encoders without changing copied parsers, serialize writes and prove ON/OFF golden packets.
**Testing:** Serial different from IMEI, misleading serial equal to another device IMEI, inactive/unbound device, all supported protocols, no unauthorized packet emitted.
**Risk of Change:** High; device compatibility is a release gate.

### CMD-002 [P0] — Device ACK return path and command lifecycle are disconnected

**Area:** Backend / Gateway / Reliability.
**Confidence:** CONFIRMED.
**Location:** `device-gateway/src/main/java/com/yantrago/gateway/service/CommandResultService.java:42-70`; `device-gateway/src/main/java/com/yantrago/gateway/service/CommandDispatchService.java:76-85`; `backend/src/main/java/com/yantrago/api/service/CommandService.java:87-99`; `backend/src/main/java/com/yantrago/api/service/CommandStateMachine.java:37-44`; `backend/src/main/java/com/yantrago/api/queue/CommandResultConsumer.java:38-64`.
**Current Behavior:** Reply handler logs without publishing/correlating a command. Create persists PENDING and publishes; no production QUEUED transition was found. The gateway emits SENT, but PENDING -> SENT is rejected. Consumer catches the error. Broadcast also receives null machineId.
**Problem / Evidence:** Neither outbound state progression nor inbound acknowledgement reaches a complete validated lifecycle. Existing state-machine unit tests do not exercise this complete path.
**Production Impact:** Commands stay pending/fail without reliable physical-state confirmation; live status disappears.
**Recommendation:** Explicit persisted dispatch stages and protocol-aware reply correlation; HTTP enqueue/SENT must never imply success.
**Implementation Approach:** Correlate command/attempt with protocol sequence and connection epoch where available; if firmware lacks correlation, serialize per device and handle late ACK ambiguity explicitly. An IMEI -> latest-command map alone is unsafe. Persist results idempotently, then broadcast the real machine ID.
**Testing:** PENDING through terminal path, early/duplicate/out-of-order/late ACK, negative reply, reconnect, old ACK after newer command and rollback between publish/commit.
**Risk of Change:** High.

### CMD-003 [P1] — Attempt history and bounded command expiry/retry are missing

**Area:** Backend / Audit / Reliability.
**Confidence:** CONFIRMED.
**Location:** `backend/src/main/java/com/yantrago/api/service/CommandService.java:168-183`; `backend/src/main/java/com/yantrago/api/queue/CommandResultConsumer.java:45-51`; `backend/src/main/java/com/yantrago/api/service/CommandStateMachine.java:18-44`; `database/migrations/V8__create_commands.sql:26-35`.
**Current Behavior:** recordAttempt increments the parent counter but never writes CommandAttempt. Each result event can increment the counter rather than updating one attempt. maxAttempts is stored, with no connected timeout/retry scheduler found.
**Problem / Evidence:** Required per-attempt rows are absent; timeout is mentioned in comments but not implemented as a lifecycle behavior.
**Production Impact:** Unbounded pending commands and unverifiable retries/audits.
**Recommendation:** Persist one row per actual transmission attempt, update its status idempotently, define deadlines and safely bounded retry semantics.
**Implementation Approach:** Add atomic command/attempt updates and durable deadline recovery; never create a new attempt for every status event. Determine whether ON/OFF retry is safe for each firmware, enforce expiration before dispatch, and reject stale replays.
**Testing:** Unique attempt numbers, repeated status events, expired commands, restart recovery, retry cap and unknown physical outcome.
**Risk of Change:** High because retry policy can affect real equipment.

### API-001 [P1] — Current location cannot resolve the device; JDBC time mapping is unsafe

**Area:** Backend / API.
**Confidence:** CONFIRMED null lookup; LIKELY nonempty history/telemetry mapping failure until exercised with PostgreSQL.
**Location:** `backend/src/main/java/com/yantrago/api/service/LocationService.java:73-92,114-137`; `backend/src/main/java/com/yantrago/api/repository/LocationRepository.java:53-64`; `backend/src/main/java/com/yantrago/api/service/TelemetryService.java:60-79`.
**Current Behavior:** The service validates the machine, checks its serial number, then calls `findCurrentLocation(orgId, null)`. The SQL requires `device_id = ?`. History and telemetry cast generic JDBC map timestamp values directly to `LocalDateTime`.
**Problem:** SQL equality against NULL does not match the machine's device. Generic JDBC temporal values are not guaranteed to be `LocalDateTime`.
**Evidence:** Exact null argument at LocationService line 87; equality predicate at LocationRepository line 54; timestamp casts at LocationService lines 122/135 and TelemetryService lines 66/72/78. The test uses manually constructed LocalDateTime map entries rather than JDBC (`TelemetryServiceTest.java:89-105`).
**Production Impact:** Current-location API returns not found even with stored data; nonempty historical queries can fail conversion.
**Recommendation:** Resolve the bound device or query explicitly by tenant and machine; adopt explicit typed row mapping and a documented UTC/timezone contract.
**Implementation Approach:** Add a real PostgreSQL regression fixture, implement tenant-scoped binding lookup, replace generic map time casts with typed mapping, preserve no-data semantics.
**Testing:** Bound/unbound machines, another tenant's ID, actual TIMESTAMPTZ rows, timezone boundaries and nonempty telemetry.
**Risk of Change:** Medium.

### API-002 [P1] — Recharge/report request validation is incomplete

**Area:** Backend / API.
**Confidence:** CONFIRMED.
**Location:** `backend/src/main/java/com/yantrago/api/controller/RechargeController.java:51-62`; `backend/src/main/java/com/yantrago/api/service/RechargeService.java:63-83`; `database/migrations/V12__create_recharges.sql:4-15`; `backend/src/main/java/com/yantrago/api/controller/ReportController.java:41-58,86-94`; `backend/src/main/java/com/yantrago/api/config/GlobalExceptionHandler.java:27-59`.
**Current Behavior:** Recharge creation accepts a Map and manually dereferences amount/device fields without Bean Validation; neither service nor schema rejects negative amounts. Report export validates a nested filter only when it is present; it is nullable before dereference.
**Problem:** Invalid business data can be accepted; malformed/missing fields produce inconsistent 4xx/5xx responses. A `@Validated` class does not validate unconstrained map members.
**Evidence:** No amount constraint in request/service/DDL; no `@NotNull` on export filter. Generic handler returns 500 for unhandled parsing/null failures.
**Production Impact:** Incorrect recharge records and noisy client/server failures. There is no evidence that this endpoint moves money.
**Recommendation:** Typed validated request DTOs, appropriate numeric/currency/date constraints, and explicit domain-error mapping.
**Implementation Approach:** Define supported inputs, add DTO validation and matching database checks via a new migration after data audit, map missing resources/conflicts separately from malformed input.
**Testing:** Missing/null/wrong-type fields, zero/negative/oversized amounts, invalid currency/date order, no partial writes, stable status/error schema.
**Risk of Change:** Medium; reject formerly accepted invalid input deliberately and coordinate clients.

### API-003 [P2] — Report export can silently omit visible content

**Area:** Backend / UX.
**Confidence:** CONFIRMED implementation limits; rendering impact requires fixture verification.
**Location:** `backend/src/main/java/com/yantrago/api/service/ReportExportService.java:34-98,107-138`.
**Current Behavior:** PDF export allocates one page, emits all rows on it, truncates each textual row/header to 100 characters, and uses Standard14 Helvetica. CSV escapes delimiters but uses the platform-default byte encoding.
**Problem:** Long reports run below the page; values are truncated and Unicode rendering is unsupported by the chosen simple font for many characters. Spreadsheet formula-like values are not neutralized.
**Evidence:** Only one `PDPage` construction; no pagination inside the row loop; substring truncation at lines 73/83; raw CSV values at 124-129 and default `getBytes()` at 138. Recharge reports include stored provider/plan names (`ReportService.java:127-134`).
**Production Impact:** Incomplete/misleading exports and spreadsheet interpretation risk when stored text begins with formula syntax; this is not a demonstrated remote-code exploit.
**Recommendation:** Paginated/wrapped PDF output, compatible Unicode font, explicit UTF-8 CSV and documented spreadsheet-safe text encoding.
**Implementation Approach:** Establish deterministic column order, pagination and font fixtures; bound export size before considering asynchronous jobs.
**Testing:** Multipage output, long cells, international text, commas/quotes/newlines, formula-prefix text and large result sets.
**Risk of Change:** Low to Medium.

## 9. Database Audit

### DB-001 [P1] — Initial telemetry partitions lack the indexes expected by read paths

**Area:** Database / Performance.
**Confidence:** CONFIRMED schema omission; actual latency UNKNOWN.
**Location:** `database/migrations/V7__create_telemetry_tables.sql:7-151`; `database/migrations/V15__add_performance_indexes.sql:1-54`; `database/partitions/create_monthly_partitions.sql:36-55`; `backend/src/main/java/com/yantrago/api/repository/TelemetryRepository.java:45-66`.
**Current Behavior:** Initial voltage/battery/GSM partitions have primary keys on `(id, recorded_at)` but no machine/time query indexes. V15's comment says partition indexes already exist, but it does not create these. The maintenance function adds a machine/time index only when creating a new partition, not for an existing one.
**Problem:** Machine/tenant/time queries cannot use a purpose-built machine/time index on those initial partitions; invoking the maintenance function does not repair them.
**Evidence:** DDL plus existing-partition skip branch; queries filter organization, machine, and time and sort by time.
**Production Impact:** Likely large-partition scans and sorting at scale; no measured threshold or latency is claimed.
**Recommendation:** Add validated tenant/machine/time indexes through a new migration, choosing index order from real EXPLAIN plans.
**Implementation Approach:** Inventory deployed indexes, benchmark representative queries, stage per-partition/backfill indexes safely, and apply the same definition to future partitions.
**Testing:** Fresh and upgraded schemas, initial and future/default partitions; EXPLAIN ANALYZE with realistic row counts and write-overhead measurement.
**Risk of Change:** Medium due to index-build locks, disk and write cost.

### DB-002 [P1] — Machine deletion cascades away the command audit history

**Area:** Database / Reliability.
**Confidence:** CONFIRMED.
**Location:** `backend/src/main/java/com/yantrago/api/service/MachineService.java:169-176`; `database/migrations/V8__create_commands.sql:6-11,26-35`; `database/migrations/V12__create_recharges.sql:4-8`.
**Current Behavior:** Service deletion removes the associated device and machine. Machine-command rows reference machines with ON DELETE CASCADE; attempts cascade from commands; recharges cascade from devices.
**Problem:** An authorized operational delete also removes historical command evidence, contrary to the required durable command audit trail.
**Evidence:** Direct repository deletes and the foreign-key actions above.
**Production Impact:** Irrecoverable loss of audit/recharge history unless recoverable from backups; no deletion was performed in this audit.
**Recommendation:** Retire/soft-delete operational records and preserve historical identities; define a separately authorized retention policy.
**Implementation Approach:** Add retirement state and restrict ordinary deletion, migrate foreign-key policy forward, ensure list filters do not hide history from audit/reporting, review organization deletion too.
**Testing:** Commands and all attempts remain queryable after retirement; recharge history retained; authorization/tenant checks; upgrade and restore rehearsal.
**Risk of Change:** High for existing deletion contracts and historical-data migration.

### DB-003 [P1] — Inventory identity and assignment updates are not concurrency-safe

**Area:** Database / Correctness.
**Confidence:** CONFIRMED race-prone algorithm and missing schema guards; concurrent failure is LIKELY.
**Location:** `backend/src/main/java/com/yantrago/api/service/MachineService.java:197-231,255-263`; `backend/src/main/java/com/yantrago/api/repository/MachineRepository.java:25-26`; `database/migrations/V4__create_machines_and_devices.sql:24-49`; `database/migrations/V17__machine_inventory_changes.sql:9-10`.
**Current Behavior:** New machine labels use `MAX(machineId)+1`; customer assignment closes an existing row then inserts another without a visible lock/version check. Devices have no unique machine binding, and active assignments have no partial uniqueness constraint.
**Problem:** Concurrent creates can select the same label and fail the unique index; assignments/bindings can violate the single-record assumptions of Optional-returning repository queries.
**Evidence:** Read-then-write code and absence of these constraints in inspected migrations. The machine-label unique index is a useful last defense, not a concurrency allocator.
**Production Impact:** Intermittent create failures, ambiguous device routing and inconsistent assignment history.
**Recommendation:** Database sequence for human-readable labels (retain UUID PKs), explicit binding cardinality, transactional locking/versioning, active-assignment uniqueness.
**Implementation Approach:** Audit existing duplicates, define product cardinality, migrate constraints and sequence above existing maximum, add concurrency guards and translate conflicts.
**Testing:** Concurrent creates/assignments, retry behavior, duplicate existing-data migration and tenant-safe binding.
**Risk of Change:** Medium to High.

### DB-004 [P2] — Partition lifecycle is a helper script, not a verified operational process

**Area:** Database / DevOps.
**Confidence:** CONFIRMED repository gap; deployed scheduler UNKNOWN.
**Location:** `database/partitions/create_monthly_partitions.sql:9-61`; `database/partitions/archive_old_partitions.sql:15-105`; `database/migrations/V6__create_location_history_partitioned.sql:26-61`; `database/migrations/V11__create_audit_logs.sql:22-35`; `backend/build.gradle.kts:61-69`.
**Current Behavior:** Finite initial partitions plus default partitions exist. Helpers live outside the Flyway migration copy path; monthly helper excludes quarterly audit partitions. No invocation was found in searched Java/SQL/shell/YAML sources.
**Problem:** Deployment of helpers, scheduling, default-partition drainage, future indexes and archive restoration are not established by this repository. A default partition prevents an immediate date-rollover insert failure but can accumulate indefinitely.
**Evidence:** Migration copy includes only database/migrations; helper's explicit four-table list; maintenance script comments instruct an external schedule.
**Production Impact:** Retention/performance drift, partition attachment failures when default data overlaps, and unverified restore behavior.
**Recommendation:** Version and schedule lifecycle operations, observe partition runway/default row count, and rehearse archival and restoration on disposable data.
**Implementation Approach:** Inventory deployed jobs first; add safe idempotent maintenance and matching quarterly policy, validate catalog-based bounds, preserve detached data until retention approval.
**Testing:** Month/quarter/year transitions, late data in default partitions, archival cutoffs and restore. Never execute archival/deletion on production as part of this audit.
**Risk of Change:** High for retention operations; Medium for partition creation.

## 10. Security Audit

### SEC-001 [P0] — Sensitive APIs omit RBAC and customer resource ownership

**Area:** Security / Backend.
**Confidence:** CONFIRMED missing enforcement; role-specific runtime matrix not executed.
**Location:** `backend/src/main/java/com/yantrago/api/config/SecurityConfig.java:56-68`; `backend/src/main/java/com/yantrago/api/controller/CommandController.java:36-59`; `backend/src/main/java/com/yantrago/api/controller/CustomerController.java:31-72`; `backend/src/main/java/com/yantrago/api/controller/MachineController.java:85-98`; `backend/src/main/java/com/yantrago/api/security/PermissionEvaluator.java:33-57`; `backend/src/main/java/com/yantrago/api/service/CommandService.java:66-99`.
**Current Behavior:** Global policy requires login. Some platform writes require SUPER_ADMIN, but commands, customer reset, device/settings/recharge/report operations lack equivalent permissions. Command access validates organization, not the customer's assigned machine. PermissionEvaluator.hasPermission has no enforcement call sites found.
**Problem / Evidence:** Authentication and same-tenant membership are treated as permission to perform sensitive operations. A customer token can target another customer's machine in the same organization using a known ID.
**Production Impact:** Unauthorized within-tenant machine commands, reassignment, customer resets and sensitive reads. P0 reflects physical control and account-reset exposure, not every read endpoint individually.
**Recommendation:** Deny-by-default permission matrix and independent tenant/customer ownership checks at service boundaries.
**Implementation Approach:** Reuse existing roles/permissions rather than inventing roles; load effective permissions, enforce every operation, distinguish explicit platform privileges from a missing tenant context, and apply customer predicates in database queries.
**Testing:** Two tenants, two customers per tenant, platform/admin/customer and each existing restricted role; forbidden operations cause no DB/queue side effects. Test direct API requests, not just hidden UI controls.
**Risk of Change:** High; approve matrix and preserve intended platform inventory operations.

### SEC-002 [P0] — Customer onboarding/reset uses one shared password

**Area:** Security / Authentication.
**Confidence:** CONFIRMED source behavior; existing affected accounts UNKNOWN.
**Location:** `backend/src/main/java/com/yantrago/api/service/CustomerService.java:37,94-108,242-247`; `backend/src/main/java/com/yantrago/api/controller/CustomerController.java:68-71`.
**Current Behavior:** Customer phone is the login identifier; create and reset hash the same built-in password. No mandatory one-time activation or first-login replacement is connected to this path.
**Problem / Evidence:** A shared password is predictable across accounts; BCrypt protects the stored hash, not knowledge of the shared plaintext. The password value is deliberately omitted here.
**Production Impact:** Accounts still using the initial/reset credential are exposed to takeover; SEC-001 also allows unauthorized resets within a tenant.
**Recommendation:** Secure, expiring single-use enrollment/reset with verified recipient and administrative permission.
**Implementation Approach:** Stop shared-password issuance, inventory affected accounts without extracting credentials, require safe re-enrollment and revoke relevant sessions. Use real provider delivery only after REL-001; do not invent successful SMS delivery.
**Testing:** Token expiration/single use, wrong recipient, rate limits, random per-account enrollment, old credential rejection, session revocation and authorization.
**Risk of Change:** High for existing-user migration; coordinate support/onboarding.

### SEC-003 [P1] — WebSocket authentication is incompatible and subscriptions lack resource authorization

**Area:** Security / Real-time clients.
**Confidence:** CONFIRMED code gaps; reachability/exposure UNKNOWN pending transport tests.
**Location:** `backend/src/main/java/com/yantrago/api/config/WebSocketConfig.java:35-47`; `backend/src/main/java/com/yantrago/api/websocket/WebSocketAuthInterceptor.java:41-76`; `backend/src/main/java/com/yantrago/api/websocket/TrackingWebSocketController.java:41-58`; `admin-web/src/lib/websocket.ts:18-36`; `mobile/lib/core/network/websocket_client.dart:20-39`; `backend/src/main/java/com/yantrago/api/config/SecurityConfig.java:56-68`.
**Current Behavior:** Backend handshake requires a query token and uses SockJS. Clients send STOMP headers and raw broker URLs; normal HTTP security also requires Authorization before the handshake interceptor. No inbound SUBSCRIBE resource/tenant authorization is configured; handshake attributes alone are not a messaging authorization policy.
**Problem / Evidence:** Normal clients can be rejected before STOMP CONNECT. A client satisfying the HTTP/handshake requirements still encounters no topic ownership check. Query tokens may be captured by URL logging; actual leakage was not inspected.
**Production Impact:** Broken live updates; cross-tenant/customer topic access once connected with a known destination. Current broadcast gaps do not make missing authorization acceptable.
**Recommendation:** Design one browser-compatible authenticated transport and deny unauthorized CONNECT/SUBSCRIBE/SEND destinations.
**Implementation Approach:** Establish a verified principal through STOMP CONNECT or a short-lived handshake ticket; align SockJS/raw transport, restrict any HTTP handshake exception to the chosen protocol, authorize machine/device destinations using persisted ownership, enforce expiry/revocation and trusted origins. Do not just append long-lived access tokens to URLs.
**Testing:** Browser and mobile connection, no-token/expired token, tenant/customer cross-subscription, unauthorized SEND, reconnect/resubscribe and token expiry.
**Risk of Change:** High; coordinate both clients and server atomically/compatibly.

### SEC-004 [P1] — Refresh rotation does not enforce account state or atomic single use

**Area:** Security / Authentication.
**Confidence:** CONFIRMED missing checks/locking; concurrency outcome requires PostgreSQL test.
**Location:** `backend/src/main/java/com/yantrago/api/service/AuthService.java:107-152`; `backend/src/main/java/com/yantrago/api/repository/RefreshTokenRepository.java:13-23`; `backend/src/main/java/com/yantrago/api/security/JwtAuthFilter.java:53-85`.
**Current Behavior:** Refresh checks token validity/revocation but does not repeat active/locked user and organization checks from login. Read-then-revoke is not guarded atomically. Reuse handling revokes rows then throws a runtime exception inside the transaction.
**Problem / Evidence:** Deactivated users can mint new tokens through this path. Concurrent refresh can pass the same check; family revocation may roll back with the thrown exception under default Spring transaction behavior.
**Production Impact:** Session revocation is unreliable; stolen-token containment and administrative deactivation are weakened. Access JWTs are accepted without account-state lookup until expiration.
**Recommendation:** Atomic consume/rotate plus account-state enforcement and explicit committed reuse revocation.
**Implementation Approach:** Lock/conditional-update token consumption, separate committed revocation from error response, define session-family policy and access-token invalidation/short TTL behavior. Reject tenant drift rather than taking stale organization claims from a refresh row.
**Testing:** Concurrent single-use rotation, reuse-family revocation after transaction completion, locked/deactivated user/org, moved user, logout, rollback and client simultaneous refresh.
**Risk of Change:** High; coordinate MOB-002/WEB-001 to avoid accidental family invalidation.

### SEC-005 [P1] — Settings creation can reference another tenant's machine

**Area:** Security / Database integrity.
**Confidence:** CONFIRMED unchecked relation; impact contingent on a valid target ID/key.
**Location:** `backend/src/main/java/com/yantrago/api/service/SettingsService.java:64-84`; `database/migrations/V13__create_settings.sql:5-15`; `backend/src/main/java/com/yantrago/api/controller/SettingsController.java:42-95`.
**Current Behavior:** Upsert sets organization from the caller and machineId from the URL, without verifying machine ownership. Independent foreign keys allow mismatched tenant/machine rows; uniqueness is `(machine_id, setting_key)`.
**Problem / Evidence:** Tenant A can insert an unused key for tenant B's machine under tenant A. This is not a demonstrated overwrite of B's existing row, but it can reserve the global machine/key pair and block B's legitimate insert.
**Production Impact:** Cross-tenant data-integrity violation and configuration denial of service.
**Recommendation:** Verify referenced machine against trusted tenant/ownership before read/write; enforce relational consistency in schema where appropriate.
**Implementation Approach:** Use tenant-scoped machine lookup and permission checks; audit inconsistent existing rows before forward constraints; return controlled conflict/not-found responses.
**Testing:** Foreign-tenant valid UUID, nonexistent machine, same-tenant unauthorized customer, duplicate key, upgrade with inconsistent fixtures.
**Risk of Change:** Medium.

### SEC-006 [P2] — Browser token persistence and permissive origins increase exposure

**Area:** Security / Admin.
**Confidence:** CONFIRMED configuration/storage; actual XSS or ambient-cookie exploit UNKNOWN.
**Location:** `admin-web/src/lib/auth.ts:12-17`; `admin-web/src/lib/api-client.ts:20-27`; `backend/src/main/java/com/yantrago/api/config/SecurityConfig.java:122-132`; `backend/src/main/java/com/yantrago/api/config/WebSocketConfig.java:43-47`.
**Current Behavior:** Access/refresh tokens persist in localStorage. All origin patterns are accepted and credentialed CORS is enabled.
**Problem / Evidence:** Successful same-origin script injection could read persistent tokens; broad origin policy enlarges browser attack surface. No actual XSS sink/exploit was established.
**Production Impact:** Higher consequence of XSS or a future cookie-auth change. **Correction:** a malicious origin cannot automatically read another origin's localStorage or obtain its Bearer JWT just because CORS is permissive. Spring allowedOriginPatterns also need not return literal wildcard ACAO; do not claim all browsers reject this setup.
**Recommendation:** Explicit origin allowlist, CSP/XSS defense, shorter-lived in-memory access tokens and a carefully designed secure refresh/session mechanism.
**Implementation Approach:** Choose between existing API-compatible session design and a Next.js server-mediated session; if cookies are adopted, add Secure/HttpOnly/SameSite and CSRF protection rather than blindly retaining disabled CSRF.
**Testing:** Allowed/denied origins, no token in logs/URLs, CSP, session persistence/logout and CSRF tests for any cookie design.
**Risk of Change:** Medium to High.

### SEC-007 [P1] — Rate limiting trusts forwarded identity, grows without eviction and precedes JWT auth

**Area:** Security / Performance.
**Confidence:** CONFIRMED design; effective proxy/filter behavior requires runtime verification.
**Location:** `backend/src/main/java/com/yantrago/api/security/RateLimitFilter.java:34-86`; `backend/src/main/java/com/yantrago/api/config/SecurityConfig.java:66-68`.
**Current Behavior:** Buckets are process-local unbounded maps. The first X-Forwarded-For value is trusted. Rate filter is registered before JWT parsing while user-limit lookup reads SecurityContext.
**Problem / Evidence:** Untrusted forwarded keys can evade IP quotas and grow memory if the edge does not overwrite them. Per-user enforcement is not placed after JWT authentication. Multiple instances multiply local quotas; shared NAT users compete for one IP bucket.
**Production Impact:** Weak abuse protection, legitimate throttling and memory pressure under hostile/large traffic.
**Recommendation:** Trusted-proxy normalization, pre-auth IP plus post-auth principal limits, bounded eviction and explicitly chosen cluster policy.
**Implementation Approach:** Validate edge ingress settings, bound key cardinality, define login/account throttles, use existing Redis only if multi-instance shared quotas are required, and define failure behavior.
**Testing:** Spoofed/long forwarded headers, NAT users, authenticated rate threshold, eviction/cardinality, two instances and Redis outage if used.
**Risk of Change:** Medium.

### SEC-008 [P1] — Sensitive-operation audit is log-only

**Area:** Security / Audit.
**Confidence:** CONFIRMED.
**Location:** `backend/src/main/java/com/yantrago/api/security/AuditLogInterceptor.java:25-58`; `backend/src/main/java/com/yantrago/api/service/AuditLogService.java:106-125`.
**Current Behavior:** Interceptor writes SLF4J events; the database audit method has no main-source invocation found. Several sensitive operations, including customer reset, are not in its path list.
**Problem / Evidence:** An audit table/service existing in the repository does not prove durable event persistence; normal logs are not an append-only business audit store.
**Production Impact:** Incomplete incident/administrative reconstruction. machine_commands provides some separate command evidence, but CMD-003 and DB-002 explain its additional gaps.
**Recommendation:** Durable actor/tenant/action/resource/outcome auditing with controlled retention and write permissions.
**Implementation Approach:** Decide fail-closed versus durable-outbox behavior by operation, record events at appropriate transaction boundaries (not merely before/after HTTP), avoid passwords/tokens/PII payloads, cover resets and assignments, protect audit mutation at the DB role level.
**Testing:** Successful/denied/failed operations, login identity, rollback, audit store unavailable, no secret fields, append-only grants and retained command history.
**Risk of Change:** Medium; audit failure policy is a business/security decision.

Additional scoped risk: `AuthService.java:56-58` uses unscoped `UserRepository.findByEmail`, while `database/migrations/V2__create_users_and_roles.sql:18-19` permits tenant-scoped duplicate identifiers. This is a confirmed schema/query inconsistency; actual collisions and resulting nonunique-result failures are UNKNOWN. TASK-005 must define and test globally unique login IDs or a non-authoritative pre-auth tenant locator. Any locator must only select an account; the JWT tenant must still come from the verified persisted identity, never a frontend-supplied organization claim.

Parameterized SQL is used in inspected report/history queries; dynamic alert metric table/column selection is allowlisted. No confirmed SQL injection, SSRF, unsafe upload or webhook exploit is asserted. Upload/storage/reset/OTP integrations not present in the inspected critical flows are not assumed implemented or secure. Dependency vulnerabilities, TLS exposure, deployed secret values and log contents require authorized independent verification. Default-secret deployment wiring is covered in DEV-001 without reproducing values.

## 11. Performance Audit

### PERF-001 [P1] — Historical reads/exports are unbounded and machine pages perform per-row lookups

**Area:** Performance / Backend / Database.
**Confidence:** CONFIRMED query patterns; throughput impact UNKNOWN.
**Location:** `backend/src/main/java/com/yantrago/api/repository/TelemetryRepository.java:45-66`; `backend/src/main/java/com/yantrago/api/repository/LocationRepository.java:59-64`; `backend/src/main/java/com/yantrago/api/controller/LocationController.java:38-48`; `backend/src/main/java/com/yantrago/api/controller/ReportController.java:41-80`; `backend/src/main/java/com/yantrago/api/service/MachineService.java:266-293`.
**Current Behavior:** Caller-selected time ranges materialize full historical lists; report output is assembled in memory. Machine DTO mapping queries device per row and customer for each assigned row.
**Problem:** No maximum time window/result count/downsampling is visible in these paths. Machine page mapping adds up to two repository lookups per row.
**Evidence:** queryForList without limit; stream materialization; synchronous byte-array exports; repository calls in toDto.
**Production Impact:** Heap growth, long database occupancy and pool contention during wide queries; N+1 amplification on list pages.
**Recommendation:** Explicit date/result limits, deterministic cursor pagination or chart downsampling, batched projections, and separately bounded exports.
**Implementation Approach:** Measure query count and representative plans; eliminate per-row fetches; enforce backwards-compatible bounded history contracts; queue large exports only if measured need justifies it, within the existing service architecture.
**Testing:** 100K/1M/10M-row datasets where relevant, maximum range/page payload, rejected oversized requests, query-count assertions, cancellation and pool/heap metrics.
**Risk of Change:** Medium due to client response-contract changes.

## 12. Scalability / 20,000-User Audit

### SCALE-001 [P1] — Local connection/broker state and unbounded socket workers constrain scaling

**Area:** Architecture / Scalability / Gateway.
**Confidence:** CONFIRMED design; actual saturation and multi-instance deployment UNKNOWN.
**Location:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/DeviceConnectionRegistry.java:17-51`; `device-gateway/src/main/java/com/yantrago/gateway/service/CommandDispatchService.java:53-60`; `device-gateway/src/main/java/com/yantrago/gateway/config/RabbitMqConfig.java:61-63`; `backend/src/main/java/com/yantrago/api/config/WebSocketConfig.java:35-39`; `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5TcpServer.java:28-29,75-103`.
**Current Behavior:** Gateway socket ownership is local, but commands compete on a common queue. Backend STOMP broker is local. TCP uses blocking socket handlers on a cached thread pool. Re-registering an IMEI replaces its output stream; disconnect of an older client removes the IMEI entry unconditionally.
**Problem / Evidence:** Adding a gateway instance does not route commands to the socket owner; backend instances do not share live subscriptions. Slow/idle connections can increase worker count. Old-session teardown can remove a replacement session even on one instance.
**Production Impact:** False offline failures, lost live updates, reconnect races and thread/heap/FD exhaustion under device bursts. No exact connection ceiling is claimed.
**Recommendation:** Fix connection-epoch ownership/removal and serialized writes; measure/bound socket admission. Before replication, design explicit socket-owner routing and cross-instance broadcast.
**Implementation Approach:** Keep one gateway until a measured requirement justifies replication; then use owner-aware routing/leases with fencing or a tested connection-partition scheme. Hashing RabbitMQ messages alone is insufficient unless device connection placement obeys the same ownership rule. Keep two services; do not rewrite copied protocol parsing for scaling.
**Testing:** Old/new connection race, simultaneous replies/writes, connection flood in isolation, two gateways with device on one, two API instances with clients on both, instance death and reconnect storm.
**Risk of Change:** High.

### GATE-001 [P1] — Concox TCP adapter discards fragmented frames between reads

**Area:** Gateway / Reliability.
**Confidence:** CONFIRMED framing behavior; physical-device incidence UNKNOWN.
**Location:** `device-gateway/src/main/java/com/yantrago/gateway/tcp/concox/ConcoxV5TcpServer.java:100-119,160-198`.
**Current Behavior:** Every InputStream read is copied into a new byte array and independently passed to extractPackets. Incomplete trailing frames cause the extraction loop to break; their bytes are not retained for the next read.
**Problem / Evidence:** TCP is a byte stream and can split headers/bodies arbitrarily. No per-connection remainder accumulator exists in this adapter path.
**Production Impact:** Valid login/telemetry/reply frames can disappear on ordinary network fragmentation; simulator writes do not guarantee identical read boundaries.
**Recommendation:** Review section 11 of the architecture document and preserve copied parser behavior. Correct only the stream-adapter boundary with bounded per-connection accumulation if approved.
**Implementation Approach:** Feed complete frames to unchanged handlers, cap buffered bytes and frame length, define malformed-stream resynchronization and timeout, and verify that adapter code is not protected copy-as-is logic before editing.
**Testing:** Split every byte boundary, multiple frames per read, trailing half-frame, oversized input, reconnect cleanup and golden handler regression fixtures.
**Risk of Change:** High; compatibility review is required before implementation.

### Workload model and capacity limits

Registered users are not concurrent users. Gather DAU/MAU, peak active sessions, tenant size/skew, dashboard polling, command frequency, connected devices, heartbeat/location/telemetry cadence, report ranges, retention, infrastructure sizes and SLO/RPO/RTO. None is derivable from the 20,000 registered-user target alone.

Model, then measure:

- REST RPS = active sessions x requests per session per second, plus jobs/integrations. Peak factors must come from actual traffic/business scenarios.
- Device ingress = connected devices x event frequency; up to three metric rows per telemetry event plus GPS/state/alerts. Device count is independent of users.
- Database load includes telemetry writes, reports, per-row DTO queries and refresh/auth writes. Historical table volume = ingress rate x retention, adjusted for deduplication and archive policy.
- Backend Hikari default max 100/min idle 10, acquire timeout 30s (`backend/src/main/resources/application.yml:20-25`) is configuration, not measured demand. Sum API + gateway + maintenance + migration connections across replicas and reserve administration headroom before raising pools.
- Default mapping cache TTL is 300s with 60s negative cache (`device-gateway/.../DeviceMappingCacheServiceImpl.java:26-27,44-72`). Redis failure currently occurs outside the DB lookup catch; DB errors are conflated with missing mappings. Measure hit ratio and define invalidation on rebinding/deactivation; never cache permissions without a revocation policy.
- Location flush batch 500, delay 5s and nominal queue guard 5,000 (`LocationPersistenceService.java:37-39`) do not establish sustainable throughput. Immediate flush paths, failures and unbounded concurrent producers change behavior.
- No need is established for read replicas, Kubernetes, new databases or new microservices. First correct durability, payload/query bounds, indexing and observable overload behavior. Evaluate NIO/Netty transport changes only if measured blocking-worker pressure requires them and parser preservation is guaranteed.

## 13. Traffic Spike Analysis

These are **risk hypotheses**, not measured saturation order. The current correctness failures already occur below load; a fast failing request must not count as successful capacity.

| Scenario | Candidate first / second pressure points | Failure/retry behavior | Required recovery proof |
|---|---|---|---|
| 2x baseline | REST rate-limit/NAT collisions; history queries/DB pool | More 429s, queueing and concurrent refresh; user retries can multiply commands | Correct Retry-After/backoff, single-flight refresh, command idempotency and normal latency after spike |
| 5x baseline | Ingest backlog / DB writes; report memory and socket workers | Long pool waits, lost caught exceptions, batch drain losses, client timeouts | No acknowledged-but-lost events, bounded queues/memory, measurable backlog drain |
| 10x baseline | Admission/thread/FD limits; DB and broker contention | Reconnect storm, delayed stale commands, provider retry amplification | Reject overload safely, expire stale commands, preserve durable backlog and audit, recover without restart loops |

Database behavior: inspect wait events, connection occupancy, locks, disk/WAL and partition scans. API behavior: p95/p99, validated success rate, response bytes, cancellation and 429s. Provider behavior: mock push/SMS cannot provide evidence; inject realistic latency/errors after real adapters exist. Enforce capped exponential backoff with jitter and idempotency before automatically retrying command writes. Measure each scenario in isolated staging, never against live equipment by default.

## 14. Reliability & Failure Analysis

### REL-001 [P1] — Alert and notification flow is incomplete and can report fictitious delivery

**Area:** Reliability / Backend.
**Confidence:** CONFIRMED.
**Location:** `backend/src/main/java/com/yantrago/api/queue/AlertConsumer.java:42-75`; `backend/src/main/java/com/yantrago/api/queue/NotificationConsumer.java:38-63`; `backend/src/main/java/com/yantrago/api/service/NotificationService.java:90-149`; `backend/src/main/java/com/yantrago/api/service/PushNotificationService.java:31-39`; `backend/src/main/java/com/yantrago/api/service/SmsService.java:28-36`; `backend/src/main/java/com/yantrago/api/service/AlertGenerationService.java:57-69,89-99`.
**Current Behavior:** Alert persistence and notification creation consume the same queue (competing consumers, not fan-out). Notification creation assigns a random organization UUID and no recipient. Dispatch passes null destinations. Push/SMS implementations return generated IDs without provider calls. Rule evaluation has no call site found in main Java sources; duplicate suppression inspects only one arbitrary alert.
**Problem:** One event does not reliably reach both business operations; placeholder identity violates persistence requirements; mock delivery becomes SENT; configured thresholds are not connected to an invocation path.
**Evidence:** Same QueueNames.ALERT_EVENT_QUEUE annotation in both consumers; UUID.randomUUID tenant; mock service returns; no scheduled/listener invocation of evaluateAlertsForMachine found.
**Production Impact:** Lost or missing safety/operational alerts, failed notification inserts and misleading delivery status.
**Recommendation:** Define one durable alert pipeline with separate queue bindings where fan-out is required; resolve tenant/recipient from trusted persisted data; implement actual provider delivery or explicitly mark provider unavailable.
**Implementation Approach:** Connect evaluation to durable telemetry/event processing; preserve event identity; create per-user/channel records after validated alert persistence; dispatch asynchronously with idempotency, bounded retries and honest delivery states. Replace one-row duplicate check with an exact active-alert predicate.
**Testing:** One event persists an alert and appropriate notification(s), duplicates/retries, missing recipient, disabled provider, provider timeout, no false SENT, tenant boundaries and threshold suppression.
**Risk of Change:** High across queue routing and existing notification state.

## 15. Error Handling Audit

GlobalExceptionHandler exists and masks generic internal errors, a useful foundation. API-002 documents validation gaps; API-001 documents misleading no-data behavior. Queue/transport error semantics remain under review.

## 16. Code Quality Audit

Evidence above favors correcting contracts and transactions rather than broad refactoring. Typed JDBC mapping and typed request DTOs address concrete failures. Preserve established module boundaries and useful repository abstractions.

## 17. AI-Generated Code Audit

Confirmed discrepancies between comments and implementation include V15 claiming telemetry partition indexes already exist, notification services describing pluggable production delivery while returning mock IDs, and completed phase checkboxes while notification comments defer work to a future phase. These observations do not establish authorship; they establish verification gaps.

## 18. Testing Audit

`TelemetryServiceTest.java:89-105` mocks temporal values as LocalDateTime and cannot detect real JDBC mapping behavior. Runtime tests have not been run. Build execution was deliberately avoided because backend processResources copies migrations into source (`backend/build.gradle.kts:61-69`). Complete inventory and verification plan pending.

## 19. Load Testing Plan

Pending consolidation. No live traffic, credentials, infrastructure or external provider was exercised.

## 20. Observability Audit

Pending deployment review consolidation.

## 21. DevOps / Production Audit

Pending deployment review consolidation.

## 22. UX / Accessibility Audit

Pending client review consolidation. Report fidelity issues are covered in API-003; no device/browser/screen-reader verification has been performed.

## 23. Master Findings Table

Pending full ordering. Stable finding identifiers already written above remain valid unless explicitly superseded during validation.

## 24. KEEP — DO NOT CHANGE

- Intentional two-service backend/gateway architecture and shared message contracts.
- UUID primary identifiers; human-readable sequence improvement must not replace UUIDs.
- Canonical forward-only Flyway source in database/migrations; never repair applied migrations by editing history.
- PostgreSQL/PostGIS and time partitioning; repair lifecycle/index gaps, not replace the storage engine.
- Batch JDBC persistence and explicit organization predicates where correctly used.
- Global exception boundary and generic internal-error masking.
- Copied Concox/JT808 parser behavior. Integration defects require adapter/lifecycle fixes and compatibility tests, not parser rewrites.

## 25. Implementation Roadmap

Not yet finalized. All remediation is proposal only. Implement one phase at a time; stop for user verification and explicit approval before the next phase. No implementation is authorized by this audit.

## 26. Detailed Windsurf Implementation Tasks

Pending full dependency ordering after security and command-path findings are validated.

## 27. BREAKING CHANGES & MIGRATION RISKS

Forward migrations only. Rehearse historical retention changes, sequence allocation, uniqueness backfills and index creation. Coordinate date/pagination/error-schema changes with both clients. Device protocol behavior is protected. Never run destructive retention, database cleanup, or history rewriting without explicit confirmation for that action.

## 28. Production Readiness Score

Pending final evidence consolidation. Any eventual score is an explicitly subjective engineering rubric, not measured availability, test coverage or capacity.

## 29. 20,000-User Capacity Verdict

Pending final verdict. Quantified capacity remains UNKNOWN — requires measurement / load testing.

## 30. Capacity Table

| Metric | Observed / configured | Target | Status | Verification |
|---|---|---|---|---|
| Registered users | UNKNOWN | 20,000 | Not measured | Business/production inventory |
| Concurrent users / RPS | UNKNOWN | Business-defined | Not measured | Workload model and load tests |
| Connected devices / telemetry rate | UNKNOWN | Business-defined | Not measured | Device inventory/cadence |
| DB pool | Default backend maximum 100 | Fleet-wide connection budget | Config only | Effective profiles and DB settings |
| API latency / error rate | UNKNOWN | Approved SLO | Not measured | Instrumented isolated staging tests |

## 31. PRODUCTION RELEASE GATE

**BLOCK RELEASE pending remediation and verification.** Existing confirmed correctness and audit-retention defects are sufficient to prevent a production-ready claim. Final prioritized gate pending security/command/client/deployment validation.

## 32. TOP 10 THINGS TO DO FIRST

Pending final dependency ordering; do not start implementation from this partial checkpoint.

## 33. Final Executive Summary

This is a durable in-progress checkpoint intended to make another conversation loss recoverable. Continue with backend security, gateway, mobile/admin and deployment results, then validate cross-module evidence, finalize findings/tasks and review the final diff. Only this report has been created by the audit.
