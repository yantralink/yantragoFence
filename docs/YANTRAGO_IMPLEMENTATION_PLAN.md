# YantraGO Machine Management Platform — Implementation Plan

> **Document Purpose:** Step-by-step implementation prompts to build the YantraGO platform incrementally.
>
> **Reference:** `YANTRAGO_PROJECT_STRUCTURE.md` for full architecture, folder layout, and reused components.
>
> **Target Project Location:** `D:\CascadeProjects\yantrago` (separate project under CascadeProjects folder)
>
> **Source for Reuse:** `D:\CascadeProjects\Tracker\device-location-api\src\main\java\com\harvesttracker\devicelocationapi\tcp\`
>
> **Stack:** Java 17 + Spring Boot 3.x, Gradle (Kotlin DSL), PostgreSQL 15 + PostGIS, Redis 7, RabbitMQ 3, Flutter, Next.js 14+

---

## How to Use This Document

- Execute each phase in order. Do not skip phases.
- Each phase contains a **prompt** you can paste into Devin/AI to implement that step.
- After each phase, verify the build/tests pass before moving to the next.
- Mark each phase `[x]` when complete.

---

## Phase 0 — Create New Project Skeleton

**Goal:** Create the `yantrago` monorepo at `D:\CascadeProjects\yantrago` with the root Gradle multi-project setup.

### Prompt

```
Create a new project folder at D:\CascadeProjects\yantrago as a Gradle multi-project monorepo.

Create these root files:
1. settings.gradle.kts — include :backend, :device-gateway, :shared, :simulator
2. build.gradle.kts — root build with Spring Boot plugin (apply false), spring dependency management (apply false), kotlin jvm (apply false), and a clean task
3. gradle.properties — org.gradle.jvmargs=-Xmx2048m, springBootVersion=3.2.5
4. .gitignore — Java/Gradle/IDE ignores
5. .editorconfig — standard Java config
6. README.md — short project description
7. gradle/wrapper/gradle-wrapper.properties — Gradle 8.5
8. gradlew and gradlew.bat — Gradle wrapper scripts

Do NOT create subproject folders yet — only the root skeleton. Run `gradlew --version` to verify the wrapper works.

Reference: D:\CascadeProjects\Tracker\docs\YANTRAGO_PROJECT_STRUCTURE.md sections 1 and 2 for exact file contents.
```

- [x] Done

---

## Phase 1 — Shared Java Library (`shared/`)

**Goal:** Create the shared module with RabbitMQ message contracts and DTOs.

### Prompt

```
Create the shared/ subproject at D:\CascadeProjects\yantrago\shared.

1. shared/build.gradle.kts — java-library plugin, group com.yantrago, version 1.0.0, Java 17, dependencies: spring-boot-starter-amqp, jackson-databind
2. shared/src/main/java/com/yantrago/shared/queue/QueueNames.java — constants for RabbitMQ exchanges and queues (command, command.result, telemetry, device.event, alert.event, location)
3. shared/src/main/java/com/yantrago/shared/queue/CommandMessage.java — serializable DTO: commandId, machineId, imei, commandType (ON/OFF), timestamp
4. shared/src/main/java/com/yantrago/shared/queue/CommandResultMessage.java — commandId, status (PENDING/QUEUED/SENT/ACK/DONE/FAILED), attemptCount, error, timestamp
5. shared/src/main/java/com/yantrago/shared/queue/TelemetryMessage.java — deviceId, imei, voltage, battery, gsmSignal, timestamp
6. shared/src/main/java/com/yantrago/shared/queue/DeviceEventMessage.java — deviceId, imei, eventType (LOGIN/HEARTBEAT/DISCONNECT), timestamp
7. shared/src/main/java/com/yantrago/shared/queue/AlertEventMessage.java — alertId, machineId, alertType, severity, message, timestamp
8. shared/src/main/java/com/yantrago/shared/queue/LocationMessage.java — deviceId, imei, latitude, longitude, speed, course, timestamp
9. shared/src/main/java/com/yantrago/shared/dto/DeviceStateDto.java — deviceId, imei, online, lastSeen, voltage, battery, relayState
10. shared/src/main/java/com/yantrago/shared/dto/GpsIngestRequest.java — machineId, latitude, longitude, speed, course, timestamp
11. shared/src/main/java/com/yantrago/shared/dto/DeviceRecord.java — record with imei, deviceId, machineId, protocolType
12. shared/src/main/java/com/yantrago/shared/util/HashUtil.java — SHA-256 hashing utility
13. shared/README.md

Run `gradlew :shared:build` to verify it compiles.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 7.
```

- [x] Done

---

## Phase 2 — Database Migrations (`database/`)

**Goal:** Create all Flyway SQL migrations and seed data.

### Prompt

```
Create the database/ folder at D:\CascadeProjects\yantrago\database.

Create these Flyway migration files under database/migrations/:

V1__create_organizations.sql — organizations table (id UUID PK, name, slug UNIQUE, white_label_config JSONB, created_at, updated_at)
V2__create_users_and_roles.sql — users, roles, permissions, role_permissions, user_roles tables with RBAC
V3__create_customers.sql — customers table (FK to organizations)
V4__create_machines_and_devices.sql — machines, devices, machine_assignments tables
V5__create_device_states.sql — device_states (upsert), device_locations (upsert)
V6__create_location_history_partitioned.sql — location_history partitioned by month (use native partitioning)
V7__create_telemetry_tables.sql — voltage_readings, battery_readings, gsm_readings (all partitioned by month)
V8__create_commands.sql — machine_commands, command_attempts
V9__create_alerts.sql — alert_rules, alerts
V10__create_notifications.sql — notifications, notification_preferences
V11__create_audit_logs.sql — audit_logs partitioned by quarter
V12__create_recharges.sql — recharges table
V13__create_settings.sql — machine_settings, system_settings
V14__create_refresh_tokens.sql — refresh_tokens, login_sessions
V15__add_performance_indexes.sql — indexes on foreign keys, tenant columns, time columns

Also create:
database/seeds/V1__seed_super_admin.sql — default super_admin user
database/seeds/V2__seed_sample_organization.sql
database/seeds/V3__seed_sample_customers.sql
database/seeds/V4__seed_sample_machines.sql
database/partitions/create_monthly_partitions.sql — function to auto-create monthly partitions
database/partitions/archive_old_partitions.sql — archive function
database/schema/ERD.md — entity relationship documentation

All tables must include organization_id for multi-tenancy where applicable. Use UUID primary keys. Include created_at/updated_at timestamps.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 8.
```

- [x] Done

---

## Phase 3 — Backend API Foundation (`backend/`)

**Goal:** Create the backend Spring Boot project with main application, config, and build file. Get it to start up empty.

### Prompt

```
Create the backend/ subproject at D:\CascadeProjects\yantrago\backend.

1. backend/build.gradle.kts — Spring Boot plugin, dependency management, Java 17, group com.yantrago, version 1.0.0. Dependencies per YANTRAGO_PROJECT_STRUCTURE.md section 3 (spring-boot-starter-web, websocket, security, validation, jdbc, data-redis, amqp, actuator, mail; jjwt 0.11.5; postgresql; flyway; bucket4j; pdfbox; micrometer-prometheus; springdoc-openapi; project(:shared); test starters). useJUnitPlatform.
2. backend/src/main/java/com/yantrago/api/YantraGoApiApplication.java — @SpringBootApplication main class
3. backend/src/main/resources/application.yml — default config (server port 8080, datasource placeholder, redis/rabbitmq placeholder, flyway enabled, jwt config placeholders, logging)
4. backend/src/main/resources/application-dev.yml — dev profile pointing to localhost services
5. backend/src/main/resources/logback-spring.xml — console + file logging
6. backend/src/main/resources/db/migration/ — symlink or copy from database/migrations/
7. backend/README.md

Create a minimal HealthController at /api/v1/health returning {"status":"UP"} so we can verify the app starts.

Run `gradlew :backend:bootRun` to verify it starts (you can stop it after confirming). If Postgres/Redis/RabbitMQ are not running locally, just verify it compiles with `gradlew :backend:build -x test`.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 3.
```

- [x] Done

---

## Phase 4 — Backend: JPA Entities & Repositories

**Goal:** Create all domain models and data access layer.

### Prompt

```
In D:\CascadeProjects\yantrago\backend, create all JPA entities and repositories.

Entities under src/main/java/com/yantrago/api/model/:
User, Organization, Role, Permission, Customer, Machine, Device, MachineAssignment, DeviceState, DeviceLocation, LocationHistory, VoltageReading, BatteryReading, GsmReading, MachineCommand, CommandAttempt, MachineSetting, AlertRule, Alert, Notification, NotificationPreference, Recharge, AuditLog, RefreshToken, LoginSession, SystemSetting

