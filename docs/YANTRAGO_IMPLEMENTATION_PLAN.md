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
