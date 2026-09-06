# YantraGO Backend API

Spring Boot 3.x REST API + WebSocket server for the YantraGO machine management platform.

## Responsibilities

- Authentication (JWT + RBAC + multi-tenant)
- User, organization, customer, machine, device CRUD
- Command lifecycle (PENDING → QUEUED → SENT → ACK → DONE / FAILED)
- Telemetry storage (voltage, battery, GSM)
- Location storage + history (PostGIS, partitioned)
- Alerts, notifications (push/email/SMS)
- Reports (PDF/CSV export)
- WebSocket real-time push (STOMP)
- Audit logging

## Build

```bash
./gradlew :backend:build -x test       # Compile + skip tests
./gradlew :backend:bootRun             # Run locally (needs Postgres/Redis/RabbitMQ)
./gradlew :backend:bootJar             # Build executable JAR
```

## Profiles

- `default` — placeholders, expects env vars for secrets
- `dev` — localhost Postgres/Redis/RabbitMQ, verbose logging
- `prod` — production (use `application-prod.yml` with file permissions 600)

Activate: `--spring.profiles.active=dev`

## Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/health` | Simple health check |
| `GET /actuator/health` | Actuator health |
| `GET /swagger-ui.html` | OpenAPI docs (dev) |
| `GET /v3/api-docs` | OpenAPI JSON |

## Flyway Migrations

Migrations are copied from `database/migrations/` into `src/main/resources/db/migration/`
during the build. The source of truth is `database/migrations/` — do not edit the copies directly.

## Reference

- `docs/YANTRAGO_PROJECT_STRUCTURE.md` section 3
