# Project Rules

1. This is a production application, not an MVP.

2. Backend:
   - Java 17
   - Spring Boot 3.x
   - PostgreSQL 15 + PostGIS
   - Redis 7
   - RabbitMQ 3
   - Netty (for TCP gateway, via ServerSocket/NIO)

3. Never put TCP protocol handling inside REST controllers.

4. Device communication must be asynchronous (via RabbitMQ between backend and gateway).

5. Never assume a device command succeeded until acknowledgement is received.

6. All machine commands must be auditable (machine_commands + command_attempts tables).

7. All APIs must enforce tenant isolation (organization_id from JWT, never from request body).

8. Never trust tenant_id / organization_id supplied by the frontend.

9. All sensitive operations require authorization (RBAC permission checks).

10. Do not create temporary architecture just to make a feature work.

11. Do not introduce microservices unless there is a documented reason.
    The current architecture has exactly two services (backend API + TCP gateway) connected via RabbitMQ. This is intentional.

12. Every production feature requires:
    - validation (Bean Validation / @Valid)
    - error handling (GlobalExceptionHandler)
    - logging (SLF4J via logback-spring.xml)
    - tests (JUnit 5)
    - security checks (JWT + RBAC + tenant guard)

13. Do not modify existing TCP protocol behavior without reviewing
    docs/YANTRAGO_PROJECT_STRUCTURE.md (section 11 — Reused GPS/TCP Protocol Components).

14. Follow the database design in docs/YANTRAGO_PROJECT_STRUCTURE.md (section 8 — Database).
    All migrations go in database/migrations/ as Flyway versioned SQL files.

15. Before implementing a major feature, review the relevant architecture documents:
    - docs/YANTRAGO_PROJECT_STRUCTURE.md — full architecture and folder layout
    - docs/YANTRAGO_IMPLEMENTATION_PLAN.md — step-by-step implementation phases

16. Reused TCP protocol code (ConcoxV5, JT808) is copied as-is from the HarvestTracker project.
    Do not rewrite or refactor protocol parsing logic. Only change package declarations.

17. The shared/ module defines RabbitMQ message contracts.
    Both backend and device-gateway must compile against these types. No ad-hoc message formats.

18. Time-series tables (location_history, voltage_readings, battery_readings, gsm_readings)
    are partitioned by month. Use batch inserts for high-volume writes.

19. UUIDs are the primary key type for all tables.

20. Phase-wise implementation:
    - All functionality implementation must be done phase by phase, one phase at a time.
    - After completing one phase, STOP and ask the user to verify the changes.
    - Explicitly ask the user whether to start the next phase implementation or not.
    - Do NOT proceed to the next phase until the user confirms.
    - Each phase should be self-contained, deployable, and testable on its own.

---

# Senior Developer Coding Standards

Rules 21-24 below are **tiered by change size**. Don't over-engineer small fixes.

## Change Size Tiers

| Tier | Example | Rules to apply |
|------|---------|----------------|
| **Trivial** | Typo, label text, color value, one-line config | Just fix it. No rules. |
| **Small** | Bug fix, add a field, change a route, tweak UI | Naming, no dead code, build passes |
| **Medium** | New screen, new API endpoint, new feature | Architecture, error handling, navigation, tests |
| **Major** | New module, new service, protocol change, schema change | All rules + code review checklist |

When unsure, default to the next tier up — but never apply Major-tier rules to a Trivial fix.

---

## 21. Backend (Java / Spring Boot) — applies to Medium+ changes

### Architecture
- Layered: Controller → Service → Repository. Never skip a layer.
- Controllers are thin: validation, delegation, response mapping only.
- DTOs separate from entities. Never return JPA entities from controllers.
- Use records for immutable DTOs where possible (Java 17+).

### Error Handling
- GlobalExceptionHandler for all exception → HTTP mapping.
- Throw domain-specific exceptions from services.
- Validate input with @Valid + Bean Validation on all DTOs.
- Consistent error format: { error, message, timestamp, status }.

### Security (always — even for Small changes)
- JWT + RBAC on every endpoint except /auth/login and /auth/refresh.
- Tenant isolation: organization_id from JWT, never from request body.
- Never log secrets, tokens, or passwords.
- Parameterized queries only — never string concatenation for SQL.

### Database
- Schema changes via Flyway migrations only.
- UUIDs as primary keys.
- Never DROP or destructive DDL without explicit user approval.

### Logging
- SLF4J, never System.out.println.
- Include context (IDs, IMEI) in log messages.

### Testing (Medium+ changes)
- Unit tests for new service logic.
- Test happy path + edge cases (null, empty, invalid).

### API Design
- RESTful, versioned: /api/v1/...
- Consistent response shapes.
- Pagination for list endpoints.

---

## 22. Mobile (Flutter / Dart) — applies to Medium+ changes

### Architecture
- Feature-based folders: features/<feature>/pages/, providers/, widgets/, models/.
- Riverpod for state management.
- Dio with interceptors for API calls.
- Never call API directly from widgets — go through a provider.
- Models: immutable, fromJson/toJson, const constructors.

### Widget Standards
- ConsumerWidget / ConsumerStatefulWidget.
- Use .when() for AsyncValue — handle loading, error, AND data.
- Extract reusable widgets (don't copy-paste).
- No magic numbers — use constants or theme values.

### Error Handling
- Never show raw exception strings to users.
- Loading states (spinner/skeleton), never blank screen.
- Empty states (message + icon), never blank list.

### Navigation (always — even for Small changes)
- context.push() for drill-down (list → detail) — enables back arrow.
- context.go() for tab switches — replaces stack.
- Path parameters for detail screens: /app/machines/:id.

### Storage
- Tokens in FlutterSecureStorage, never SharedPreferences.

### Testing (Medium+ changes)
- Widget tests for screens, unit tests for models/providers.

---

## 23. General Standards (All Languages) — always apply

### Always (even Trivial)
- No dead code. Remove unused imports/variables.
- No secrets in source code.
- Build must succeed.

### Small+ changes
- Naming: PascalCase classes, camelCase methods/variables, descriptive names.
- No commented-out code. Delete it — Git remembers.
- One logical change per commit. Imperative commit messages.

### Medium+ changes
- DRY: extract shared logic. KISS: prefer simple solutions. YAGNI: don't build unrequested features.
- Functions < 30 lines. Files < 300 lines. Split if larger.
- Comments explain WHY, not WHAT. Preserve existing comments.
- Check dependency exists in project before using. Prefer stable versions (7+ days old).

---

## 24. Code Review Checklist — apply to Major changes only

### Backend
- [ ] Input validation (@Valid + Bean Validation)
- [ ] Error handling via GlobalExceptionHandler
- [ ] Tenant isolation (organization_id from JWT)
- [ ] RBAC check (@PreAuthorize)
- [ ] Logging with context, no sensitive data
- [ ] Flyway migration if schema changed
- [ ] Unit tests for new logic
- [ ] No dead code
- [ ] Consistent API response format

### Mobile
- [ ] No direct API calls from widgets
- [ ] AsyncValue .when() handles all 3 states
- [ ] User-friendly error messages
- [ ] Loading + empty states shown
- [ ] push() for drill-down, go() for tabs
- [ ] Reusable widgets extracted
- [ ] No dead code or unused imports
- [ ] Secure storage for tokens

### General
- [ ] Compiles without warnings
- [ ] No dead code
- [ ] Descriptive names
- [ ] Small, focused functions
- [ ] No secrets in code
- [ ] Tests for new logic
- [ ] Build succeeds
