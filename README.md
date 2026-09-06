# YantraGO Machine Management Platform

Production-grade multi-tenant platform for managing YantraGO fencing machines with GPS tracking, telemetry, command lifecycle, and real-time updates.

## Tech Stack

- **Backend API** — Java 17 + Spring Boot 3.x
- **TCP Device Gateway** — Java 17 + Spring Boot 3.x (reuses HarvestTracker GPS/TCP protocol code)
- **Shared Library** — Java 17 (RabbitMQ message contracts)
- **Device Simulator** — Java 17
- **Mobile App** — Flutter (Android + iOS)
- **Admin Web** — Next.js 14+ (TypeScript)
- **Database** — PostgreSQL 15 + PostGIS
- **Cache** — Redis 7
- **Message Broker** — RabbitMQ 3
- **Build** — Gradle (Kotlin DSL)

## Project Structure

```
yantrago/
├── backend/          # Spring Boot REST API + WebSocket
├── device-gateway/   # Spring Boot TCP device gateway
├── mobile/           # Flutter mobile app
├── admin-web/        # Next.js admin portal
├── shared/           # Shared Java library
├── database/         # Flyway migrations
├── infra/            # Docker, Nginx, monitoring
├── simulator/        # Java device simulator
└── docs/             # Documentation
```

## Build

```bash
./gradlew build          # Build all Java modules
./gradlew :backend:bootRun   # Run backend API
```

## Documentation

- `docs/YANTRAGO_PROJECT_STRUCTURE.md` — Full architecture and folder layout
- `docs/YANTRAGO_IMPLEMENTATION_PLAN.md` — Step-by-step implementation plan
