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

20. Never commit secrets, passwords, or API keys to the repository.
    Use environment variables and application-prod.yml with file permissions 600.