Repositories under src/main/java/com/yantrago/api/repository/:
UserRepository, OrganizationRepository, CustomerRepository, MachineRepository, DeviceRepository, CommandRepository, TelemetryRepository, LocationRepository, AlertRepository, NotificationRepository, SettingsRepository, RechargeRepository, AuditLogRepository, RefreshTokenRepository

Rules:
- Use UUID primary keys
- All tenant-scoped entities have organization_id
- Use @Entity, @Table with proper column mappings
- Use LocalDateTime for timestamps
- Repositories extend JpaRepository where appropriate; use JdbcTemplate for high-volume time-series (LocationRepository, TelemetryRepository)
- Include proper indexes via @Table(indexes=...)

Reference: YANTRAGO_PROJECT_STRUCTURE.md sections 3 (model/ and repository/ lists).
```

- [x] Done

---

## Phase 5 — Backend: Security & Auth

**Goal:** Implement JWT auth, RBAC, multi-tenant context, and the Auth controller/service.

### Prompt

```
In D:\CascadeProjects\yantrago\backend, implement the security and authentication layer.

Config:
- src/main/java/com/yantrago/api/config/SecurityConfig.java — Spring Security filter chain, permit /api/v1/auth/**, /actuator/health, /swagger-ui/**, /v3/api-docs/**; require auth for everything else; CORS config; stateless session
- src/main/java/com/yantrago/api/config/WebMvcConfig.java — CORS, interceptors

Security:
- src/main/java/com/yantrago/api/security/JwtAuthFilter.java — extract JWT from Authorization header, validate, set SecurityContext
- src/main/java/com/yantrago/api/security/TenantContextFilter.java — resolve organization_id from JWT claim, set TenantContext
- src/main/java/com/yantrago/api/security/TenantGuard.java — prevent cross-tenant access on repository queries
- src/main/java/com/yantrago/api/security/PermissionEvaluator.java — RBAC permission check
- src/main/java/com/yantrago/api/security/RateLimitFilter.java — per-IP and per-user rate limiting using Bucket4j
- src/main/java/com/yantrago/api/security/AuditLogInterceptor.java — log sensitive actions

Services:
- src/main/java/com/yantrago/api/service/JwtService.java — sign/verify JWT (HS256), generate access + refresh tokens
- src/main/java/com/yantrago/api/service/AuthService.java — login, refresh rotation, logout
- src/main/java/com/yantrago/api/service/OwnerContextService.java — tenant context resolution

DTOs:
- src/main/java/com/yantrago/api/dto/auth/LoginRequest.java, LoginResponse.java, RefreshTokenRequest.java, TokenResponse.java

Controller:
- src/main/java/com/yantrago/api/controller/AuthController.java — POST /api/v1/auth/login, POST /api/v1/auth/refresh, POST /api/v1/auth/logout

Write a basic AuthControllerTest to verify login flow compiles and the security chain loads.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 3 (security/, service/Auth*, dto/auth/).
```

- [x] Done

---

## Phase 6 — Backend: Core CRUD (Users, Organizations, Customers, Machines, Devices)

**Goal:** Implement the core business CRUD modules.

### Prompt

```
In D:\CascadeProjects\yantrago\backend, implement core CRUD modules.

For each module create Controller + Service + DTOs:

1. Users — UserController (/api/v1/users), UserService, dto for user
2. Organizations — OrganizationController (/api/v1/organizations), OrganizationService
3. Customers — CustomerController (/api/v1/customers), CustomerService, dto/customer/CustomerDto
4. Machines — MachineController (/api/v1/machines), MachineService, dto/machine/MachineDto, CreateMachineRequest, MachineStatusDto
5. Devices — DeviceController (/api/v1/devices), DeviceService

Rules:
- All endpoints under /api/v1/{resource}
- Tenant isolation: every query filters by organization_id from TenantContext
- Use @Valid on request bodies
- Return standard error responses via @ControllerAdvice
- Pagination via Pageable for list endpoints
- Create a GlobalExceptionHandler under config/ or a dedicated package

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 3 (controller/, service/, dto/).
```

- [x] Done

---

## Phase 7 — Backend: Command Lifecycle & Telemetry

**Goal:** Implement command state machine, telemetry storage, and location services.

### Prompt

```
In D:\CascadeProjects\yantrago\backend, implement command and telemetry modules.

Commands:
- src/main/java/com/yantrago/api/service/CommandService.java — create command, track lifecycle
- src/main/java/com/yantrago/api/service/CommandStateMachine.java — states: PENDING → QUEUED → SENT → ACK → DONE (or FAILED)
- src/main/java/com/yantrago/api/controller/CommandController.java — POST /api/v1/commands (ON/OFF), GET /api/v1/commands/{id}
- src/main/java/com/yantrago/api/dto/command/CommandRequest.java, CommandResponse.java, CommandStatusDto.java

Telemetry:
- src/main/java/com/yantrago/api/service/TelemetryService.java — store voltage, battery, GSM readings
- src/main/java/com/yantrago/api/controller/TelemetryController.java — GET /api/v1/telemetry/{machineId}
- src/main/java/com/yantrago/api/dto/telemetry/TelemetryDto.java, VoltageDto.java, BatteryDto.java

Locations:
- src/main/java/com/yantrago/api/service/LocationService.java — store GPS, query history
- src/main/java/com/yantrago/api/service/LocationPersistenceService.java — batch insert (adapt pattern from HarvestTracker)
- src/main/java/com/yantrago/api/controller/LocationController.java — GET /api/v1/locations/{machineId}, GET /api/v1/locations/{machineId}/history
- src/main/java/com/yantrago/api/dto/location/LocationDto.java

DeviceHeartbeatService — track device last-seen.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 3.
```

- [x] Done

---

## Phase 8 — Backend: RabbitMQ Integration

**Goal:** Wire up RabbitMQ producers and consumers between backend and gateway.

### Prompt

```
In D:\CascadeProjects\yantrago\backend, implement RabbitMQ integration.

Config:
- src/main/java/com/yantrago/api/config/RabbitMqConfig.java — declare exchanges, queues, bindings using QueueNames from shared module

Producers:
- src/main/java/com/yantrago/api/queue/CommandProducer.java — publish CommandMessage to command.queue
- (gateway will consume)

Consumers:
- src/main/java/com/yantrago/api/queue/DeviceEventConsumer.java — consume DeviceEventMessage from device.events queue
- src/main/java/com/yantrago/api/queue/TelemetryConsumer.java — consume TelemetryMessage, persist via TelemetryService
- src/main/java/com/yantrago/api/queue/AlertConsumer.java — consume AlertEventMessage, persist
- src/main/java/com/yantrago/api/queue/NotificationConsumer.java — async notification dispatch

Wire CommandService to publish via CommandProducer when a command is created.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 3 (queue/).
```

- [x] Done

---

## Phase 9 — Backend: Alerts, Notifications, Reports, Settings, Recharge, Audit

**Goal:** Implement the remaining business modules.

### Prompt

```
In D:\CascadeProjects\yantrago\backend, implement remaining modules.

Alerts:
- AlertService, AlertGenerationService (evaluate rules from telemetry), AlertController (/api/v1/alerts), dto/alert/AlertDto

Notifications:
- NotificationService, PushNotificationService (FCM/Expo/OneSignal-agnostic interface), EmailService (SMTP via spring-boot-starter-mail), SmsService (Twilio/MSG91-agnostic), NotificationController (/api/v1/notifications)

Settings:
- SettingsService, SettingsController (/api/v1/settings)

Reports:
- ReportService, ReportExportService (PDF via PDFBox, CSV), ReportController (/api/v1/reports), dto/report/ReportFilterDto

Recharge:
- RechargeService, RechargeController (/api/v1/recharges)

Audit:
- AuditLogService, AuditLogController (/api/v1/audit-logs)

Redis:
- src/main/java/com/yantrago/api/config/RedisConfig.java — cache config, session template

OpenAPI:
- src/main/java/com/yantrago/api/config/OpenApiConfig.java — Swagger/OpenAPI docs

Metrics:
- src/main/java/com/yantrago/api/config/MetricsConfig.java — Micrometer custom metrics

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 3.
```

- [x] Done

---

## Phase 10 — Backend: WebSocket Real-Time Push

**Goal:** Implement STOMP WebSocket for live location and command status updates.

### Prompt

```
In D:\CascadeProjects\yantrago\backend, implement WebSocket real-time push.

Config:
- src/main/java/com/yantrago/api/config/WebSocketConfig.java — STOMP WebSocket config, register /ws endpoint, message broker /topic

WebSocket:
- src/main/java/com/yantrago/api/websocket/TrackingWebSocketController.java — STOMP topics for machine updates
- src/main/java/com/yantrago/api/websocket/LocationBroadcastService.java — broadcast GPS updates to /topic/location/{machineId}
- src/main/java/com/yantrago/api/websocket/CommandBroadcastService.java — broadcast command status to /topic/command/{machineId}
- src/main/java/com/yantrago/api/websocket/WebSocketAuthInterceptor.java — JWT auth for WS connections

Wire TelemetryConsumer and DeviceEventConsumer to broadcast via these services.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 3 (websocket/).
```

- [x] Done

---

## Phase 11 — TCP Device Gateway (`device-gateway/`)

**Goal:** Create the gateway service and copy reused protocol code from HarvestTracker.

### Prompt

```
Create the device-gateway/ subproject at D:\CascadeProjects\yantrago\device-gateway.

1. device-gateway/build.gradle.kts — Spring Boot, Java 17, dependencies per YANTRAGO_PROJECT_STRUCTURE.md section 4 (spring-boot-starter-web, amqp, data-redis, actuator, jdbc; postgresql; micrometer-prometheus; project(:shared); test starters)
2. device-gateway/src/main/java/com/yantrago/gateway/YantraGoGatewayApplication.java — @SpringBootApplication
3. device-gateway/src/main/resources/application.yml — TCP port config (concox 5000, jt808 5001, fencing 5002), datasource, redis, rabbitmq placeholders
4. device-gateway/src/main/resources/application-dev.yml
5. device-gateway/src/main/resources/logback-spring.xml
6. device-gateway/README.md

COPY these files AS-IS from D:\CascadeProjects\Tracker\device-location-api\src\main\java\com\harvesttracker\devicelocationapi\tcp\ into device-gateway/src/main/java/com/yantrago/gateway/tcp/, changing the package from com.harvesttracker.devicelocationapi.tcp to com.yantrago.gateway.tcp:

- ProtocolHandler.java → tcp/ProtocolHandler.java
- ProtocolRouter.java → tcp/ProtocolRouter.java
- ConcoxV5ProtocolHandler.java → tcp/concox/ConcoxV5ProtocolHandler.java
- ConcoxV5TcpServer.java → tcp/concox/ConcoxV5TcpServer.java
- JT808ProtocolHandler.java → tcp/jt808/JT808ProtocolHandler.java
- JT808FrameParser.java → tcp/jt808/JT808FrameParser.java
- JT808MessageEncoder.java → tcp/jt808/JT808MessageEncoder.java
- JT808TcpServer.java → tcp/jt808/JT808TcpServer.java
- DeviceConnectionRegistry.java → tcp/DeviceConnectionRegistry.java
- DashcamConnectionRegistry.java → tcp/DashcamConnectionRegistry.java
- JT1076FrameAssembler.java → tcp/JT1076FrameAssembler.java
- JT1076RtpPacket.java → tcp/JT1076RtpPacket.java
- JT1076RtpReceiver.java → tcp/JT1076RtpReceiver.java

Create concox/ConcoxV5Constants.java and jt808/JT808Constants.java with protocol constants.

Only change the package declaration and imports. Do NOT rewrite the protocol logic.

Run `gradlew :device-gateway:build -x test` to verify it compiles.

Reference: YANTRAGO_PROJECT_STRUCTURE.md sections 4 and 11.
```

- [x] Done

---

## Phase 12 — Gateway: Services & Queue Integration

**Goal:** Add gateway business logic and RabbitMQ wiring.

### Prompt

```
In D:\CascadeProjects\yantrago\device-gateway, add services and queue integration.

Services (src/main/java/com/yantrago/gateway/service/):
- DeviceAuthService.java — authenticate devices on connect using IMEI lookup
- DeviceHeartbeatService.java — track heartbeats (adapt from HarvestTracker pattern)
- DeviceMappingCacheService.java — IMEI → device mapping (Redis-cached)
- TelemetryForwardService.java — forward parsed telemetry to RabbitMQ via TelemetryProducer
- CommandDispatchService.java — send commands to devices via DeviceConnectionRegistry.sendCommand()
- CommandResultService.java — process ACK/reply from devices, publish CommandResultMessage
- DeviceStateService.java — maintain last-known state in Redis

Queue (src/main/java/com/yantrago/gateway/queue/):
- CommandConsumer.java — consume CommandMessage from command.queue, dispatch via CommandDispatchService
- DeviceEventProducer.java — publish DeviceEventMessage to device.events exchange
- TelemetryProducer.java — publish TelemetryMessage to telemetry exchange
- QueueConfig.java — exchange/queue declarations (use QueueNames from shared)

Config (src/main/java/com/yantrago/gateway/config/):
- GatewayConfig.java — TCP port config, timeouts, thread pool
- RabbitMqConfig.java
- RedisConfig.java

Wire the ConcoxV5ProtocolHandler and JT808ProtocolHandler to call TelemetryForwardService and DeviceEventProducer when packets arrive.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 4.
```

- [x] Done

---

## Phase 13 — Gateway: New Fencing Protocol Handler

**Goal:** Implement the new YantraGO fencing protocol alongside the reused handlers.

### Prompt

```
In D:\CascadeProjects\yantrago\device-gateway, create the new fencing protocol handler.

Files under src/main/java/com/yantrago/gateway/tcp/fencing/:
- FencingProtocolHandler.java — implements ProtocolHandler; getProtocolName() returns "YANTRAGO_FENCING"; canHandle() checks for 0xAA 0x55 start bytes; handlePacket() parses login, heartbeat, GPS, voltage, battery, fencing state; forwards telemetry to TelemetryForwardService; returns ACK
- FencingParser.java — parse voltage, battery, fencing state from packet bytes
- FencingEncoder.java — build ON/OFF command packets for fencing machines
- FencingConstants.java — protocol constants (start bytes, command opcodes, field offsets)

Also create a FencingTcpServer.java that listens on port 5002 (same pattern as ConcoxV5TcpServer).

Register FencingProtocolHandler as a Spring @Component so ProtocolRouter auto-discovers it.

Write FencingProtocolHandlerTest.java under src/test/java/com/yantrago/gateway/tcp/fencing/ to verify packet parsing and command encoding.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 11.8.
```

- [x] Done

---

## Phase 14 — Device Simulator (`simulator/`)

**Goal:** Create a Java simulator for load testing the gateway.

### Prompt

```
Create the simulator/ subproject at D:\CascadeProjects\yantrago\simulator.

1. simulator/build.gradle.kts — Spring Boot, Java 17, dependency on project(:shared)
2. simulator/src/main/java/com/yantrago/simulator/SimulatorApplication.java
3. simulator/src/main/java/com/yantrago/simulator/ConcoxV5Simulator.java — simulates Concox V5 devices (login, heartbeat, GPS)
4. simulator/src/main/java/com/yantrago/simulator/JT808Simulator.java — simulates JT808/T98 devices
5. simulator/src/main/java/com/yantrago/simulator/FencingSimulator.java — simulates YantraGO fencing machines
6. simulator/src/main/java/com/yantrago/simulator/CommandResponder.java — simulates device ACK for ON/OFF commands
7. simulator/src/main/java/com/yantrago/simulator/SimConfig.java — device count, intervals, target host/port (configurable via application.yml)
8. simulator/src/main/resources/application.yml — default config (target localhost, 1 device, 10s interval)
9. simulator/README.md — how to run scenarios (single-device, hundred-devices, thousand-devices, ten-thousand-devices)

Use the same CRC-16, BCD, and packet-building logic patterns as the gateway handlers.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 10.
```

- [x] Done

---

## Phase 15 — Docker & Local Dev Infrastructure

**Goal:** Set up Docker Compose for local development and Dockerfiles for deployment.

### Prompt

```
Create the infra/ folder at D:\CascadeProjects\yantrago\infra.

Docker:
- infra/docker/docker-compose.dev.yml — services: postgres (postgres:15-postgis, port 5432), redis (redis:7-alpine, 6379), rabbitmq (rabbitmq:3-management, 5672+15672), prometheus (9090), grafana (3002). Volumes for data persistence. Healthchecks.
- infra/docker/docker-compose.staging.yml — same plus backend, gateway, admin-web services
- infra/docker/Dockerfile.backend — multi-stage build: eclipse-temurin:17-jdk-alpine for build, eclipse-temurin:17-jre-alpine for runtime. Expose 8080.
- infra/docker/Dockerfile.gateway — same pattern. Expose 5000 5001 5002.
- infra/docker/Dockerfile.admin-web — node:20-alpine build, node:20-alpine runtime. Expose 3001.

Monitoring:
- infra/monitoring/prometheus.yml — scrape configs for backend (8080) and gateway (8081)
- infra/monitoring/grafana/datasources/prometheus.yml
- infra/monitoring/grafana/dashboards/ — placeholder dashboard JSON files (api-overview, gateway-metrics, device-health, command-latency)
- infra/monitoring/alerting/alert-rules.yml

Scripts:
- infra/scripts/deploy.sh, rollback.sh, seed-db.sh

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 9.
```

- [x] Done

---

## Phase 16 — Mobile App (`mobile/`)

**Goal:** Scaffold the Flutter mobile app with core structure and auth flow.

### Prompt

```
Create the mobile/ Flutter app at D:\CascadeProjects\yantrago\mobile.

Run `flutter create --org com.yantrago --project-name yantrago mobile` (or create manually if flutter CLI not available).

Set up the structure per YANTRAGO_PROJECT_STRUCTURE.md section 5:

lib/
  main.dart, app.dart
  core/config/app_config.dart, theme.dart
  core/network/api_client.dart (Dio), websocket_client.dart, interceptors/auth_interceptor.dart, error_interceptor.dart
  core/auth/auth_service.dart, token_manager.dart, auth_state.dart
  core/storage/secure_storage.dart
  core/utils/date_utils.dart, validators.dart
  features/auth/pages/login_page.dart, splash_page.dart, providers/auth_provider.dart
  features/dashboard/pages/dashboard_page.dart, widgets/ (machine_status_card, battery_widget, voltage_widget, gsm_status_widget, recharge_status_widget), providers/dashboard_provider.dart
  features/machines/pages/machine_list_page.dart, machine_detail_page.dart, widgets/machine_card.dart, on_off_button.dart, providers/machine_provider.dart
  features/map/pages/machine_map_page.dart, widgets/machine_marker.dart, providers/location_provider.dart
  features/commands/widgets/command_status_widget.dart, providers/command_provider.dart
  features/alerts/pages/alerts_page.dart, providers/alerts_provider.dart
  features/settings/pages/settings_page.dart, providers/settings_provider.dart
  features/history/pages/activity_history_page.dart, providers/history_provider.dart
  features/profile/pages/profile_page.dart, providers/profile_provider.dart
  models/ (user, machine, device_state, location, telemetry, command, alert, notification).dart
  routing/app_router.dart (GoRouter with auth guards)

pubspec.yaml — dependencies: flutter_riverpod, go_router, dio, flutter_secure_storage, web_socket_channel, stomp_dart_client, intl, json_annotation, google_maps_flutter
analysis_options.yaml

Implement the login page and auth flow fully. Other pages can be scaffolds with placeholder UI.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 5.
```

- [x] Done

---

## Phase 17 — Admin Web (`admin-web/`)

**Goal:** Scaffold the Next.js admin portal with auth and dashboard.

### Prompt

```
Create the admin-web/ Next.js app at D:\CascadeProjects\yantrago\admin-web.

Use Next.js 14+ App Router with TypeScript.

Structure per YANTRAGO_PROJECT_STRUCTURE.md section 6:

src/app/
  layout.tsx, page.tsx
  (auth)/login/page.tsx, layout.tsx
  (super-admin)/dashboard, organizations, admins, machines, customers, reports, audit-logs, settings, layout.tsx
  (admin)/dashboard, customers, machines, reports, alerts, layout.tsx

src/components/ui/ (buttons, cards, tables), charts/, maps/, tables/, forms/
src/lib/api-client.ts, auth.ts, websocket.ts, utils.ts
src/hooks/use-auth.ts, use-machines.ts, use-websocket.ts
src/stores/auth-store.ts, app-store.ts (Zustand)
src/types/api.ts, models.ts, index.ts

package.json — next 14, react 18, typescript, tailwindcss, zustand, @tanstack/react-query, recharts, leaflet, @stomp/stompjs, axios
tailwind.config.ts, tsconfig.json, next.config.js, .eslintrc.js
.env.development, .env.staging, .env.production

Implement the login page and dashboard layout fully. Other pages can be scaffolds.

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 6.
```

- [x] Done

---

## Phase 18 — CI/CD Pipelines

**Goal:** Set up GitHub Actions workflows.

### Prompt

```
Create .github/workflows/ at D:\CascadeProjects\yantrago.

Files:
- backend-ci.yml — on PR: setup Java 17, Gradle cache, run `gradlew :backend :shared :device-gateway :simulator build`, run tests
- gateway-ci.yml — on PR: build and test gateway + shared
- mobile-ci.yml — on PR: setup Flutter, flutter analyze, flutter test
- admin-web-ci.yml — on PR: setup Node 20, npm ci, npm run build, npm run lint
- deploy-staging.yml — on merge to main: build Docker images, deploy to staging
- deploy-production.yml — on release tag: deploy to production
- security-scan.yml — run Trivy on dependencies
- load-test.yml — manual trigger: run simulator against staging

Reference: YANTRAGO_PROJECT_STRUCTURE.md section 14.
```

- [x] Done

---

## Phase 19 — Vultr Infrastructure Scripts

**Goal:** Create deployment scripts and server setup configs for Vultr.

### Prompt

```
Create infra/vultr/ at D:\CascadeProjects\yantrago\infra\vultr.

Server setup scripts:
- server-setup/db-server-setup.sh — install PostgreSQL 15 + PostGIS, create db/user, configure
- server-setup/app-server-setup.sh — install Java 17 + Nginx, create yantrago user, directories
- server-setup/gateway-server-setup.sh — install Java 17, create user, dirs
- server-setup/redis-server-setup.sh — install Redis, configure password
- server-setup/rabbitmq-server-setup.sh — install RabbitMQ, create user, enable management

Systemd units:
- systemd/yantrago-backend.service
- systemd/yantrago-gateway.service
- systemd/yantrago-rabbitmq.service

Nginx configs:
- nginx/nginx.conf, nginx.websocket.conf, nginx.ssl.conf

Firewall rules:
- firewall/db-server.ufw, app-server.ufw, gateway-server.ufw, redis-server.ufw

Deploy scripts:
- deploy/deploy-backend.sh, deploy-gateway.sh, deploy-admin-web.sh, rollback.sh

README.md — Vultr deployment guide summary.

Reference: YANTRAGO_PROJECT_STRUCTURE.md sections 16, 17, 18.
```

- [x] Done

---

## Phase 20 — Integration Testing & Final Verification

**Goal:** End-to-end tests across all components.

### Prompt

```
In D:\CascadeProjects\yantrago, add integration tests and verify the full stack.

Backend tests:
- src/test/java/com/yantrago/api/controller/AuthControllerTest.java — login flow
- src/test/java/com/yantrago/api/controller/MachineControllerTest.java — CRUD with tenant isolation
- src/test/java/com/yantrago/api/controller/CommandControllerTest.java — command lifecycle
- src/test/java/com/yantrago/api/controller/TenantIsolationTest.java — verify cross-tenant access blocked
- src/test/java/com/yantrago/api/service/CommandServiceTest.java
- src/test/java/com/yantrago/api/service/AlertServiceTest.java
- src/test/java/com/yantrago/api/service/TelemetryServiceTest.java

Gateway tests:
- src/test/java/com/yantrago/gateway/tcp/ConcoxV5ProtocolHandlerTest.java (copied)
- src/test/java/com/yantrago/gateway/tcp/JT808FrameParserTest.java
- src/test/java/com/yantrago/gateway/tcp/FencingProtocolHandlerTest.java
- src/test/java/com/yantrago/gateway/service/CommandDispatchServiceTest.java
- src/test/java/com/yantrago/gateway/service/TelemetryForwardServiceTest.java

Use Testcontainers for Postgres, Redis, RabbitMQ in integration tests.

Run `gradlew build` from root to verify all modules compile and tests pass.
Run `docker compose -f infra/docker/docker-compose.dev.yml up` to verify local stack starts.

Reference: YANTRAGO_PROJECT_STRUCTURE.md sections 3 (test/) and 4 (test/).
```

- [x] Done

---

## Progress Tracker

| Phase | Description | Status |
|-------|-------------|--------|
| 0 | Project skeleton | [x] |
| 1 | Shared library | [x] |
| 2 | Database migrations | [x] |
| 3 | Backend foundation | [x] |
| 4 | Entities & repositories | [x] |
| 5 | Security & auth | [x] |
| 6 | Core CRUD | [x] |
| 7 | Commands & telemetry | [x] |
| 8 | RabbitMQ integration | [x] |
| 9 | Alerts, notifications, reports | [x] |
| 10 | WebSocket push | [x] |
| 11 | TCP device gateway (reused code) | [x] |
| 12 | Gateway services & queue | [x] |
| 13 | Fencing protocol handler | [x] |
| 14 | Device simulator | [x] |
| 15 | Docker & local infra | [x] |
| 16 | Mobile app (Flutter) | [x] |
| 17 | Admin web (Next.js) | [x] |
| 18 | CI/CD pipelines | [x] |
| 19 | Vultr infra scripts | [x] |
| 20 | Integration testing | [x] |

---

## Notes

- **Project location:** `D:\CascadeProjects\yantrago` (NOT inside the existing Tracker folder)
- **Reused code source:** `D:\CascadeProjects\Tracker\device-location-api\src\main\java\com\harvesttracker\devicelocationapi\tcp\`
- **Build verification:** Run `gradlew build` from root after each phase to catch issues early
- **Do not skip phases** — later phases depend on earlier ones
- **Commit after each phase** with a descriptive message

---

# Notification Alerts — Full Production Implementation Plan

**Prepared:** 2026-09-09

**Status:** Planning only; no notification implementation authorized or completed.

**Scope:** Backend API, shared message contracts, existing gateway integration boundaries, database, Flutter mobile, and operational configuration.

**Authority:** Follow AGENTS.md and the architecture in YANTRAGO_PROJECT_STRUCTURE.md, especially sections 8 and 11.

**Execution rule:** Implement one notification phase at a time, verify it, STOP, and ask the user whether to start the next phase. Approval of this document alone does not authorize implementation.

This section is the consolidated notification plan. It supersedes the earlier conversational A/B/D/C/E proposal. The original project tracker above records historical scaffolding work; its completed Phase 9 entry does not mean production notification delivery is complete.

## N1. Selected Recommendations and Scope

| Decision | Selected recommendation | Reason |
|----------|-------------------------|--------|
| Event scope | Telemetry-rule alerts, device offline/recovery, SIM expiry reminders | Matches the user's selected scope; recovery closes the corresponding incident |
| Recipients | Machine assignees only | Do not notify all organization users or automatically copy administrators |
| Channels | Durable in-app inbox first, FCM push second | Useful without external delivery; push extends it when the app is backgrounded |
| Push provider | Firebase Cloud Messaging | FCM messaging has no per-message charge; Firebase setup and platform requirements still apply |
| Paid channels | Email, SMS, WhatsApp deferred | No automatic paid fallback; require separate product and cost approval |
| Architecture | Existing backend plus TCP gateway only | No notification microservice; workers and schedulers live in the backend |
| Reliable processing | PostgreSQL transactions/outbox plus RabbitMQ | Avoid losing events between a committed database transaction and publication |
| Inbox identity | One item per event and recipient, independent of delivery channels | Retrying push or sending to two phones must not create duplicate inbox items |
| Rollout order | Reliable backend -> rule sources -> inbox APIs -> mobile inbox -> real push | Every UI phase receives its backend dependencies first |
| Realtime | Secure user-scoped WebSocket invalidation, with REST reconciliation | WebSocket is an optimization, not the source of notification history |

FCM is not a promise that the whole deployment is free: existing servers, data connectivity, monitoring, and optional services have costs. iOS also needs APNs configuration and applicable Apple developer membership. Verify current provider terms when setting up production. Do not introduce mock delivery that reports production success.

**Excluded:** Command ACK/DONE/FAILED notifications, automatic machine control from alerts, marketing campaigns, admin notification dashboards, custom rule-expression languages, automatic SMS escalation, and protocol parser rewrites.

**Capability-gated:** Geofence, SOS, tamper, power-cut, and fence-fault events are not assumed available merely because an alert type name exists. Enable them only after confirming reliable input fields, documented device semantics, and test fixtures. Geofence evaluation requires an approved boundary model and GPS-quality rules; it is a separately approved extension if those prerequisites do not exist.

## N2. Verified Baseline and Corrections

The following findings are from source inspection, not a runtime audit or database inspection:

- V9 and V10 define alert/rule and notification/preference tables; actual deployment migration state must be checked before rollout.
- AlertConsumer and NotificationConsumer both consume ALERT_EVENT_QUEUE. They compete for messages rather than each receiving every event. Distribution is not guaranteed to be exactly 50/50.
- NotificationConsumer uses a random organization UUID, does not resolve a recipient, and references an incoming alert ID that AlertConsumer does not preserve. Foreign-key failures and lost processing are possible.
- AlertGenerationService is present but no caller was found. Its suppression check examines only one arbitrary alert, uses acknowledgement as incident state, and has no race-safe uniqueness enforcement.
- Rule evaluation saves alerts but does not atomically arrange downstream notification delivery.
- NotificationService supplies null destination values. PushNotificationService returns a generated mock ID and logs tokens. Real provider acceptance must replace mock success, and token logging must be removed during implementation.
- Existing notification endpoints expose entities and lack explicit endpoint permission checks; tenant-only filtering is insufficient for customer access to other assignees' alerts or notifications.
- The mobile alert list is basic; the notification model expects read state absent from the current backend entity. The inspected pubspec has no Firebase messaging dependency.
- No backend scheduled jobs were found for rule evaluation, offline sweeps, or expiry reminders.
- Assignments link machines to customers, not directly to users: machine_assignments.customer_id -> customers.user_id -> users.id.
- The highest migration version found during planning is V20. Allocate the next unused versions at implementation time; do not reuse V16/V17 or edit applied migrations.

**Corrections to the earlier proposal:**

1. Keep the existing direct alert exchange. Multiple queues can bind to a direct exchange when broadcast semantics are actually needed; changing an existing exchange's type would cause a RabbitMQ declaration conflict.
2. Do not independently persist an alert and create its notification in parallel consumers: notification creation could race the alert foreign key. Persist first, publish a committed domain event second.
3. Do not save an alert in the rule evaluator and then save the same incident again in AlertConsumer. Use one canonical alert application service for all sources.
4. Put inbox schema, read/unread endpoints, preferences, and security before the mobile inbox phase.
5. Keep retry, idempotency, and dead-letter behavior in the foundation rather than postponing reliability until Firebase.
6. Keep database deduplication authoritative. Redis can accelerate scheduling/caching, but cache loss must not recreate every incident.
7. Provider acceptance is not delivery, user reading, incident acknowledgement, or device-command success.

## N3. Event Catalog and Notification Policy

All numeric settings below are recommended initial defaults, not validated hardware limits. Thresholds and units must be checked against actual device telemetry before enabling a rule.

| Event | Source and trigger | Severity | Initial notification policy |
|-------|--------------------|----------|-----------------------------|
| LOW_BATTERY | Valid battery reading below configured threshold for a sustained interval | WARNING | Inbox plus push once per incident |
| BATTERY_CRITICAL | Valid reading below a separate critical threshold | CRITICAL | Inbox plus immediate push; escalation of the existing battery incident |
| VOLTAGE_DROP | Output voltage below a machine-specific safe threshold while confirmed operating | WARNING or CRITICAL | Inbox plus push; suppress expected low output when intentionally OFF |
| WEAK_GSM | Supported signal metric below its configured limit for a sustained interval | WARNING | Inbox by default; push opt-in |
| DEVICE_OFFLINE | No fresh validated liveness evidence beyond configured heartbeat timeout | WARNING | Inbox plus push once; socket disconnect alone is not the final trigger |
| DEVICE_ONLINE | Fresh liveness evidence after an established offline incident | INFO | Resolve offline incident; inbox plus optional recovery push |
| CONDITION_RECOVERED | Fresh valid readings clear an active telemetry incident with hysteresis | INFO | Resolve incident; inbox plus optional recovery push |
| SIM_EXPIRING | Effective expiry reaches 7-day, 3-day, or 1-day reminder boundary | INFO/WARNING | Inbox plus push, once per expiry cycle and reminder boundary |
| SIM_EXPIRED | Effective expiry has passed | WARNING | Inbox plus push once per expiry cycle |

### Detection rules

- Example battery defaults: warning below 20%, critical below 10%, recovery above 25%, with sustained valid readings for 60 seconds. Enable only if telemetry is genuinely a percentage; do not treat raw voltage as percentage.
- Do not invent a universal fence-voltage threshold. Validate output-voltage units and machine operating state; keep the rule disabled if those prerequisites are unavailable.
- Treat null, invalid, stale, or unsupported telemetry as unknown, not zero and not recovered. Report stale telemetry separately from device connectivity.
- Offline default: max(three expected heartbeat intervals, five minutes), evaluated approximately every minute. Honor actual per-device reporting settings.
- Ignore brief disconnect/reconnect flapping. Recovery requires fresh evidence newer than the offline transition; delayed queue messages must not revive an offline machine.
- Persist device event time and server receipt time separately. Reject/quarantine unreasonable future timestamps and prevent older observations from overwriting newer state.
- Events buffered during a platform outage can be stored as delayed history, but must not generate misleading current-state pushes. Apply event-age limits and re-evaluate current state before dispatch.
- Do not schedule expiry reminders when valid_until is unknown. Determine effective entitlement from recharge business semantics, not every historical recharge row independently.
- For existing recharge data, prefer the latest effective successful entitlement; confirm whether renewals extend or replace previous validity before activation. Until that is established, keep expiry reminders disabled rather than guess.
- Evaluate reminder dates in the configured organization timezone, defaulting explicitly to UTC. On first observation inside a reminder window, emit only the nearest applicable reminder, not all missed milestones.
- Renewal invalidates unsent old-cycle reminders. Reminder uniqueness includes the effective expiry cycle and milestone; do not emit daily expired notifications by default.

### Incident lifecycle and noise control

Track condition state separately from human acknowledgement: OPEN -> RESOLVED, with acknowledgedBy/acknowledgedAt orthogonal to state. Reading an inbox item changes only that user's read state. Acknowledging an incident does not prove a fault is fixed and must not immediately re-arm the same condition.

Use a stable incident key containing organization, machine/device, rule identity, and condition category. A partial unique constraint or equivalent locked state record permits only one open incident per key. Repeated violations update lastObservedAt, value snapshot, and occurrence count. Notify on opening, meaningful severity escalation, and recovery; reminders are separately rate-limited. Changes to rule thresholds must not generate a duplicate storm.

## N4. Recipient Resolution and Security

Resolve recipients using server-owned relationships:

`machine -> active machine_assignments -> active customer -> linked active user in the same organization`

An active assignment has assigned_at <= the applicable time and unassigned_at absent or later. Deduplicate users when multiple eligible links exist. Snapshot event-time recipients when applying the event, and revalidate current access before creating the inbox item, dispatching, and serving machine-linked details. Do not transfer an old queued notification to a new assignee. Late events require assignment-history checks.

- An unassigned machine, inactive customer, absent customer-user link, inactive user, or inconsistent organization mapping yields no customer recipient. Record a safe reason and metric; never fall back to all administrators.
- API organization and current user come from validated JWT context. Request bodies cannot select the organization or recipient.
- Queue workers have no HTTP JWT. Resolve/validate ownership from trusted persisted machine/device/event records; do not invent a tenant or bypass checks with a synthetic administrator context.
- Notification list/detail/read/count queries must include both organization and current recipient. Alerts additionally require machine-assignment access for customer callers.
- Use explicit RBAC permissions in the project's resource:action convention. Retain notification:read and alert:read where appropriate; add narrowly scoped notification:write, alert:acknowledge, and alert:configure permissions through reviewed migrations if needed.
- A user's ability to read their notifications must not grant permission to dispatch messages, edit alert thresholds, or acknowledge incidents. Verify permissions are actually loaded into JWT authorities, not only inserted into role_permissions.
- Existing organization-wide notification and arbitrary-user preference endpoints need explicit administrative policy or safe deprecation. The customer app uses only self-scoped endpoints.
- Use private user destinations, such as /user/queue/notifications, not a publicly subscribable /topic/notifications/{userId}. Validate STOMP CONNECT/SUBSCRIBE/SEND, expire sessions, and prevent spoofed destinations.
- Push payloads contain an opaque notification ID, schema version, and minimal generic display text. Never include credentials, raw telemetry dumps, exact location, phone numbers, or arbitrary navigation URLs.
- On assignment revocation, hide/restrict old machine-linked detail for the former assignee and suppress pending pushes. Already displayed OS notifications cannot reliably be recalled; minimize lock-screen content accordingly.
- Audit acknowledgements, rule/preference changes, token lifecycle changes, and privileged replay actions. Do not log tokens, provider credentials, or unsanitized provider response bodies.

## N5. Target Processing Architecture

```text
Gateway observations --existing shared contracts/RabbitMQ--> Backend ingestion
Backend telemetry evaluation / offline sweep / expiry scheduler
                              |
                              v
Canonical alert application service
  DB transaction: deduplicate input + update incident + snapshot eligible recipients
                  + write committed alert transition to outbox
                              |
                       Outbox publisher
                 confirms + mandatory routing checks
                              |
             Dedicated notification event queue (RabbitMQ)
                              |
Notification planner: revalidate access + apply preferences/templates
  DB transaction: unique inbox item + optional push delivery jobs + outbox
                    |                         |
          REST inbox / WS invalidation    Dedicated push queue
                                              |
                               Claim delivery -> FCM -> record outcome
```

- Retain ALERT_EVENT_QUEUE for raw alert ingestion with one logical handler; replicas of that same handler can compete safely. Remove the unrelated notification handler from that queue during controlled rollout.
- Publish AlertTransitionMessage and NotificationDispatchMessage contracts from shared/, with schemaVersion, stable eventId, occurredAt, correlationId, and canonical record identifiers. Final contract names may follow existing conventions.
- Never let an incoming arbitrary alertId become an unverified foreign-key reference. Retain a separate source event identity and resolve the canonical persisted alert.
- If an input contract lacks stable event identity, add backward-compatible fields and coordinate producer rollout. For legacy producers, define/test a bounded deterministic source fingerprint; random IDs generated anew on redelivery do not provide idempotency.
- Existing direct exchange declarations remain unchanged. Declare new notification exchanges/queues as needed with persistent messages, bounded retries, and DLQs. All constants/contracts live in shared/.
- Outbox rows are committed with business state. Publishers use bounded row claiming/leases, retry after failure, and mark publication complete only after confirmation with no routing return. A crash can republish, so consumers must be idempotent.
- Acknowledge RabbitMQ delivery after successful durable work. Let transient failures enter bounded retry; reject malformed poison messages into quarantine/DLQ with safe diagnostics. Do not catch-and-log and silently acknowledge failures.
- Initial retry policy: delayed retries around 30 seconds, 2 minutes, and 10 minutes with jitter where supported; honor provider Retry-After. Stop when event TTL expires or the attempt budget is exhausted.
- Push attempts run outside long database transactions. Use a claimed delivery state with lease expiry to recover crashed workers without uncontrolled parallel sends.
- End-to-end processing is at-least-once, not exactly-once. Unique records prevent duplicate inbox items; a crash after FCM acceptance but before recording its response can still duplicate external delivery. Use stable notification IDs and client deduplication and document that limitation.
- WebSocket invalidations happen after commit and carry identifiers/count hints only. Reconnect and app resume fetch authoritative state through REST.
- Redis is optional for rule caching, short-lived rate limits, and coalescing. Database constraints and worker leases remain authoritative across replicas and cache restarts.

## N6. Database and Migration Plan

Use additive Flyway migrations only in database/migrations/. Allocate consecutive unused versions after the current repository/deployed version check; V21 is only a planning candidate. Review section 8 of the architecture before final DDL. No queue-only SQL migrations, no table drops, no rewriting old migration checksums.

| Table/change | Purpose and essential fields |
|--------------|------------------------------|
| alerts extension | incident key/state, first/last observed times, resolved_at, occurrence count, observed value/unit snapshot, rule version; preserve acknowledgement fields |
| alert_rules extension | Validated typed configuration for thresholds, sustain/recovery windows, severity, scope, enabled state and version |
| alert_rule_states | Durable per-machine/rule observation and pending-violation/recovery state when needed for restart-safe windows |
| event_outbox | UUID, organization, event type/schema, aggregate ID, unique event identity, payload, timestamps, publication attempts, next attempt, claim lease |
| processed_events | Stable consumer/event uniqueness for replay protection; retention must cover supported replay horizons |
| notification_inbox | UUID, organization, user, event ID, optional alert/machine IDs, type, severity, rendered title/body, locale/template version, created_at, read_at |
| notifications extension | Reuse current table for channel delivery jobs linked to inbox; channel, recipient target, state, attempts, next attempt, expiry, claim lease, sanitized failure code |
| notification_attempts | UUID, delivery ID, attempt number, timestamps, provider result/message ID, categorized error; no raw secrets |
| user_push_tokens | UUID, organization/user, installation ID, protected token, token fingerprint, platform/environment, active state, last_seen_at, revoked_at |
| notification_preferences extension | Deterministic per-user/type/channel preferences, timezone/quiet-hour policy if enabled |
| expiry reminder identity | Unique device/entitlement-cycle/milestone key using an existing event identity table or a focused reminder table |

Keep notification_inbox separate from the existing channel-delivery table to avoid changing legacy row semantics in place. Link new delivery jobs to inbox rows; historical deliveries can retain a nullable link. Do not fabricate read state or recipients for malformed legacy rows. Review legacy data and handle remediation separately with explicit approval before any deletion.

**Constraints/indexes:** UUID primary keys; tenant-aware relationships or equivalent verified constraints; unique inbox (organization_id, user_id, event_id); unique delivery (inbox_id, channel, destination identity); unique attempt (delivery_id, attempt_number); unique active incident; inbox pagination index (organization_id, user_id, created_at, id); unread partial index; due outbox/delivery indexes; assignment and expiry lookup indexes.

The existing UNIQUE(user_id, channel, alert_type) allows multiple NULL alert_type rows in PostgreSQL. Use a validated explicit all-types value, partial unique indexes, or NULLS NOT DISTINCT with an intentional migration and duplicate-data preflight. Do not silently discard duplicates.

Use Instant/UTC for new Java event timestamps with PostgreSQL TIMESTAMPTZ. Audit integration boundaries with existing LocalDateTime fields and avoid a repository-wide timestamp refactor. Keep time-series telemetry partitioned and batch-oriented; do not query historical partitions for every raw packet when validated current observations suffice.

Retention recommendations: inbox 90 days and attempt diagnostics 30 days initially, subject to business/audit requirements. Keep unresolved incidents and their dependencies. Determine longer audit/incident retention before enabling cleanup. No automatic destructive cleanup is authorized by this document.

## N7. Backend API Contract

All routes are under /api/v1, authenticated, RBAC-protected, tenant-isolated, and implemented Controller -> Service -> Repository. Return DTOs/records, never JPA entities. Validate request bodies with @Valid and Bean Validation; map domain errors using GlobalExceptionHandler to {error, message, timestamp, status}.

| Endpoint | Behavior |
|----------|----------|
| GET /notifications/mine | Current user's paginated inbox, bounded page size, filters for read/type/severity/machine/date |
| GET /notifications/{id} | Recipient-owned notification detail, with current access checks |
| GET /notifications/unread/count | Authoritative current-user count using the same visibility rules as inbox |
| PATCH /notifications/{id}/read | Idempotently mark the user's item read |
| POST /notifications/read-all | Mark visible items through a supplied validated cutoff; do not consume items arriving later |
| GET /notifications/preferences | Current user's effective preferences and supported event catalog |
| PUT /notifications/preferences | Validated updates to the current user's supported preferences |
| PUT /notifications/push-tokens/{installationId} | Idempotent registration/refresh for the authenticated user; never trust body user/org IDs |
| DELETE /notifications/push-tokens/{installationId} | Revoke the current user's installation binding on logout |
| GET /alerts and GET /alerts/{id} | Existing routes hardened for assignment/RBAC, combined filters and pagination |
| POST /alerts/{id}/acknowledge | Authorized, audited and idempotent acknowledgement; does not resolve the condition |
| GET/POST/PATCH /alert-rules | Narrowly authorized tenant rule management with supported typed conditions only |

Use stable ordering createdAt DESC, id DESC and consistent page metadata; cap page size at 100 by default. Combine filters rather than ignoring machineId when an unacknowledged filter is selected. Do not expose raw provider errors, device tokens, or administrative dispatch capabilities through customer DTOs.

Preference defaults: durable inbox remains enabled, primary warning/critical push enabled once OS permission is granted, recovery push and weak-signal push disabled initially. A channel-wide disable overrides event defaults. No quiet hours initially; if added, use the user's explicit timezone and define opt-in critical exceptions. Notification preferences never disable incident recording.

## N8. Flutter Experience and FCM Integration

### In-app experience

- Enhance the existing Alerts tab instead of adding a confusing second bottom-navigation destination. Provide Notifications and Active Alerts views inside it, plus an unread badge.
- Feature folders: features/notifications/{pages,providers,widgets,models}; reuse existing alerts components where appropriate. Keep Riverpod and Dio; no direct API calls from widgets.
- Provide paginated list, unread filter, machine/severity filters, pull-to-refresh, and notification detail. Show machine display name only after authorized API retrieval, clear timestamps, status, and a human-readable message.
- Use AsyncValue.when for loading/error/data, friendly errors, empty states, accessible severity labels, and retry. Never display raw exception strings.
- Routes: /app/notifications/:id and /app/alerts/:id as authenticated drill-down screens; machine links use /app/machines/:id. context.push for drill-down; context.go for tab switching.
- Mark read separately from acknowledge. Disable repeated mutation taps, reconcile with backend on errors, and refresh badge on app resume and reconnect.
- Clear tenant/user-scoped providers and subscriptions on logout or account switch. The app must never briefly show a previous account's cached inbox.
- Defer offline persistence unless needed; initial offline mode shows an honest connection state and safely retained in-memory data for the current session only.

### Real push

- Use the maintained Firebase Admin Java SDK on the backend, subject to dependency compatibility review, rather than implementing OAuth signing manually. Put it behind a small PushProvider interface; FCM is the only initial implementation.
- Flutter requires compatible stable firebase_core and firebase_messaging; add flutter_local_notifications only for foreground/local presentation needs. Pin compatible stable versions published at least seven days earlier when implementation begins.
- Separate Firebase environments for development/staging and production. Obtain credentials securely outside source control; never paste service-account keys into this document or logs.
- Configure Android application identity/notification channels and runtime permission for Android 13+. Configure iOS bundle identity, APNs credentials, entitlements, and notification permission; physical iOS verification needs appropriate macOS signing/build access.
- Register tokens after authentication, handle refresh, multiple installations, account switching, reinstall, and logout. Do not treat possession of a caller-supplied installation UUID as authority to revoke another user's registration.
- Encrypt retrievable token values at rest with managed secret material; use a fingerprint for uniqueness/diagnostics. Reject inappropriate cross-account rebinding and define a safe verified installation-transfer flow for shared phones.
- Permanently invalid tokens are deactivated; transient network/provider failures retry; permission denial is not a backend delivery success. No token means inbox-only, not failed incident creation.
- Foreground: show at most one local/in-app presentation per event. Background/terminated: use platform-supported notification presentation and tap handling; do not depend on a long-lived socket or guaranteed background Dart execution.
- Notification taps resolve an allowlisted in-app route by notification ID after login and authorization. Preserve pending navigation during auth initialization. Handle removed/reassigned machines with a friendly unavailable message.
- Prevent duplicate local notifications when the OS already displays the push. Use stable notification identity and appropriate TTL; do not collapse unrelated critical incidents under one global collapse key.
- Suggested push TTL: 15 minutes for current telemetry/connectivity warnings, up to 24 hours for expiry reminders; revalidate whether a queued condition is still actionable before sending.
- FCM acceptance means ACCEPTED_BY_PROVIDER. Set DELIVERED only with a supported receipt; app read state remains separate. Force-stopped apps, denied permissions, battery restrictions, and connectivity can prevent timely presentation.
- Never claim push is a guaranteed safety alarm or automatically bypass Do Not Disturb. No direct ON/OFF action from a push in this scope.

## N9. Sequential Implementation Phases

Each phase includes focused tests, build verification, backward-compatible deployment, and a user verification stop. These are new notification phases, independent of the historical project phase numbering.

### Notification Phase 1 — Reliable Alert Foundation

**Goal:** Durable, tenant-correct incident ingestion and committed event publication without external sends.

1. Recheck schema, security authority loading, assignment history, device telemetry units, and shared contracts against the latest branch. Add regression tests for competing queue consumers, invalid tenant/alert linkage, duplicate events and swallowed failures.
2. Add additive incident/outbox/idempotency schema and canonical alert service. Preserve existing alert API shapes until DTO extensions are ready.
3. Remove the unrelated notification listener from raw alert ingestion; retain existing exchange types. Route all raw alert persistence through the canonical service.
4. Add versioned shared transition contracts, outbox publisher, dedicated notification queue, retry/DLQ, and durable replay protection.
5. Harden affected alert reads/acknowledgements with RBAC and assignment checks; disable inaccessible legacy manual dispatch for ordinary users without reporting fake success.
6. Deploy with notification delivery disabled. Verify canonical alerts and pending committed transitions through integration tests/staging diagnostics.

**Acceptance:** Replaying one source event produces one incident transition; a database rollback publishes nothing; a broker outage leaves recoverable outbox work; a foreign-tenant/customer request cannot read or acknowledge another assignee's alert. No new push is sent.

**STOP:** Ask the user to verify Phase 1 and explicitly approve Phase 2.

### Notification Phase 2 — Rule Evaluation, Offline and Expiry Sources

**Goal:** Produce correct alerts from the selected real event sources.

1. Replace the one-row suppression query with durable incident lifecycle handling, hysteresis, sustain windows and escalation/recovery transitions.
2. Connect telemetry evaluation after successful ingestion, using current valid observations and restart-safe state. Preserve high-volume batch writes and avoid N+1 historical queries.
3. Add bounded multi-instance-safe schedulers for offline detection and expiry milestones. Use row claiming/locking and uniqueness, not one scheduler per machine.
4. Implement liveness ordering, reconnect grace, unknown-device handling, expiry-cycle identity, renewal cancellation, and no-recipient behavior.
5. Add typed rule configuration and narrow RBAC/validation with audit history. Leave unsupported metrics and unverified voltage/expiry semantics disabled.
6. Add tests using a controlled Clock for thresholds, window boundaries, delayed observations, flapping, scheduler overlap, renewal, and timezone boundaries.

**Acceptance:** Sustained violation creates one incident; healthy recovery resolves it once; brief noise does not notify; scheduler restarts do not duplicate reminders; no alert claims voltage failure while intentionally OFF. Events remain durable with external delivery disabled.

**STOP:** Ask the user to verify Phase 2 and explicitly approve Phase 3.

### Notification Phase 3 — Recipient Inbox, Preferences and APIs

**Goal:** A usable, secure backend inbox with all contracts required by mobile.

1. Add inbox/delivery/attempt schema and deterministic preference uniqueness, with migration preflight for existing data.
2. Implement event-time recipient snapshots and current-access revalidation through assignment -> customer -> user; add per-event/recipient database deduplication.
3. Add templates for all enabled event types, safe display content, locale/template version, fallback text and read-state DTOs.
4. Implement notification event consumer, preferences, pagination/filtering, unread count, mark-read/read-all and private WebSocket invalidation.
5. Add explicit permissions, protect or deprecate legacy broad endpoints, and ensure backend authority generation includes required permissions.
6. Keep push delivery off. Exercise inbox delivery end to end using supported simulated observations rather than a public test-alert endpoint.

**Acceptance:** Eligible assignees each get exactly one inbox item; non-assignees/admins receive no automatic copy; revoked access suppresses queued fanout; read state is private and idempotent; REST and socket access reject cross-user/tenant requests.

**STOP:** Ask the user to verify Phase 3 and explicitly approve Phase 4.

### Notification Phase 4 — Mobile Inbox and Alert Experience

**Goal:** Visible end-to-end notifications without depending on Firebase setup.

1. Implement feature-based Riverpod models/providers and update the Alerts tab with inbox/active-alert views, badge, filters and pagination.
2. Add notification/alert detail routes, machine drill-down, mark-read/read-all, authorized acknowledgement, and preferences.
3. Add private socket invalidation plus REST refresh on resume/reconnect; clear all scoped state on account switching.
4. Add loading/empty/offline/error UI and accessibility; avoid duplicate list items across pagination and live refresh.
5. Add model/provider/widget/navigation tests and validate real backend interactions with two users and two organizations.

**Acceptance:** A staged battery/offline/expiry event appears in the correct assignee's app; unread count and read state reconcile; detail navigation has a working back path; a different user cannot see the event. Works without push permission or Firebase.

**STOP:** Ask the user to verify Phase 4 and explicitly approve Phase 5.

### Notification Phase 5 — FCM Delivery and Mobile Push

**Goal:** Real push delivery integrated with the already-working inbox.

1. Obtain approved Firebase/APNs environment configuration without exposing credentials; review stable dependency compatibility before installing.
2. Add user installation/token schema, authenticated lifecycle APIs, protected token storage, multi-device delivery jobs and provider attempt tracking.
3. Implement the real FCM provider with worker leases, bounded retries, invalid-token removal, TTL, stale-event cancellation and honest delivery status.
4. Add Flutter Firebase initialization, permission onboarding, Android channels/iOS setup, token rotation/logout, foreground/background/tap behavior and deduplication.
5. Add provider-adapter tests with controlled responses; use real staging phones for end-to-end delivery checks. No production mock-success fallback.

**Acceptance:** Verify foreground/background/terminated behavior, permission denied, token rotation, shared-phone account switch, multiple phones, expired/reassigned event suppression, provider failure/recovery, and logout. FCM response IDs are recorded without being mislabeled as device delivery.

**STOP:** Ask the user to verify Phase 5 and explicitly approve Phase 6.

### Notification Phase 6 — Production Rollout and Operational Validation

**Goal:** Safely activate an already-tested system and verify operational behavior.

1. Validate all additive migrations against representative existing data, queue rollout ordering, provider credentials, expected load and rollback compatibility.
2. Enable staged event types for a limited tenant cohort; verify approved thresholds and reminder semantics with real machine observations.
3. Monitor queue lag, retries, recipient exclusions, duplicates, invalid tokens, rule noise and end-to-end latency. Tune batch sizes/worker concurrency from evidence.
4. Exercise database/broker/provider outages, crash recovery, concurrent schedulers, a notification storm, assignment churn and expired queue backlog.
5. Expand only after user acceptance. Keep per-event and push-delivery kill switches available; define ownership for DLQ review and safe replay.

**Acceptance:** No cross-tenant/user disclosure, no fake sends, controlled duplicate behavior, bounded backlog/retries, agreed latency targets met in staging and operational failure scenarios demonstrated. Reliability is required in earlier phases too; this phase validates rollout rather than adding it late.

**STOP:** Ask the user to verify the rollout. Do not start deferred features without separate approval.

## N10. Verification Matrix and Commands

| Area | Required cases |
|------|----------------|
| Rules | Healthy/violating/recovery, null/invalid units, equality boundaries, stale/out-of-order data, disabled/edited rules, sustained windows, escalation |
| Connectivity | Disconnect grace, heartbeat timeout, stale heartbeat after timeout, reconnect flap, never-seen device, gateway/backend outage |
| Expiry | 7/3/1/expired boundaries, timezone change, null validity, overlapping recharge history, renewal, late activation, multi-worker duplicate prevention |
| Security | Unauthenticated, missing permission, same-tenant wrong assignee, cross-tenant ID, revoked assignment, inactive account, spoofed socket destination |
| Durability | DB rollback, broker unavailable, publisher confirms/returns, crash before/after publication, consumer redelivery, poison event, replay after restart |
| Inbox | One item per recipient/event, stable pagination, combined filters, concurrent read-all/new arrival, no linked customer-user, no assignee |
| Push | No token, invalid token, provider throttling, transient/permanent failures, lease expiry, retry TTL, accepted-but-response-lost duplicate window |
| Mobile | Loading/error/empty, list/detail/back navigation, unread badge, resume/reconnect, logout switch, pending deep link, denied permission |
| Performance | Representative telemetry rate, worst-case affected-machine burst, bounded recipient lookup, indexed due-job scans, concurrent backend replicas |
| Migration | Fresh database and upgrade from current schema, legacy null/bad rows, constraints/indexes, old application compatibility during rollout |

Use JUnit 5 for Java unit/service tests, existing integration infrastructure where available, and PostgreSQL/RabbitMQ integration tests for transactions, constraints and delivery behavior. Confirm Testcontainers dependencies and local Docker availability before relying on them; in-memory substitutes do not verify PostgreSQL or broker semantics. Gateway parser regression tests are mandatory if touching any gateway integration boundary; parsing logic remains unchanged.

Planned commands from repository root: `.\gradlew.bat :shared:build :backend:build`; include `:device-gateway:build` when shared contracts/gateway integration change. Final Java verification: `.\gradlew.bat build`. From mobile/: `flutter analyze`, `flutter test`, and `flutter build apk --debug`; perform production release/signing checks before release and iOS checks on an appropriately configured macOS host. These commands are planned, not claimed to have run for this document.

Each phase must run its focused tests plus affected-module build checks and record actual results and blockers. Do not bypass failing security checks, hooks, or dependency policies. Review the AGENTS.md Major-change checklist for schema/contracts/security changes.

## N11. Operations, Rollout Safety and Success Criteria

- Metrics: alert transitions by type, duplicate inputs suppressed, recipients excluded by reason, oldest unpublished outbox age, queue depth/age, retry/DLQ counts, inbox creation latency, provider acceptance/failure, invalid tokens, and worker lease recovery.
- Avoid user/IMEI/token IDs as metric labels; use structured logs with eventId/alertId/notificationId and safe tenant/machine context for correlation.
- Initial staging targets: event application to inbox p95 under 5 seconds; inbox to provider acceptance p95 under 10 seconds in healthy conditions. Detection windows are additional; these are acceptance targets to measure, not delivery guarantees or implementation time estimates.
- Configure alerts for sustained backlog/DLQ growth and abnormal no-recipient rates. Keep provider outages distinct from machine offline incidents to avoid a platform incident notifying every farmer incorrectly.
- Rollout order: additive schema -> compatible contracts/topology -> canonical ingestion/outbox -> inbox consumer/APIs -> mobile inbox -> token registration -> FCM workers -> controlled enablement. Buffer committed events durably; do not activate customer fanout until compatible consumers are deployed.
- Feature switches separately control each event family and push dispatch. Disabling push preserves inbox and incident processing. Avoid building an unrelated feature-flag service.
- On rollback, stop new pushes/event-family activation, retain outbox and data, and deploy a known-compatible binary. Do not drop queues/exchanges, reverse applied migrations destructively, or purge pending work.
- Existing queue argument changes can also conflict with declarations: use coordinated versioned queues and an approved drain/cutover plan rather than deleting a queue to make startup succeed.
- Replays are privileged, audited, bounded, and idempotent. Recheck current recipient access, expiry-cycle validity, and event age so recovery does not flood users with stale pushes.
- Any cleanup, data correction, production sends, credentials/platform setup, or destructive action requires the appropriate explicit approval. This plan does not authorize those actions.

## N12. Future Improvements — Not Included Automatically

After the six phases are verified, consider quiet hours with explicit critical exceptions, localized templates and timezone preferences, technician escalation with approved recipients, notification digests, a privileged delivery-health dashboard, and capability-backed geofence/SOS/tamper rules. Evaluate paid SMS/WhatsApp only after consent, cost limits, and provider/compliance requirements are agreed. Do not expand assignee-only routing or add command notifications without user approval.

## N13. Implementation Tracker

| Notification phase | Status | User verification/next-phase approval |
|--------------------|--------|---------------------------------------|
| 1 — Reliable alert foundation | Not started | Required before Phase 2 |
| 2 — Rule/offline/expiry sources | Not started | Required before Phase 3 |
| 3 — Inbox/preferences/APIs | Not started | Required before Phase 4 |
| 4 — Mobile inbox | Not started | Required before Phase 5 |
| 5 — Real FCM push | Not started | Required before Phase 6 |
| 6 — Production rollout validation | Not started | Required before any expansion |

**Current action completed by this document:** planning only. No database migration, dependency installation, backend/mobile/gateway implementation, provider setup, deployment, or notification send is part of the current task.
