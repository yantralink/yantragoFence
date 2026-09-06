# YantraGO Machine Management Platform — Project Structure

> **Document Purpose:** This document defines the complete project structure, folder layout, and module organization for the new **YantraGO Machine Management Platform**.
>
> It explicitly documents how the **existing GPS/TCP protocol implementation** from the `HarvestTracker` project is reused and integrated into the new platform.
>
> **Backend Technology:** Java 17 + Spring Boot 3.x (entire backend)
> **Status:** Foundation specification — ready for development kickoff
> **Target Scale:** ~20,000 users, multi-tenant, production-grade
> **Created:** September 2026
> **Updated:** September 2026 — Backend confirmed as Java/Spring Boot

---

## Table of Contents

1. [Monorepo Overview](#1-monorepo-overview)
2. [Top-Level Folder Structure](#2-top-level-folder-structure)
3. [Backend API — Spring Boot (`backend/`)](#3-backend-api--spring-boot-backend)
4. [TCP Device Gateway — Spring Boot (`device-gateway/`)](#4-tcp-device-gateway--spring-boot-device-gateway)
5. [Mobile App — Flutter (`mobile/`)](#5-mobile-app--flutter-mobile)
6. [Admin Web — Next.js (`admin-web/`)](#6-admin-web--nextjs-admin-web)
7. [Shared Java Library (`shared/`)](#7-shared-java-library-shared)
8. [Database (`database/`)](#8-database-database)
9. [Infrastructure (`infra/`)](#9-infrastructure-infra)
10. [Device Simulator (`simulator/`)](#10-device-simulator-simulator)
11. [Reused GPS/TCP Protocol Components](#11-reused-gpstcp-protocol-components)
12. [Module Dependency Map](#12-module-dependency-map)
13. [Environment Strategy](#13-environment-strategy)
14. [CI/CD Pipeline Structure](#14-cicd-pipeline-structure)
15. [Why Java for the Entire Backend](#15-why-java-for-the-entire-backend)
16. [Vultr Infrastructure Setup](#16-vultr-infrastructure-setup)
17. [Vultr Server Management](#17-vultr-server-management)
18. [Vultr Cost Breakdown & Scaling](#18-vultr-cost-breakdown--scaling)

---

## 1. Monorepo Overview

The entire platform is managed as a **single Git monorepo** so that the backend, device gateway, mobile app, admin web, and infrastructure can evolve together with consistent contracts.

The backend (API server + TCP device gateway) is built entirely in **Java 17 + Spring Boot 3.x**. This allows direct reuse of the proven GPS/TCP protocol code from the HarvestTracker project without any porting or rewriting.

```
yantrago-platform/
├── backend/              # Spring Boot REST API + WebSocket server (Java 17)
├── device-gateway/       # Spring Boot TCP device gateway (Java 17)
├── mobile/               # Flutter mobile app (Android + iOS)
├── admin-web/            # Next.js admin web portal (TypeScript)
├── shared/               # Shared Java library (DTOs, queue contracts)
├── database/             # Flyway migrations, seeds, schema docs
├── infra/                # Docker, Kubernetes, Terraform, Nginx
├── simulator/            # Java device simulator for load testing
├── docs/                 # Project documentation
├── scripts/              # Utility scripts (setup, deploy, seed)
├── .github/              # CI/CD workflows
├── build.gradle.kts      # Root Gradle build (multi-project)
├── settings.gradle.kts   # Gradle project includes
├── gradle/               # Gradle wrapper
├── gradlew / gradlew.bat # Gradle wrapper scripts
├── .gitignore
├── .editorconfig
├── README.md
└── LICENSE
```

**Build System:** Gradle (Kotlin DSL) — same as HarvestTracker
**Java Version:** 17 (LTS)
**Spring Boot Version:** 3.2.x or later

---

## 2. Top-Level Folder Structure

### Root `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "yantrago-platform"
include(":backend")
include(":device-gateway")
include(":shared")
include(":simulator")
```

### Root `build.gradle.kts`

```kotlin
plugins {
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
```

---

## 3. Backend API — Spring Boot (`backend/`)

The backend API server handles all business logic: authentication, user management, machine management, command lifecycle, telemetry storage, alerts, notifications, reporting, and WebSocket real-time push.

```
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/yantrago/api/
│   │   │       │
│   │   │       ├── YantraGoApiApplication.java   # Main entry point
│   │   │       │
│   │   │       ├── config/                           # Configuration classes
│   │   │       │   ├── SecurityConfig.java           # Spring Security, JWT filter chain
│   │   │       │   ├── WebSocketConfig.java          # STOMP WebSocket configuration
│   │   │       │   ├── RedisConfig.java              # Redis cache + session config
│   │   │       │   ├── RabbitMqConfig.java           # RabbitMQ queues, exchanges, bindings
│   │   │       │   ├── HikariPoolConfig.java         # Database connection pool tuning
│   │   │       │   ├── OpenApiConfig.java            # Swagger/OpenAPI documentation
│   │   │       │   ├── WebMvcConfig.java             # CORS, interceptors, converters
│   │   │       │   └── MetricsConfig.java            # Micrometer custom metrics
│   │   │       │
│   │   │       ├── controller/                       # REST controllers
│   │   │       │   ├── AuthController.java           # /api/v1/auth — login, refresh, logout
│   │   │       │   ├── UserController.java           # /api/v1/users
│   │   │       │   ├── OrganizationController.java   # /api/v1/organizations
│   │   │       │   ├── CustomerController.java       # /api/v1/customers
│   │   │       │   ├── MachineController.java        # /api/v1/machines
│   │   │       │   ├── DeviceController.java         # /api/v1/devices
│   │   │       │   ├── CommandController.java        # /api/v1/commands — ON/OFF
│   │   │       │   ├── TelemetryController.java      # /api/v1/telemetry — voltage, battery
│   │   │       │   ├── LocationController.java       # /api/v1/locations — GPS
│   │   │       │   ├── AlertController.java          # /api/v1/alerts
│   │   │       │   ├── NotificationController.java   # /api/v1/notifications
│   │   │       │   ├── SettingsController.java       # /api/v1/settings
│   │   │       │   ├── ReportController.java         # /api/v1/reports
│   │   │       │   ├── RechargeController.java       # /api/v1/recharges
│   │   │       │   ├── AuditLogController.java       # /api/v1/audit-logs
│   │   │       │   └── HealthController.java         # /api/v1/health
│   │   │       │
│   │   │       ├── service/                          # Business logic services
│   │   │       │   ├── AuthService.java              # JWT generation, refresh rotation
│   │   │       │   ├── JwtService.java               # JWT sign/verify (HS256)
│   │   │       │   ├── UserService.java
│   │   │       │   ├── OrganizationService.java
│   │   │       │   ├── CustomerService.java
│   │   │       │   ├── MachineService.java
│   │   │       │   ├── DeviceService.java
│   │   │       │   ├── CommandService.java           # Command lifecycle management
│   │   │       │   ├── CommandStateMachine.java      # PENDING→QUEUED→SENT→ACK→DONE
│   │   │       │   ├── TelemetryService.java         # Store voltage, battery, GSM readings
│   │   │       │   ├── LocationService.java          # Store GPS, query history
│   │   │       │   ├── LocationPersistenceService.java  # Batch insert (reused from HarvestTracker)
│   │   │       │   ├── AlertService.java             # Alert rule evaluation
│   │   │       │   ├── AlertGenerationService.java   # Generate alerts from telemetry
│   │   │       │   ├── NotificationService.java      # Dispatch push/email/SMS
│   │   │       │   ├── PushNotificationService.java  # Push notifications (FCM/Expo/onesignal-agnostic)
│   │   │       │   ├── EmailService.java             # Email notifications (SMTP)
│   │   │       │   ├── SmsService.java               # SMS notifications (provider-agnostic)
│   │   │       │   ├── SettingsService.java          # Machine & system settings
│   │   │       │   ├── ReportService.java            # Aggregated reporting
│   │   │       │   ├── ReportExportService.java      # PDF/CSV export (PDFBox)
│   │   │       │   ├── RechargeService.java          # SIM recharge tracking
│   │   │       │   ├── AuditLogService.java          # Tamper-resistant audit trail
│   │   │       │   ├── DeviceHeartbeatService.java   # Track device last-seen (reused)
│   │   │       │   └── OwnerContextService.java      # Tenant context resolution
│   │   │       │
│   │   │       ├── repository/                       # Data access (JdbcTemplate / JPA)
│   │   │       │   ├── UserRepository.java
│   │   │       │   ├── OrganizationRepository.java
│   │   │       │   ├── CustomerRepository.java
│   │   │       │   ├── MachineRepository.java
│   │   │       │   ├── DeviceRepository.java
│   │   │       │   ├── CommandRepository.java
│   │   │       │   ├── TelemetryRepository.java
│   │   │       │   ├── LocationRepository.java
│   │   │       │   ├── AlertRepository.java
│   │   │       │   ├── NotificationRepository.java
│   │   │       │   ├── SettingsRepository.java
│   │   │       │   ├── RechargeRepository.java
│   │   │       │   ├── AuditLogRepository.java
│   │   │       │   └── RefreshTokenRepository.java
│   │   │       │
│   │   │       ├── model/                            # JPA entities / domain models
│   │   │       │   ├── User.java
│   │   │       │   ├── Organization.java
│   │   │       │   ├── Role.java
│   │   │       │   ├── Permission.java
│   │   │       │   ├── Customer.java
│   │   │       │   ├── Machine.java
│   │   │       │   ├── Device.java
│   │   │       │   ├── MachineAssignment.java
│   │   │       │   ├── DeviceState.java
│   │   │       │   ├── DeviceLocation.java
│   │   │       │   ├── LocationHistory.java
│   │   │       │   ├── VoltageReading.java
│   │   │       │   ├── BatteryReading.java
│   │   │       │   ├── GsmReading.java
│   │   │       │   ├── MachineCommand.java
│   │   │       │   ├── CommandAttempt.java
│   │   │       │   ├── MachineSetting.java
│   │   │       │   ├── AlertRule.java
│   │   │       │   ├── Alert.java
│   │   │       │   ├── Notification.java
│   │   │       │   ├── NotificationPreference.java
│   │   │       │   ├── Recharge.java
│   │   │       │   ├── AuditLog.java
│   │   │       │   ├── RefreshToken.java
│   │   │       │   ├── LoginSession.java
│   │   │       │   └── SystemSetting.java
│   │   │       │
│   │   │       ├── dto/                              # Request/Response DTOs
│   │   │       │   ├── auth/
│   │   │       │   │   ├── LoginRequest.java
│   │   │       │   │   ├── LoginResponse.java
│   │   │       │   │   ├── RefreshTokenRequest.java
│   │   │       │   │   └── TokenResponse.java
│   │   │       │   ├── machine/
│   │   │       │   │   ├── MachineDto.java
│   │   │       │   │   ├── CreateMachineRequest.java
│   │   │       │   │   └── MachineStatusDto.java
│   │   │       │   ├── command/
│   │   │       │   │   ├── CommandRequest.java
│   │   │       │   │   ├── CommandResponse.java
│   │   │       │   │   └── CommandStatusDto.java
│   │   │       │   ├── telemetry/
│   │   │       │   │   ├── TelemetryDto.java
│   │   │       │   │   ├── VoltageDto.java
│   │   │       │   │   └── BatteryDto.java
│   │   │       │   ├── location/
│   │   │       │   │   └── LocationDto.java
│   │   │       │   ├── alert/
│   │   │       │   │   └── AlertDto.java
│   │   │       │   ├── customer/
│   │   │       │   │   └── CustomerDto.java
│   │   │       │   └── report/
│   │   │       │       └── ReportFilterDto.java
│   │   │       │
│   │   │       ├── security/                         # Security layer
│   │   │       │   ├── JwtAuthFilter.java            # JWT authentication filter
│   │   │       │   ├── TenantContextFilter.java      # Multi-tenant context filter
│   │   │       │   ├── TenantGuard.java              # Cross-tenant access prevention
│   │   │       │   ├── PermissionEvaluator.java      # RBAC permission check
│   │   │       │   ├── AuditLogInterceptor.java      # Auto-log sensitive actions
│   │   │       │   └── RateLimitFilter.java          # Per-IP / per-user rate limiting
│   │   │       │
│   │   │       ├── websocket/                        # Real-time push
│   │   │       │   ├── TrackingWebSocketController.java  # STOMP topics (reused pattern)
│   │   │       │   ├── LocationBroadcastService.java     # Broadcast GPS updates
│   │   │       │   ├── CommandBroadcastService.java      # Broadcast command status
│   │   │       │   └── WebSocketAuthInterceptor.java     # JWT auth for WS connections
│   │   │       │
│   │   │       └── queue/                            # RabbitMQ integration
│   │   │           ├── CommandProducer.java          # Publish commands to gateway
│   │   │           ├── DeviceEventConsumer.java      # Consume events from gateway
│   │   │           ├── TelemetryConsumer.java        # Consume telemetry from gateway
│   │   │           ├── AlertConsumer.java            # Consume alert events
│   │   │           └── NotificationConsumer.java     # Async notification dispatch
│   │   │
│   │   └── resources/
│   │       ├── application.yml                       # Default config
│   │       ├── application-dev.yml                   # Development
│   │       ├── application-staging.yml               # Staging
│   │       ├── application-prod.yml                  # Production
│   │       ├── db/migration/                         # Flyway migrations
│   │       │   └── (symlinked from database/migrations/)
│   │       └── logback-spring.xml                    # Logging configuration
│   │
│   └── test/
│       └── java/
│           └── com/yantrago/api/
│               ├── controller/
│               │   ├── AuthControllerTest.java
│               │   ├── MachineControllerTest.java
│               │   ├── CommandControllerTest.java
│               │   └── TenantIsolationTest.java
│               ├── service/
│               │   ├── CommandServiceTest.java
│               │   ├── AlertServiceTest.java
│               │   └── TelemetryServiceTest.java
│               └── YantraGoApiApplicationTests.java
│
├── build.gradle.kts
└── README.md
```

### Backend `build.gradle.kts`

```kotlin
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("java")
}

group = "com.yantrago"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")       // RabbitMQ
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")

    // Rate limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // PDF export
    implementation("org.apache.pdfbox:pdfbox:3.0.1")

    // Monitoring
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.5")

    // API docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Shared library
    implementation(project(":shared"))

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### Module Responsibilities

| Module | Controller | Service | Responsibility |
|--------|-----------|---------|----------------|
| Auth | AuthController | AuthService, JwtService | Login, JWT issuance, refresh rotation, logout |
| Users | UserController | UserService | User CRUD, profile |
| Organizations | OrganizationController | OrganizationService | Tenant lifecycle, white-label config |
| Customers | CustomerController | CustomerService | Customer CRUD within tenant |
| Machines | MachineController | MachineService | Machine registration, assignment |
| Devices | DeviceController | DeviceService | Device identity, IMEI/SIM mapping |
| Commands | CommandController | CommandService, CommandStateMachine | ON/OFF lifecycle (PENDING→ACK→DONE) |
| Telemetry | TelemetryController | TelemetryService | Voltage, battery, GSM storage |
| Locations | LocationController | LocationService, LocationPersistenceService | GPS storage, history, batch insert |
| Alerts | AlertController | AlertService, AlertGenerationService | Alert rules, generation, history |
| Notifications | NotificationController | NotificationService, PushNotificationService, EmailService, SmsService | Push, email, SMS dispatch |
| Settings | SettingsController | SettingsService | Machine thresholds, intervals |
| Reports | ReportController | ReportService, ReportExportService | Aggregated reports, PDF/CSV export |
| Recharge | RechargeController | RechargeService | SIM recharge status, expiry |
| Audit | AuditLogController | AuditLogService | Tamper-proof audit trail |

---

## 4. TCP Device Gateway — Spring Boot (`device-gateway/`)

This is the **critical component that reuses the existing GPS/TCP protocol** from the HarvestTracker project. The protocol parsing logic (ConcoxV5, JT808) is **copied directly** as Java source files — no porting, no rewriting.

```
device-gateway/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/yantrago/gateway/
│   │   │       │
│   │   │       ├── YantraGoGatewayApplication.java    # Main entry point
│   │   │       │
│   │   │       ├── tcp/                              # REUSED FROM HARVESTTRACKER
│   │   │       │   ├── ProtocolHandler.java          # ← COPIED AS-IS
│   │   │       │   ├── ProtocolRouter.java           # ← COPIED AS-IS
│   │   │       │   │
│   │   │       │   ├── concox/                       # Concox V5 / BR05 protocol
│   │   │       │   │   ├── ConcoxV5ProtocolHandler.java   # ← COPIED AS-IS (446 lines)
│   │   │       │   │   ├── ConcoxV5TcpServer.java         # ← COPIED AS-IS (197 lines)
│   │   │       │   │   └── ConcoxV5Constants.java
│   │   │       │   │
│   │   │       │   ├── jt808/                        # JT808-2013 protocol (T98)
│   │   │       │   │   ├── JT808ProtocolHandler.java     # ← COPIED AS-IS (300 lines)
│   │   │       │   │   ├── JT808FrameParser.java         # ← COPIED AS-IS (150 lines)
│   │   │       │   │   ├── JT808MessageEncoder.java      # ← COPIED AS-IS (163 lines)
│   │   │       │   │   ├── JT808TcpServer.java           # ← COPIED AS-IS (139 lines)
│   │   │       │   │   └── JT808Constants.java
│   │   │       │   │
│   │   │       │   ├── fencing/                      # NEW: YantraGO fencing protocol
│   │   │       │   │   ├── FencingProtocolHandler.java  # Fencing-specific handler
│   │   │       │   │   ├── FencingParser.java          # Parse voltage, battery, state
│   │   │       │   │   ├── FencingEncoder.java         # Build ON/OFF command packets
│   │   │       │   │   └── FencingConstants.java
│   │   │       │   │
│   │   │       │   ├── DeviceConnectionRegistry.java # ← COPIED AS-IS (59 lines)
│   │   │       │   ├── DashcamConnectionRegistry.java
│   │   │       │   ├── JT1076FrameAssembler.java
│   │   │       │   ├── JT1076RtpPacket.java
│   │   │       │   └── JT1076RtpReceiver.java
│   │   │       │
│   │   │       ├── service/                          # Gateway business logic
│   │   │       │   ├── DeviceAuthService.java        # Authenticate devices on connect
│   │   │       │   ├── DeviceHeartbeatService.java   # Track heartbeats (reused pattern)
│   │   │       │   ├── DeviceMappingCacheService.java # IMEI → device mapping (reused)
│   │   │       │   ├── TelemetryForwardService.java  # Forward telemetry to RabbitMQ
│   │   │       │   ├── CommandDispatchService.java   # Send commands to devices via TCP
│   │   │       │   ├── CommandResultService.java     # Process ACK/reply from devices
│   │   │       │   └── DeviceStateService.java       # Maintain last-known state in Redis
│   │   │       │
│   │   │       ├── queue/                            # RabbitMQ integration
│   │   │       │   ├── CommandConsumer.java          # Consume commands from backend
│   │   │       │   ├── DeviceEventProducer.java      # Publish device events to backend
│   │   │       │   ├── TelemetryProducer.java        # Publish telemetry to backend
│   │   │       │   └── QueueConfig.java              # Exchange/queue declarations
│   │   │       │
│   │   │       └── config/
│   │   │           ├── GatewayConfig.java            # TCP port config, timeouts
│   │   │           ├── RabbitMqConfig.java
│   │   │           └── RedisConfig.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── logback-spring.xml
│   │
│   └── test/
│       └── java/
│           └── com/yantrago/gateway/
│               ├── tcp/
│               │   ├── ConcoxV5ProtocolHandlerTest.java   # ← COPIED AS-IS
│               │   ├── JT808FrameParserTest.java
│               │   └── FencingProtocolHandlerTest.java
│               └── service/
│                   ├── CommandDispatchServiceTest.java
│                   └── TelemetryForwardServiceTest.java
│
├── build.gradle.kts
└── README.md
```

### Device Gateway `build.gradle.kts`

```kotlin
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("java")
}

group = "com.yantrago"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-amqp")       // RabbitMQ
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // Database (for device mapping lookups)
    runtimeOnly("org.postgresql:postgresql")

    // Monitoring
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.5")

    // Shared library
    implementation(project(":shared"))

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### Why a Separate Spring Boot Service?

The TCP gateway is separated from the API server because:

1. **TCP connections are long-lived** — holding them in the API server ties up HTTP threads
2. **Independent scaling** — TCP gateway scales based on device count; API scales based on user count
3. **Fault isolation** — a TCP gateway crash doesn't take down the API
4. **Protocol parsing is CPU-intensive** — isolating it prevents API latency impact
5. **Different deployment profiles** — gateway needs more memory for connections; API needs more CPU for JSON

Both services are Spring Boot, both are Java 17, both use Gradle, and they communicate via RabbitMQ.

### Gateway Architecture

```
                    RabbitMQ
                       │
          ┌────────────┼────────────┐
          │            │            │
          ▼            │            ▼
    command.queue      │      device.events
          │            │            │
          ▼            │            ▼
    ┌──────────────────────────────────────┐
    │     TCP Device Gateway (Java)         │
    │                                       │
    │  ┌─────────┐  ┌─────────┐            │
    │  │ Concox  │  │ JT808   │            │
    │  │ V5      │  │ T98     │            │
    │  │ Handler │  │ Handler │            │
    │  └────┬────┘  └────┬────┘            │
    │       │            │                  │
    │       ▼            ▼                  │
    │  ┌──────────────────────┐            │
    │  │  YantraGO Fencing    │            │
    │  │  Protocol Handler    │            │
    │  │  (NEW)               │            │
    │  └──────────┬───────────┘            │
    │             │                         │
    │             ▼                         │
    │  ┌──────────────────────┐            │
    │  │  TelemetryForward    │            │
    │  │  → Publish to queue  │            │
    │  └──────────────────────┘            │
    └──────────────────────────────────────┘
                    │
               GSM / TCP
                    │
                    ▼
         YantraGO Machine
```

---

## 5. Mobile App — Flutter (`mobile/`)

The mobile app is built with **Flutter** for Android + iOS from a single codebase.

```
mobile/
├── lib/
│   ├── main.dart                    # App entry point
│   ├── app.dart                     # MaterialApp / router setup
│   │
│   ├── core/                        # Core app infrastructure
│   │   ├── config/
│   │   │   ├── app_config.dart      # API URLs, environment config
│   │   │   └── theme.dart           # App theme & colors
│   │   ├── network/
│   │   │   ├── api_client.dart      # HTTP client (Dio)
│   │   │   ├── websocket_client.dart # WebSocket for real-time
│   │   │   └── interceptors/
│   │   │       ├── auth_interceptor.dart
│   │   │       └── error_interceptor.dart
│   │   ├── auth/
│   │   │   ├── auth_service.dart    # Login, refresh, logout
│   │   │   ├── token_manager.dart   # Secure token storage
│   │   │   └── auth_state.dart      # Auth state management
│   │   ├── storage/
│   │   │   └── secure_storage.dart  # Flutter Secure Storage
│   │   └── utils/
│   │       ├── date_utils.dart
│   │       └── validators.dart
│   │
│   ├── features/                    # Feature-based organization
│   │   ├── auth/
│   │   │   ├── pages/
│   │   │   │   ├── login_page.dart
│   │   │   │   └── splash_page.dart
│   │   │   ├── widgets/
│   │   │   └── providers/
│   │   │       └── auth_provider.dart
│   │   │
│   │   ├── dashboard/               # Main dashboard
│   │   │   ├── pages/
│   │   │   │   └── dashboard_page.dart
│   │   │   ├── widgets/
│   │   │   │   ├── machine_status_card.dart
│   │   │   │   ├── battery_widget.dart
│   │   │   │   ├── voltage_widget.dart
│   │   │   │   ├── gsm_status_widget.dart
│   │   │   │   └── recharge_status_widget.dart
│   │   │   └── providers/
│   │   │       └── dashboard_provider.dart
│   │   │
│   │   ├── machines/                # Machine list & details
│   │   │   ├── pages/
│   │   │   │   ├── machine_list_page.dart
│   │   │   │   └── machine_detail_page.dart
│   │   │   ├── widgets/
│   │   │   │   ├── machine_card.dart
│   │   │   │   └── on_off_button.dart
│   │   │   └── providers/
│   │   │       └── machine_provider.dart
│   │   │
│   │   ├── map/                     # GPS map view
│   │   │   ├── pages/
│   │   │   │   └── machine_map_page.dart
│   │   │   ├── widgets/
│   │   │   │   └── machine_marker.dart
│   │   │   └── providers/
│   │   │       └── location_provider.dart
│   │   │
│   │   ├── commands/                # ON/OFF command UI
│   │   │   ├── widgets/
│   │   │   │   └── command_status_widget.dart
│   │   │   └── providers/
│   │   │       └── command_provider.dart
│   │   │
│   │   ├── alerts/                  # Alerts & notifications
│   │   │   ├── pages/
│   │   │   │   └── alerts_page.dart
│   │   │   └── providers/
│   │   │       └── alerts_provider.dart
│   │   │
│   │   ├── settings/                # Machine & app settings
│   │   │   ├── pages/
│   │   │   │   └── settings_page.dart
│   │   │   └── providers/
│   │   │       └── settings_provider.dart
│   │   │
│   │   ├── history/                 # Activity history
│   │   │   ├── pages/
│   │   │   │   └── activity_history_page.dart
│   │   │   └── providers/
│   │   │       └── history_provider.dart
│   │   │
│   │   └── profile/                 # User profile
│   │       ├── pages/
│   │       │   └── profile_page.dart
│   │       └── providers/
│   │           └── profile_provider.dart
│   │
│   ├── models/                      # Data models
│   │   ├── user.dart
│   │   ├── machine.dart
│   │   ├── device_state.dart
│   │   ├── location.dart
│   │   ├── telemetry.dart
│   │   ├── command.dart
│   │   ├── alert.dart
│   │   └── notification.dart
│   │
│   └── routing/
│       └── app_router.dart          # GoRouter configuration
│
├── assets/
│   ├── icons/
│   ├── images/
│   └── fonts/
│
├── test/
│   ├── unit/
│   ├── widget/
│   └── integration/
│
├── android/                         # Android-specific config
├── ios/                             # iOS-specific config
├── pubspec.yaml                     # Flutter dependencies
├── analysis_options.yaml
└── README.md
```

### State Management

- **Riverpod** for dependency injection and state management
- **GoRouter** for declarative routing with auth guards
- **Dio** for HTTP with interceptors for auth token injection
- **WebSocket** (STOMP) for real-time machine state updates

---

## 6. Admin Web — Next.js (`admin-web/`)

The admin web portal is built with **Next.js + TypeScript**. This is the only non-Java component, which is appropriate since it's a frontend-only application.

```
admin-web/
├── src/
│   ├── app/                         # Next.js App Router
│   │   ├── layout.tsx               # Root layout
│   │   ├── page.tsx                 # Landing / redirect
│   │   │
│   │   ├── (auth)/                  # Auth route group
│   │   │   ├── login/
│   │   │   │   └── page.tsx
│   │   │   └── layout.tsx
│   │   │
│   │   ├── (super-admin)/           # Super Admin route group
│   │   │   ├── dashboard/
│   │   │   ├── organizations/
│   │   │   ├── admins/
│   │   │   ├── machines/
│   │   │   ├── customers/
│   │   │   ├── reports/
│   │   │   ├── audit-logs/
│   │   │   ├── settings/
│   │   │   └── layout.tsx
│   │   │
│   │   └── (admin)/                 # Wholesaler/Admin route group
│   │       ├── dashboard/
│   │       ├── customers/
│   │       ├── machines/
│   │       ├── reports/
│   │       ├── alerts/
│   │       └── layout.tsx
│   │
│   ├── components/                  # Shared UI components
│   │   ├── ui/                      # Base UI (buttons, cards, tables)
│   │   ├── charts/                  # Dashboard charts
│   │   ├── maps/                    # Map components
│   │   ├── tables/                  # Data tables
│   │   └── forms/                   # Form components
│   │
│   ├── lib/                         # Utilities & hooks
│   │   ├── api-client.ts            # HTTP client → Java backend
│   │   ├── auth.ts
│   │   ├── websocket.ts             # STOMP client → Java backend
│   │   └── utils.ts
│   │
│   ├── hooks/                       # React hooks
│   │   ├── use-auth.ts
│   │   ├── use-machines.ts
│   │   └── use-websocket.ts
│   │
│   ├── stores/                      # State management (Zustand)
│   │   ├── auth-store.ts
│   │   └── app-store.ts
│   │
│   └── types/                       # TypeScript types
│       ├── api.ts
│       ├── models.ts
│       └── index.ts
│
├── public/
├── package.json
├── tsconfig.json
├── next.config.js
├── tailwind.config.ts
└── .eslintrc.js
```

### Admin Web Tech Stack

- **Next.js 14+** with App Router
- **TypeScript** for type safety
- **Tailwind CSS** for styling
- **Zustand** for client state
- **React Query** for server state & caching
- **Recharts** for dashboard charts
- **Leaflet/Mapbox** for map visualization
- **STOMP.js** for WebSocket communication with Java backend

---

## 7. Shared Java Library (`shared/`)

A shared Java module consumed by both `backend/` and `device-gateway/` via Gradle's `project(":shared")` dependency. This ensures both services agree on RabbitMQ message contracts.

```
shared/
├── src/
│   └── main/
│       └── java/
│           └── com/yantrago/shared/
│               ├── queue/
│               │   ├── QueueNames.java              # RabbitMQ queue/exchange name constants
│               │   ├── CommandMessage.java          # Command → gateway message contract
│               │   ├── CommandResultMessage.java    # Gateway → backend command result
│               │   ├── TelemetryMessage.java        # Gateway → backend telemetry data
│               │   ├── DeviceEventMessage.java      # Gateway → backend device events
│               │   ├── AlertEventMessage.java       # Alert event contract
│               │   └── LocationMessage.java         # GPS location message contract
│               │
│               ├── dto/
│               │   ├── DeviceStateDto.java          # Last-known device state
│               │   ├── GpsIngestRequest.java        # GPS ingest (reused from HarvestTracker)
│               │   └── DeviceRecord.java            # Device mapping record (reused)
│               │
│               └── util/
│                   └── HashUtil.java                # Hashing utilities (reused)
│
├── build.gradle.kts
└── README.md
```

### Shared `build.gradle.kts`

```kotlin
plugins {
    id("java-library")
}

group = "com.yantrago"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
```

This library defines the **RabbitMQ message contracts** as Java classes so both the backend and gateway compile against the same types. No serialization mismatch is possible.

---

## 8. Database (`database/`)

```
database/
├── migrations/                     # Flyway versioned SQL migrations
│   ├── V1__create_organizations.sql
│   ├── V2__create_users_and_roles.sql
│   ├── V3__create_customers.sql
│   ├── V4__create_machines_and_devices.sql
│   ├── V5__create_device_states.sql
│   ├── V6__create_location_history_partitioned.sql
│   ├── V7__create_telemetry_tables.sql
│   ├── V8__create_commands.sql
│   ├── V9__create_alerts.sql
│   ├── V10__create_notifications.sql
│   ├── V11__create_audit_logs.sql
│   ├── V12__create_recharges.sql
│   ├── V13__create_settings.sql
│   ├── V14__create_refresh_tokens.sql
│   └── V15__add_performance_indexes.sql
│
├── seeds/                          # Seed data for dev/staging
│   ├── V1__seed_super_admin.sql
│   ├── V2__seed_sample_organization.sql
│   ├── V3__seed_sample_customers.sql
│   └── V4__seed_sample_machines.sql
│
├── partitions/                     # Partition management
│   ├── create_monthly_partitions.sql
│   └── archive_old_partitions.sql
│
└── schema/
    └── ERD.md                      # Entity relationship documentation
```

### Core Tables

```
organizations              # Tenants (companies using the platform)
users                      # All users (super_admin, admin, customer)
roles                      # Role definitions
permissions                # Permission definitions
role_permissions           # Many-to-many
user_roles                 # Many-to-many

customers                  # End users within a tenant
machines                   # YantraGO machines
machine_assignments        # Machine → customer mapping
devices                    # Device identity (IMEI, SIM, credentials)

device_states              # Last-known state per device (upsert)
device_locations           # Current GPS location (upsert)
location_history           # Historical GPS (partitioned by month)

voltage_readings           # Time-series voltage data (partitioned)
battery_readings           # Time-series battery data (partitioned)
gsm_readings               # Time-series GSM signal data (partitioned)

machine_commands           # Command lifecycle records
command_attempts           # Per-attempt tracking (retry, timeout)

machine_settings           # Per-machine configurable thresholds
alert_rules                # Alert configuration per machine/tenant
alerts                     # Generated alert instances

notifications              # Notification records
notification_preferences   # Per-user notification preferences

recharges                  # SIM recharge records
audit_logs                 # Tamper-resistant audit trail (partitioned)
refresh_tokens             # JWT refresh tokens (rotatable)
login_sessions             # Active session tracking
system_settings            # Platform-level configuration
```

### Partitioning Strategy

- `location_history` — partitioned by month (same as HarvestTracker's `vehicle_location_history`)
- `voltage_readings`, `battery_readings`, `gsm_readings` — partitioned by month
- `audit_logs` — partitioned by quarter
- Retention: hot data (3 months) on SSD, older data archived

---

## 9. Infrastructure (`infra/`)

The production infrastructure runs on **Vultr Cloud Compute** instances. The folder structure below reflects the Vultr-based deployment.

```
infra/
├── vultr/                           # Vultr-specific deployment configs
│   ├── server-setup/
│   │   ├── db-server-setup.sh       # PostgreSQL + PostGIS install script
│   │   ├── app-server-setup.sh      # Java 17 + Nginx install script
│   │   ├── gateway-server-setup.sh  # TCP gateway install script
│   │   ├── redis-server-setup.sh    # Redis install script
│   │   └── rabbitmq-server-setup.sh # RabbitMQ install script
│   ├── systemd/
│   │   ├── yantrago-backend.service     # Backend API systemd unit
│   │   ├── yantrago-gateway.service     # TCP gateway systemd unit
│   │   └── yantrago-rabbitmq.service    # RabbitMQ systemd unit (if self-hosted)
│   ├── nginx/
│   │   ├── nginx.conf               # Reverse proxy for backend API
│   │   ├── nginx.websocket.conf     # WebSocket upgrade config
│   │   └── nginx.ssl.conf           # TLS/SSL config (Let's Encrypt)
│   ├── firewall/
│   │   ├── db-server.ufw            # UFW rules for database server
│   │   ├── app-server.ufw           # UFW rules for app server
│   │   ├── gateway-server.ufw       # UFW rules for TCP gateway
│   │   └── redis-server.ufw         # UFW rules for Redis server
│   ├── deploy/
│   │   ├── deploy-backend.sh        # SCP + restart backend
│   │   ├── deploy-gateway.sh        # SCP + restart gateway
│   │   ├── deploy-admin-web.sh      # Build + deploy Next.js
│   │   └── rollback.sh              # Rollback to previous JAR
│   └── README.md                    # Vultr deployment guide
│
├── docker/
│   ├── docker-compose.dev.yml       # Local development stack
│   ├── docker-compose.staging.yml   # Staging environment
│   ├── Dockerfile.backend           # Java backend API image
│   ├── Dockerfile.gateway           # Java TCP gateway image
│   └── Dockerfile.admin-web         # Next.js admin web image
│
├── monitoring/
│   ├── prometheus.yml
│   ├── grafana/
│   │   ├── dashboards/
│   │   │   ├── api-overview.json
│   │   │   ├── gateway-metrics.json
│   │   │   ├── device-health.json
│   │   │   └── command-latency.json
│   │   └── datasources/
│   │       └── prometheus.yml
│   └── alerting/
│       └── alert-rules.yml
│
└── scripts/
    ├── deploy.sh
    ├── rollback.sh
    └── seed-db.sh
```

### Docker Compose (Development — Local Only)

For local development, Docker Compose spins up all dependencies. In production, these run as separate Vultr instances.

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| postgres | postgres:15-postgis | 5432 | Primary database |
| redis | redis:7-alpine | 6379 | Cache & session store |
| rabbitmq | rabbitmq:3-management | 5672, 15672 | Message broker |
| backend | local Java build | 8080 | Spring Boot API |
| gateway | local Java build | 5000, 5001, 5002 | TCP device gateway |
| admin-web | local Node build | 3001 | Next.js admin portal |
| prometheus | prom/prometheus | 9090 | Metrics |
| grafana | grafana/grafana | 3002 | Dashboards |

### Production Deployment on Vultr

In production, each service runs on its own Vultr Cloud Compute instance (or shared for cost savings). See [Section 16 — Vultr Infrastructure Setup](#16-vultr-infrastructure-setup) for the complete step-by-step guide.

### Dockerfile.backend (Example)

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

### Dockerfile.gateway (Example)

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew :device-gateway:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/device-gateway/build/libs/*.jar gateway.jar
EXPOSE 5000 5001 5002
ENTRYPOINT ["java", "-jar", "gateway.jar", "--spring.profiles.active=prod"]
```

---

## 10. Device Simulator (`simulator/`)

A **Java** device simulator for testing the TCP gateway at scale without real hardware. Uses the same protocol encoding as the gateway.

```
simulator/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/yantrago/simulator/
│   │   │       ├── SimulatorApplication.java
│   │   │       ├── ConcoxV5Simulator.java       # Simulates Concox V5 devices
│   │   │       ├── JT808Simulator.java          # Simulates JT808/T98 devices
│   │   │       ├── FencingSimulator.java        # Simulates YantraGO machines
│   │   │       ├── CommandResponder.java        # Simulates device ACK for commands
│   │   │       └── SimConfig.java               # Device count, intervals, etc.
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/yantrago/simulator/
│               └── SimulatorTest.java
│
├── build.gradle.kts
└── README.md
```

### Simulator Scenarios

| Scenario | Devices | Purpose |
|----------|---------|---------|
| `single-device` | 1 | Manual testing |
| `hundred-devices` | 100 | Integration testing |
| `thousand-devices` | 1,000 | Load testing |
| `ten-thousand-devices` | 10,000 | Stress testing |

The simulator uses the **same CRC-16, BCD, and packet-building logic** as the gateway (from the `shared` module) to generate valid packets.

---

## 11. Reused GPS/TCP Protocol Components

### What Is Reused from HarvestTracker

The HarvestTracker project contains a mature, tested TCP protocol implementation at:
`D:\CascadeProjects\Tracker\device-location-api\src\main\java\com\harvesttracker\devicelocationapi\tcp\`

The following Java files are **copied directly** into the new `device-gateway/` module — no porting, no rewriting:

### 11.1 Direct Copy-Paste Files

| HarvestTracker Source File | New Location | Lines | Action |
|---------------------------|--------------|-------|--------|
| `ProtocolHandler.java` | `tcp/ProtocolHandler.java` | 47 | Copy as-is |
| `ProtocolRouter.java` | `tcp/ProtocolRouter.java` | 57 | Copy as-is |
| `ConcoxV5ProtocolHandler.java` | `tcp/concox/ConcoxV5ProtocolHandler.java` | 446 | Copy + add fencing fields |
| `ConcoxV5TcpServer.java` | `tcp/concox/ConcoxV5TcpServer.java` | 197 | Copy as-is |
| `JT808ProtocolHandler.java` | `tcp/jt808/JT808ProtocolHandler.java` | 300 | Copy as-is |
| `JT808FrameParser.java` | `tcp/jt808/JT808FrameParser.java` | 150 | Copy as-is |
| `JT808MessageEncoder.java` | `tcp/jt808/JT808MessageEncoder.java` | 163 | Copy as-is |
| `JT808TcpServer.java` | `tcp/jt808/JT808TcpServer.java` | 139 | Copy as-is |
| `DeviceConnectionRegistry.java` | `tcp/DeviceConnectionRegistry.java` | 59 | Copy as-is |
| `GpsIngestService.java` | `service/` (adapted) | 305 | Copy + rename vehicle→machine |
| `LocationPersistenceService.java` | `service/` (adapted) | 268 | Copy + rename table names |
| `DeviceHeartbeatService.java` | `service/` (adapted) | — | Copy + adapt |
| `DeviceMappingCacheService.java` | `service/` (adapted) | — | Copy + adapt |

**Total reused code: ~1,900+ lines of proven, production-tested Java**

### 11.2 Protocol Handler Interface

**Source:** `ProtocolHandler.java` (47 lines)
**Action:** Copy as-is

```java
public interface ProtocolHandler {
    String getProtocolName();
    boolean canHandle(byte[] firstBytes);
    byte[] handlePacket(byte[] packet, String clientId);
    String getImeiForClient(String clientId);
    default void removeClient(String clientId) {}
}
```

### 11.3 Protocol Router

**Source:** `ProtocolRouter.java` (57 lines)
**Action:** Copy as-is

Spring auto-injects all `ProtocolHandler` implementations. The router inspects the first bytes of each packet and delegates to the matching handler.

### 11.4 Concox V5 / BR05 Protocol

**Source:** `ConcoxV5ProtocolHandler.java` (446 lines)
**Action:** Copy + minor adaptation

Handles:
- `0x01` — Login packet (IMEI extraction via BCD)
- `0x13` — Heartbeat packet (terminal info: ACC, charging, GPS, relay)
- `0x21` — Command reply (success/failure flag, terminal state)
- `0x22` — GPS location (lat/lng, speed, course, satellites, LBS)
- `0x26` — Alarm packet
- `0x94` — Information transmission

Key functions reused directly:
- `calculateCrc16()` — CRC-ITU checksum calculation
- `convertBcdToImei()` — BCD → IMEI conversion
- `buildResponse()` — Response packet construction
- `buildCommandPacket()` — Outgoing command packet construction
- `extractPackets()` — Split TCP stream into individual packets

### 11.5 JT808-2013 Protocol

**Source:** `JT808ProtocolHandler.java` + `JT808FrameParser.java` + `JT808MessageEncoder.java` (613 lines combined)
**Action:** Copy as-is

Handles:
- `0x0100` — Terminal registration
- `0x0102` — Authentication
- `0x0002` — Heartbeat
- `0x0200` — Location report (lat/lng, speed, direction, time)
- `0x0800` — Multimedia event
- `0x0801` — Multimedia data upload
- `0x0001` — Terminal general response

Key functions reused:
- `extractFrames()` — 0x7E delimiter-based frame extraction
- `unescape()` / `escape()` — 0x7D 0x02 / 0x7D 0x01 escape handling
- `validateChecksum()` — XOR checksum validation
- `buildFrame()` — Full frame construction
- `buildGeneralResponse()` — 0x8001 response

### 11.6 TCP Server & Connection Management

**Source:** `ConcoxV5TcpServer.java` (197 lines) + `JT808TcpServer.java` (139 lines) + `DeviceConnectionRegistry.java` (59 lines)
**Action:** Copy as-is

- `ConcoxV5TcpServer` — Accepts TCP connections on port 5000, spawns per-connection handlers
- `JT808TcpServer` — Accepts TCP connections on port 5001
- `DeviceConnectionRegistry` — Maps IMEI → active `OutputStream` for command dispatch

The `DeviceConnectionRegistry.sendCommand(imei, commandPacket)` method is the critical path for ON/OFF commands — it looks up the active socket by IMEI and writes the command packet directly.

### 11.7 GPS Ingest Pipeline

**Source:** `GpsIngestService.java` (305 lines) + `LocationPersistenceService.java` (268 lines)
**Action:** Copy + adapt (rename vehicle→machine, owner→tenant)

The ingest pipeline is split between the gateway and backend:
- **Telemetry parsing** stays in the gateway (extracts values from raw packets)
- **Persistence** moves to the backend (receives parsed telemetry via RabbitMQ)
- **Batching** is preserved (the `LocationPersistenceService` batch-insert pattern)

### 11.8 New Fencing Protocol Handler

A new `FencingProtocolHandler.java` is added alongside the reused handlers:

```java
@Component
public class FencingProtocolHandler implements ProtocolHandler {

    @Override
    public String getProtocolName() {
        return "YANTRAGO_FENCING";
    }

    @Override
    public boolean canHandle(byte[] firstBytes) {
        // Identify fencing machine packets by manufacturer-specific start bytes
        // Example: 0xAA 0x55 for fencing devices
        return firstBytes.length >= 2
            && firstBytes[0] == (byte) 0xAA
            && firstBytes[1] == (byte) 0x55;
    }

    @Override
    public byte[] handlePacket(byte[] packet, String clientId) {
        // Parse: login, heartbeat, GPS, voltage, battery, fencing state
        // Forward telemetry to TelemetryForwardService
        // Return response packet (ACK)
    }

    // Build ON/OFF command packets for fencing machines
    public byte[] buildOnCommand() { ... }
    public byte[] buildOffCommand() { ... }
}
```

This handler follows the **exact same pattern** as `ConcoxV5ProtocolHandler` and `JT808ProtocolHandler`, making it consistent with the existing architecture.

---

## 12. Module Dependency Map

```
                    ┌──────────────┐
                    │  Mobile App  │
                    │  (Flutter)   │
                    └──────┬───────┘
                           │ HTTP / WebSocket (STOMP)
                           │
                    ┌──────┴───────┐
                    │  Admin Web   │
                    │  (Next.js)   │
                    └──────┬───────┘
                           │ HTTP / WebSocket (STOMP)
                           ▼
              ┌────────────────────────────┐
              │   Spring Boot Backend API   │
              │   (Java 17)                 │
              │                             │
              │  Auth, RBAC, CRUD,          │
              │  Commands, Reports,         │
              │  Alerts, Notifications      │
              └───────────┬────────────────┘
                          │
                    ┌─────┴─────┐
                    │           │
                    ▼           ▼
              ┌─────────┐ ┌──────────┐
              │PostgreSQL│ │  Redis   │
              │+ PostGIS │ │ (Cache)  │
              └─────────┘ └──────────┘
                          ▲
                          │
              ┌───────────┴──────────────┐
              │      RabbitMQ             │
              │   (Message Broker)        │
              └───────────┬──────────────┘
                          │
                          ▼
         ┌──────────────────────────────────┐
         │   Spring Boot TCP Device Gateway  │
         │   (Java 17)                       │
         │                                   │
         │  ┌─────────────────────┐         │
         │  │  Protocol Router     │         │
         │  └──────────┬──────────┘         │
         │             │                     │
         │   ┌─────────┼──────────┐         │
         │   ▼         ▼          ▼         │
         │ Concox V5  JT808    Fencing      │
         │ Handler    Handler  Handler      │
         │ (reused)   (reused)  (NEW)       │
         │ └──────────┴─────────────────────│
         └──────────────┬───────────────────┘
                        │
                   GSM / TCP
                        │
                        ▼
         ┌──────────────────────────┐
         │  YantraGO Machine    │
         └──────────────────────────┘
```

### Shared Dependencies

| Component | Technology | Consumed By |
|-----------|-----------|-------------|
| `shared` module | Java library | backend, device-gateway, simulator |
| PostgreSQL 15 + PostGIS | Database | backend, device-gateway (read-only) |
| Redis 7 | Cache & session | backend, device-gateway |
| RabbitMQ 3 | Message broker | backend, device-gateway |
| Google Maps API | Maps | mobile, admin-web |
| SMTP (e.g. Postfix, SendGrid) | Email notifications | backend |
| SMS Gateway (e.g. Twilio, MSG91) | SMS notifications | backend |
| Push Provider (FCM/Expo/OneSignal) | Mobile push notifications | backend (optional) |
| Prometheus + Grafana | Monitoring | backend, device-gateway |

---

## 13. Environment Strategy

```
yantrago-platform/
├── backend/src/main/resources/
│   ├── application.yml              # Default
│   ├── application-dev.yml          # Development
│   ├── application-staging.yml      # Staging
│   └── application-prod.yml         # Production
│
├── device-gateway/src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-staging.yml
│   └── application-prod.yml
│
├── admin-web/.env.development
├── admin-web/.env.staging
├── admin-web/.env.production
└── .env.example                     # Template (committed, no secrets)
```

### Key Environment Variables

```yaml
# Database
spring.datasource.url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME:yantrago}
spring.datasource.username: ${DB_USERNAME}
spring.datasource.password: ${DB_PASSWORD}
spring.datasource.hikari.maximum-pool-size: 100

# Redis
spring.redis.host: ${REDIS_HOST}
spring.redis.port: 6379
spring.redis.password: ${REDIS_PASSWORD}

# RabbitMQ
spring.rabbitmq.host: ${RABBITMQ_HOST}
spring.rabbitmq.port: 5672
spring.rabbitmq.username: ${RABBITMQ_USERNAME}
spring.rabbitmq.password: ${RABBITMQ_PASSWORD}

# JWT
jwt.access.secret: ${JWT_ACCESS_SECRET}
jwt.refresh.secret: ${JWT_REFRESH_SECRET}
jwt.access.ttl: 900                # 15 minutes
jwt.refresh.ttl: 2592000           # 30 days

# TCP Gateway
tcp.server.port.concox: 5000
tcp.server.port.jt808: 5001
tcp.server.port.fencing: 5002
tcp.device.auth.timeout: 60000

# Mobile App
API_BASE_URL: http://api.yantrago.com/api/v1
WEBSOCKET_URL: ws://api.yantrago.com/ws

# Admin Web
NEXT_PUBLIC_API_URL: http://api.yantrago.com/api/v1
NEXT_PUBLIC_WS_URL: ws://api.yantrago.com/ws

# Push Notifications (optional — choose one provider)
push.provider: ${PUSH_PROVIDER:none}        # none | fcm | expo | onesignal
push.fcm.server.key: ${FCM_SERVER_KEY:}      # only if push.provider=fcm
push.expo.api.url: ${EXPO_API_URL:}          # only if push.provider=expo
push.onesignal.app.id: ${ONESIGNAL_APP_ID:}  # only if push.provider=onesignal

# Email (SMTP)
spring.mail.host: ${SMTP_HOST:}
spring.mail.port: ${SMTP_PORT:587}
spring.mail.username: ${SMTP_USERNAME:}
spring.mail.password: ${SMTP_PASSWORD:}

# SMS (optional)
sms.provider: ${SMS_PROVIDER:none}           # none | twilio | msg91
sms.twilio.account.sid: ${TWILIO_ACCOUNT_SID:}
sms.twilio.auth.token: ${TWILIO_AUTH_TOKEN:}

# Maps
google.maps.api.key: ${GOOGLE_MAPS_API_KEY}
```

---

## 14. CI/CD Pipeline Structure

```
.github/
├── workflows/
│   ├── backend-ci.yml            # Gradle build + test backend on PR
│   ├── gateway-ci.yml            # Gradle build + test gateway on PR
│   ├── mobile-ci.yml             # Flutter analyze + test on PR
│   ├── admin-web-ci.yml          # npm build + test admin-web on PR
│   ├── deploy-staging.yml        # Deploy to staging on merge to main
│   ├── deploy-production.yml     # Deploy to prod on release tag
│   ├── security-scan.yml         # Run Trivy/Snyk on dependencies
│   └── load-test.yml             # Run Java simulator against staging
```

### Pipeline Stages

```
PR Opened
    │
    ├──► Gradle Build (backend + gateway + shared)
    ├──► Unit Tests (JUnit 5)
    ├──► Integration Tests (Testcontainers)
    ├──► Flutter Analyze + Test
    ├──► Admin Web Build + Test
    ├──► Security Scan (Trivy)
    └──► PR Checks Pass
              │
              ▼
         Merge to main
              │
              ▼
         Build Docker Images
              │
              ▼
         Deploy to Staging
              │
              ▼
         Run Load Tests (Java simulator)
              │
              ▼
         QA Approval
              │
              ▼
         Create Release Tag
              │
              ▼
         Deploy to Production (Kubernetes)
              │
              ▼
         Post-Deploy Health Check
```

---

## 15. Why Java for the Entire Backend

### 15.1 Direct Code Reuse

The single biggest reason: **~1,900 lines of proven, production-tested TCP protocol code** can be copied directly as Java source files. No porting, no rewriting, no risk of introducing bugs in binary protocol handling.

### 15.2 Superior Binary Protocol Handling

Java has better tooling for binary/TCP protocols:

| Feature | Java | Node.js |
|---------|------|---------|
| Byte buffer manipulation | `ByteBuffer`, `DataInputStream` — mature, typed | `Buffer` — workable but less ergonomic |
| TCP server | `ServerSocket`, NIO, Netty — battle-tested | `net` module — decent but less mature |
| Concurrency | Thread-per-connection or NIO selectors | Single-threaded event loop (can block on CPU-heavy parsing) |
| Binary protocol libraries | Rich ecosystem (Netty, Mina) | Limited |
| Memory under sustained TCP load | Predictable | GC pressure with many Buffer allocations |

### 15.3 Spring Boot Is Production-Proven at Scale

Spring Boot handles 20,000+ users without issue. The HarvestTracker project already demonstrates:
- HikariCP connection pooling (tuned for production)
- WebSocket support (STOMP-based, working)
- Redis integration
- PostgreSQL + PostGIS
- Micrometer + Prometheus metrics
- Bucket4j rate limiting
- PDF export with PDFBox

### 15.4 Team Familiarity

The existing codebase is Java + Kotlin. The team already knows:
- Spring Boot patterns
- JPA / JdbcTemplate
- Gradle builds
- Java debugging and profiling

### 15.5 Single Build System

With Java for both backend and gateway:
- **One build tool** (Gradle) for all backend components
- **One language** (Java) for all backend code
- **One shared module** (`shared/`) compiled once, consumed by both
- **One CI pipeline** for all Java components
- **Consistent dependency management** via Gradle

### 15.6 Technology Stack Summary

| Component | Technology | Language |
|-----------|-----------|----------|
| Backend API | Spring Boot 3.x | Java 17 |
| TCP Device Gateway | Spring Boot 3.x | Java 17 |
| Shared Library | Java Library | Java 17 |
| Device Simulator | Spring Boot / CLI | Java 17 |
| Mobile App | Flutter | Dart |
| Admin Web | Next.js 14+ | TypeScript |
| Database | PostgreSQL 15 + PostGIS | SQL |
| Cache | Redis 7 | — |
| Message Broker | RabbitMQ 3 | — |
| Monitoring | Prometheus + Grafana | — |
| CI/CD | GitHub Actions | YAML |
| Cloud Provider | Vultr Cloud Compute | Ubuntu 22.04 LTS |
| Container | Docker (local dev) | Dockerfile/YAML |

---

## 16. Vultr Infrastructure Setup

This section provides the complete step-by-step guide for creating and configuring Vultr instances for the YantraGO platform.

### 16.1 Prerequisites

- Vultr account (create at https://www.vultr.com/register/)
- SSH key generated on your local machine
- Basic knowledge of Linux commands

### 16.2 Generate SSH Key (if not already done)

```powershell
# On Windows PowerShell
ssh-keygen -t rsa -b 4096 -C "yantrago@vultr" -f "$env:USERPROFILE\.ssh\yantrago_vultr"
```

Retrieve the public key to add to Vultr:

```powershell
Get-Content "$env:USERPROFILE\.ssh\yantrago_vultr.pub"
```

### 16.3 Add SSH Key to Vultr

1. Login to Vultr dashboard at https://my.vultr.com/
2. Go to **SSH Keys** in the left sidebar
3. Click **"+ Add SSH Key"**
4. **Name:** `YantraGO Key`
5. **Key:** Paste the public key from above
6. Click **"Add SSH Key"**

### 16.4 Vultr Instance Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Vultr Cloud                       │
│                                                      │
│  ┌──────────────┐     ┌──────────────────────────┐  │
│  │  DB Server    │     │  App Server (Backend)     │  │
│  │  PostgreSQL   │◄────│  Spring Boot API          │  │
│  │  + PostGIS    │     │  Port 8080 (via Nginx)    │  │
│  │  Port 5432    │     │  Nginx + SSL              │  │
│  │  (internal)   │     │  Port 80/443              │  │
│  └──────────────┘     └──────────────────────────┘  │
│         ▲                          ▲                 │
│         │                          │                 │
│  ┌──────┴──────────┐     ┌────────┴───────────────┐  │
│  │  Redis Server    │     │  Gateway Server         │  │
│  │  Cache + Sessions│     │  TCP Device Gateway     │  │
│  │  Port 6379       │     │  Port 5000, 5001, 5002  │  │
│  │  (internal)      │     │  (public for devices)   │  │
│  └──────────────────┘     └─────────────────────────┘  │
│                                  ▲                     │
│                           ┌──────┴──────────────┐      │
│                           │  RabbitMQ Server     │      │
│                           │  Message Broker      │      │
│                           │  Port 5672 (internal)│      │
│                           └─────────────────────┘      │
└─────────────────────────────────────────────────────┘
```

### 16.5 Create Database Server (PostgreSQL + PostGIS)

#### Deploy Instance on Vultr

1. Go to **Servers** → **"+ Deploy Server"**
2. Configure:

| Setting | Value |
|---------|-------|
| Server Type | Cloud Compute → Regular Performance |
| Location | Choose closest to users (Mumbai/Singapore/Tokyo) |
| Image | Ubuntu 22.04 LTS x64 |
| Size | **4 GB RAM, 2 CPU, 80 GB SSD** ($24/month) |
| Auto Backups | Enable ($2/month) |
| DDoS Protection | Enable ($10/month) |
| Hostname | `yantrago-db` |
| Label | `YantraGO Database Server` |
| SSH Key | Select `YantraGO Key` |

3. Click **"Deploy Now"**
4. Wait 2-3 minutes
5. **Note the server IP address** — this is `YOUR_DB_IP`

#### Install PostgreSQL + PostGIS

```bash
# Connect via SSH
ssh -i ~/.ssh/yantrago_vultr root@YOUR_DB_IP

# Update system
apt update && apt upgrade -y

# Install PostgreSQL 15 + PostGIS
apt install postgresql-15 postgis postgresql-15-postgis-3 -y

# Verify
psql --version
systemctl start postgresql
systemctl enable postgresql
```

#### Create Database and User

```bash
sudo -u postgres psql
```

```sql
-- Create database
CREATE DATABASE yantrago;

-- Create user with strong password (REPLACE THIS!)
CREATE USER yantrago WITH PASSWORD 'YourStrongDbPassword2026!';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE yantrago TO yantrago;

-- Connect to database and enable PostGIS
\c yantrago
CREATE EXTENSION postgis;
SELECT PostGIS_Version();

-- Exit
\q
```

#### Configure PostgreSQL for Remote Access

```bash
# Edit main config
sudo nano /etc/postgresql/15/main/postgresql.conf
```

Modify these lines:

```ini
listen_addresses = '*'
max_connections = 200
shared_buffers = 1GB
effective_cache_size = 3GB
work_mem = 16MB
maintenance_work_mem = 256MB
```

```bash
# Edit pg_hba.conf to allow app server connection
sudo nano /etc/postgresql/15/main/pg_hba.conf
```

Add at the end (replace `YOUR_APP_IP` with the app server IP):

```ini
# Allow app server to connect
host    yantrago    yantrago    YOUR_APP_IP/32    scram-sha-256
host    yantrago    yantrago    YOUR_GATEWAY_IP/32    scram-sha-256
```

```bash
# Restart PostgreSQL
sudo systemctl restart postgresql

# Configure firewall
ufw allow 22/tcp
ufw allow from YOUR_APP_IP to any port 5432
ufw allow from YOUR_GATEWAY_IP to any port 5432
ufw enable
ufw status
```

### 16.6 Create Application Server (Backend API)

#### Deploy Instance on Vultr

| Setting | Value |
|---------|-------|
| Server Type | Cloud Compute → Regular Performance |
| Location | Same region as DB server |
| Image | Ubuntu 22.04 LTS x64 |
| Size | **2 GB RAM, 1 CPU, 40 GB SSD** ($12/month) |
| Auto Backups | Enable ($2/month) |
| DDoS Protection | Enable ($10/month) |
| Hostname | `yantrago-app` |
| Label | `YantraGO Application Server` |
| SSH Key | Select `YantraGO Key` |

**Note the server IP** — this is `YOUR_APP_IP`

#### Install Java 17 + Nginx

```bash
ssh -i ~/.ssh/yantrago_vultr root@YOUR_APP_IP

# Update system
apt update && apt upgrade -y

# Install Java 17
apt install openjdk-17-jdk -y
java -version

# Set JAVA_HOME
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc

# Install Nginx (reverse proxy)
apt install nginx -y
systemctl start nginx
systemctl enable nginx

# Install useful tools
apt install git curl wget unzip -y
```

#### Create Application User and Directory

```bash
# Create dedicated user
useradd -r -s /bin/false yantrago

# Create directories
mkdir -p /opt/yantrago/backend
mkdir -p /opt/yantrago/logs
mkdir -p /opt/yantrago/config
chown -R yantrago:yantrago /opt/yantrago
```

#### Configure Firewall

```bash
ufw allow 22/tcp       # SSH
ufw allow 80/tcp       # HTTP
ufw allow 443/tcp      # HTTPS
ufw enable
ufw status
```

#### Create application-prod.yml

```bash
nano /opt/yantrago/config/application-prod.yml
```

```yaml
server:
  port: 8080
  compression:
    enabled: true

spring:
  application:
    name: yantrago-backend

  datasource:
    url: jdbc:postgresql://YOUR_DB_IP:5432/yantrago
    username: yantrago
    password: YourStrongDbPassword2026!
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: YantraGoHikariPool
      maximum-pool-size: 100
      minimum-idle: 20
      idle-timeout: 300000
      max-lifetime: 1800000
      connection-timeout: 30000

  data:
    redis:
      host: YOUR_REDIS_IP
      port: 6379
      password: YourRedisPassword2026!

  rabbitmq:
    host: YOUR_RABBITMQ_IP
    port: 5672
    username: yantrago
    password: YourRabbitMqPassword2026!

  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  access:
    secret: ${JWT_ACCESS_SECRET:ChangeThisToRandom256BitHex}
    ttl: 900
  refresh:
    secret: ${JWT_REFRESH_SECRET:ChangeThisToDifferentRandom256BitHex}
    ttl: 2592000

logging:
  level:
    com.yantrago: INFO
  file:
    name: /opt/yantrago/logs/backend.log
```

```bash
chown yantrago:yantrago /opt/yantrago/config/application-prod.yml
chmod 600 /opt/yantrago/config/application-prod.yml
```

#### Create Systemd Service for Backend

```bash
nano /etc/systemd/system/yantrago-backend.service
```

```ini
[Unit]
Description=YantraGO Backend API
After=network.target

[Service]
Type=simple
User=yantrago
Group=yantrago
WorkingDirectory=/opt/yantrago/backend
ExecStart=/usr/bin/java -Xmx1536m -jar /opt/yantrago/backend/backend.jar --spring.config.location=file:/opt/yantrago/config/application-prod.yml --spring.profiles.active=prod
Restart=always
RestartSec=10
StandardOutput=append:/opt/yantrago/logs/backend-stdout.log
StandardError=append:/opt/yantrago/logs/backend-error.log

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable yantrago-backend
```

#### Configure Nginx Reverse Proxy

```bash
nano /etc/nginx/sites-available/yantrago
```

```nginx
server {
    listen 80;
    server_name api.yantrago.com;  # Replace with your domain or IP

    # Redirect to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.yantrago.com;

    # SSL certificates (use Let's Encrypt — see Section 17.3)
    ssl_certificate /etc/letsencrypt/live/api.yantrago.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yantrago.com/privkey.pem;

    # API proxy
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket proxy
    location /ws/ {
        proxy_pass http://localhost:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }

    # Actuator health (restrict access)
    location /actuator/health {
        proxy_pass http://localhost:8080/actuator/health;
        allow 127.0.0.1;
        deny all;
    }
}
```

```bash
ln -s /etc/nginx/sites-available/yantrago /etc/nginx/sites-enabled/
nginx -t
systemctl restart nginx
```

### 16.7 Create TCP Gateway Server

#### Deploy Instance on Vultr

| Setting | Value |
|---------|-------|
| Server Type | Cloud Compute → Regular Performance |
| Location | Same region as other servers |
| Image | Ubuntu 22.04 LTS x64 |
| Size | **2 GB RAM, 1 CPU, 40 GB SSD** ($12/month) |
| Auto Backups | Enable ($2/month) |
| Hostname | `yantrago-gateway` |
| Label | `YantraGO TCP Gateway` |
| SSH Key | Select `YantraGO Key` |

**Note the server IP** — this is `YOUR_GATEWAY_IP`

> **Important:** This server must have **public IP** with TCP ports 5000, 5001, 5002 open because YantraGO machines connect directly to it over GSM/TCP.

#### Install Java 17

```bash
ssh -i ~/.ssh/yantrago_vultr root@YOUR_GATEWAY_IP

apt update && apt upgrade -y
apt install openjdk-17-jdk -y
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc

# Create user and directories
useradd -r -s /bin/false yantrago
mkdir -p /opt/yantrago/gateway
mkdir -p /opt/yantrago/logs
mkdir -p /opt/yantrago/config
chown -R yantrago:yantrago /opt/yantrago
```

#### Configure Firewall (TCP ports for devices)

```bash
ufw allow 22/tcp       # SSH
ufw allow 5000/tcp     # Concox V5 devices
ufw allow 5001/tcp     # JT808 devices
ufw allow 5002/tcp     # YantraGO fencing devices
ufw enable
ufw status
```

#### Create gateway application-prod.yml

```bash
nano /opt/yantrago/config/application-prod.yml
```

```yaml
server:
  port: 8081  # Internal management port (actuator)

tcp:
  server:
    port:
      concox: 5000
      jt808: 5001
      fencing: 5002
    device:
      auth:
        timeout: 60000

spring:
  application:
    name: yantrago-gateway

  datasource:
    url: jdbc:postgresql://YOUR_DB_IP:5432/yantrago
    username: yantrago
    password: YourStrongDbPassword2026!
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  data:
    redis:
      host: YOUR_REDIS_IP
      port: 6379
      password: YourRedisPassword2026!

  rabbitmq:
    host: YOUR_RABBITMQ_IP
    port: 5672
    username: yantrago
    password: YourRabbitMqPassword2026!

logging:
  level:
    com.yantrago.gateway: INFO
  file:
    name: /opt/yantrago/logs/gateway.log
```

#### Create Systemd Service for Gateway

```bash
nano /etc/systemd/system/yantrago-gateway.service
```

```ini
[Unit]
Description=YantraGO TCP Device Gateway
After=network.target

[Service]
Type=simple
User=yantrago
Group=yantrago
WorkingDirectory=/opt/yantrago/gateway
ExecStart=/usr/bin/java -Xmx1024m -jar /opt/yantrago/gateway/gateway.jar --spring.config.location=file:/opt/yantrago/config/application-prod.yml --spring.profiles.active=prod
Restart=always
RestartSec=10
StandardOutput=append:/opt/yantrago/logs/gateway-stdout.log
StandardError=append:/opt/yantrago/logs/gateway-error.log

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable yantrago-gateway
```

### 16.8 Create Redis Server

#### Deploy Instance on Vultr

| Setting | Value |
|---------|-------|
| Server Type | Cloud Compute → Regular Performance |
| Location | Same region |
| Image | Ubuntu 22.04 LTS x64 |
| Size | **1 GB RAM, 1 CPU, 20 GB SSD** ($6/month) |
| Auto Backups | Enable ($1/month) |
| Hostname | `yantrago-redis` |
| Label | `YantraGO Redis Server` |
| SSH Key | Select `YantraGO Key` |

**Note the server IP** — this is `YOUR_REDIS_IP`

#### Install and Configure Redis

```bash
ssh -i ~/.ssh/yantrago_vultr root@YOUR_REDIS_IP

apt update && apt upgrade -y
apt install redis-server -y

# Configure Redis
nano /etc/redis/redis.conf
```

Modify these lines:

```ini
bind 0.0.0.0
protected-mode yes
requirepass YourRedisPassword2026!
maxmemory 512mb
maxmemory-policy allkeys-lru
```

```bash
systemctl restart redis
systemctl enable redis

# Firewall — only allow app and gateway servers
ufw allow 22/tcp
ufw allow from YOUR_APP_IP to any port 6379
ufw allow from YOUR_GATEWAY_IP to any port 6379
ufw enable

# Test
redis-cli
AUTH YourRedisPassword2026!
PING
# Should return: PONG
```

### 16.9 Create RabbitMQ Server

#### Deploy Instance on Vultr

| Setting | Value |
|---------|-------|
| Server Type | Cloud Compute → Regular Performance |
| Location | Same region |
| Image | Ubuntu 22.04 LTS x64 |
| Size | **1 GB RAM, 1 CPU, 20 GB SSD** ($6/month) |
| Auto Backups | Enable ($1/month) |
| Hostname | `yantrago-rabbitmq` |
| Label | `YantraGO RabbitMQ Server` |
| SSH Key | Select `YantraGO Key` |

**Note the server IP** — this is `YOUR_RABBITMQ_IP`

#### Install and Configure RabbitMQ

```bash
ssh -i ~/.ssh/yantrago_vultr root@YOUR_RABBITMQ_IP

apt update && apt upgrade -y

# Install Erlang + RabbitMQ
apt install rabbitmq-server -y
systemctl start rabbitmq-server
systemctl enable rabbitmq-server

# Enable management plugin
rabbitmq-plugins enable rabbitmq_management

# Create user
rabbitmqctl add_user yantrago YourRabbitMqPassword2026!
rabbitmqctl set_user_tags yantrago administrator
rabbitmqctl set_permissions -p / yantrago ".*" ".*" ".*"

# Firewall — only allow app and gateway
ufw allow 22/tcp
ufw allow from YOUR_APP_IP to any port 5672
ufw allow from YOUR_GATEWAY_IP to any port 5672
ufw enable

# Test management UI (accessible from browser at http://YOUR_RABBITMQ_IP:15672)
# Login: yantrago / YourRabbitMqPassword2026!
```

### 16.10 Deploy Backend JAR to App Server

#### Build on Local Machine

```powershell
# On Windows
cd D:\yantrago-platform
.\gradlew.bat :backend:bootJar -x test
# JAR at: backend/build/libs/backend-1.0.0.jar
```

#### Upload to Vultr App Server

```powershell
# Using SCP
scp -i $env:USERPROFILE\.ssh\yantrago_vultr `
  backend\build\libs\backend-1.0.0.jar `
  root@YOUR_APP_IP:/opt/yantrago/backend/backend.jar
```

#### Start the Service

```bash
# On app server
ssh -i ~/.ssh/yantrago_vultr root@YOUR_APP_IP

chown yantrago:yantrago /opt/yantrago/backend/backend.jar
systemctl start yantrago-backend
systemctl status yantrago-backend

# Check logs
journalctl -u yantrago-backend -f
```

#### Verify

```bash
# Health check
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# From your local machine (after Nginx + SSL setup)
curl https://api.yantrago.com/actuator/health
```

### 16.11 Deploy Gateway JAR to Gateway Server

#### Build on Local Machine

```powershell
.\gradlew.bat :device-gateway:bootJar -x test
# JAR at: device-gateway/build/libs/device-gateway-1.0.0.jar
```

#### Upload and Start

```powershell
scp -i $env:USERPROFILE\.ssh\yantrago_vultr `
  device-gateway\build\libs\device-gateway-1.0.0.jar `
  root@YOUR_GATEWAY_IP:/opt/yantrago/gateway/gateway.jar
```

```bash
# On gateway server
chown yantrago:yantrago /opt/yantrago/gateway/gateway.jar
systemctl start yantrago-gateway
systemctl status yantrago-gateway
journalctl -u yantrago-gateway -f
```

### 16.12 Run Database Migrations

Flyway migrations run automatically when the backend starts. Verify:

```bash
# On app server
journalctl -u yantrago-backend | grep -i flyway

# Or check database directly
sudo -u postgres psql -d yantrago -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

### 16.13 Update Mobile App Configuration

After servers are running, update the mobile app to point to the new backend:

```dart
// mobile/lib/core/config/app_config.dart
class AppConfig {
  // Replace with your Vultr app server domain/IP
  static const String apiBaseUrl = 'https://api.yantrago.com/api/v1';
  static const String websocketUrl = 'wss://api.yantrago.com/ws';
}
```

### 16.14 Vultr Instance Summary

| Server | Hostname | Specs | Monthly Cost | Ports |
|--------|----------|-------|-------------|-------|
| Database | `yantrago-db` | 4GB RAM, 2 CPU, 80GB SSD | $36 | 5432 (internal) |
| Backend API | `yantrago-app` | 2GB RAM, 1 CPU, 40GB SSD | $24 | 80, 443 (public) |
| TCP Gateway | `yantrago-gateway` | 2GB RAM, 1 CPU, 40GB SSD | $24 | 5000, 5001, 5002 (public) |
| Redis | `yantrago-redis` | 1GB RAM, 1 CPU, 20GB SSD | $7 | 6379 (internal) |
| RabbitMQ | `yantrago-rabbitmq` | 1GB RAM, 1 CPU, 20GB SSD | $7 | 5672 (internal) |
| **Total** | | | **~$98/month** | |

> **Cost-saving option:** For initial launch, you can combine Redis + RabbitMQ on the same server (1GB RAM) and combine Backend + Gateway on one server (4GB RAM). This reduces cost to ~$60/month but limits independent scaling.

---

## 17. Vultr Server Management

### 17.1 SSH Access

```powershell
# Connect to each server from Windows
ssh -i $env:USERPROFILE\.ssh\yantrago_vultr root@YOUR_DB_IP
ssh -i $env:USERPROFILE\.ssh\yantrago_vultr root@YOUR_APP_IP
ssh -i $env:USERPROFILE\.ssh\yantrago_vultr root@YOUR_GATEWAY_IP
ssh -i $env:USERPROFILE\.ssh\yantrago_vultr root@YOUR_REDIS_IP
ssh -i $env:USERPROFILE\.ssh\yantrago_vultr root@YOUR_RABBITMQ_IP
```

### 17.2 Service Management Commands

```bash
# Backend API
systemctl start yantrago-backend
systemctl stop yantrago-backend
systemctl restart yantrago-backend
systemctl status yantrago-backend
journalctl -u yantrago-backend -f          # Follow logs

# TCP Gateway
systemctl start yantrago-gateway
systemctl stop yantrago-gateway
systemctl restart yantrago-gateway
systemctl status yantrago-gateway
journalctl -u yantrago-gateway -f

# PostgreSQL
systemctl restart postgresql
systemctl status postgresql

# Redis
systemctl restart redis
systemctl status redis

# RabbitMQ
systemctl restart rabbitmq-server
systemctl status rabbitmq-server

# Nginx
systemctl restart nginx
nginx -t                                    # Test config
```

### 17.3 SSL/TLS with Let's Encrypt (Free)

```bash
# On app server
apt install certbot python3-certbot-nginx -y

# Get SSL certificate (replace with your domain)
certbot --nginx -d api.yantrago.com

# Auto-renewal is set up automatically. Test it:
certbot renew --dry-run
```

### 17.4 Database Backup

#### Manual Backup

```bash
# On database server
sudo -u postgres pg_dump yantrago > /tmp/yantrago_backup_$(date +%Y%m%d).sql

# Download to local machine
scp -i ~/.ssh/yantrago_vultr root@YOUR_DB_IP:/tmp/yantrago_backup_*.sql ./
```

#### Automated Daily Backup (Cron)

```bash
# On database server
crontab -e
```

Add:

```cron
# Daily backup at 2 AM
0 2 * * * sudo -u postgres pg_dump yantrago | gzip > /var/backups/yantrago_$(date +\%Y\%m\%d).sql.gz

# Delete backups older than 30 days
0 3 * * * find /var/backups/ -name "yantrago_*.sql.gz" -mtime +30 -delete
```

### 17.5 Vultr Snapshots

In addition to auto backups, take manual snapshots before deployments:

1. Vultr Dashboard → **Servers** → Select server → **Snapshots**
2. Name: `yantrago-app-pre-deploy-YYYYMMDD`
3. Snapshots are stored off-server and can be used to restore

### 17.6 Monitoring Setup

#### Install Prometheus Node Exporter on each server

```bash
# On each server
apt install prometheus-node-exporter -y
systemctl start prometheus-node-exporter
systemctl enable prometheus-node-exporter
```

#### Install Prometheus + Grafana on app server (or a separate monitoring server)

```bash
apt install prometheus grafana -y

# Configure Prometheus to scrape all servers
nano /etc/prometheus/prometheus.yml
```

```yaml
scrape_configs:
  - job_name: 'yantrago-backend'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: /actuator/prometheus

  - job_name: 'yantrago-gateway'
    static_configs:
      - targets: ['YOUR_GATEWAY_IP:8081']
    metrics_path: /actuator/prometheus

  - job_name: 'node-exporters'
    static_configs:
      - targets:
        - 'localhost:9100'
        - 'YOUR_DB_IP:9100'
        - 'YOUR_GATEWAY_IP:9100'
        - 'YOUR_REDIS_IP:9100'
        - 'YOUR_RABBITMQ_IP:9100'
```

```bash
systemctl restart prometheus
systemctl enable grafana-server
systemctl start grafana-server

# Access Grafana at http://YOUR_APP_IP:3000
# Default login: admin / admin (change immediately)
```

### 17.7 Deploying Updates

#### Backend Update

```powershell
# Build new JAR
.\gradlew.bat :backend:bootJar -x test

# Upload
scp -i $env:USERPROFILE\.ssh\yantrago_vultr `
  backend\build\libs\backend-1.0.0.jar `
  root@YOUR_APP_IP:/opt/yantrago/backend/backend.jar
```

```bash
# Restart service
ssh -i ~/.ssh/yantrago_vultr root@YOUR_APP_IP
systemctl restart yantrago-backend
systemctl status yantrago-backend
```

#### Gateway Update

```powershell
.\gradlew.bat :device-gateway:bootJar -x test

scp -i $env:USERPROFILE\.ssh\yantrago_vultr `
  device-gateway\build\libs\device-gateway-1.0.0.jar `
  root@YOUR_GATEWAY_IP:/opt/yantrago/gateway/gateway.jar
```

```bash
ssh -i ~/.ssh/yantrago_vultr root@YOUR_GATEWAY_IP
systemctl restart yantrago-gateway
systemctl status yantrago-gateway
```

### 17.8 Security Checklist

- [ ] SSH key authentication enabled (password auth disabled)
- [ ] UFW firewall configured on every server
- [ ] PostgreSQL only accessible from app + gateway IPs
- [ ] Redis only accessible from app + gateway IPs
- [ ] RabbitMQ only accessible from app + gateway IPs
- [ ] Strong passwords for DB, Redis, RabbitMQ (not defaults)
- [ ] SSL/TLS configured via Let's Encrypt
- [ ] Vultr auto backups enabled on all servers
- [ ] `application-prod.yml` permissions set to 600
- [ ] System services run as `yantrago` user (not root)
- [ ] Nginx rate limiting configured
- [ ] JWT secrets are strong random values

### 17.9 Troubleshooting

#### Database Connection Issues

```bash
# Check PostgreSQL status
systemctl status postgresql
sudo tail -f /var/log/postgresql/postgresql-15-main.log

# Test connection from app server
psql -h YOUR_DB_IP -U yantrago -d yantrago -c "SELECT 1;"

# Check if pg_hba.conf allows app server IP
cat /etc/postgresql/15/main/pg_hba.conf | grep yantrago
```

#### Backend Won't Start

```bash
systemctl status yantrago-backend
journalctl -u yantrago-backend -f
tail -f /opt/yantrago/logs/backend-error.log

# Common issues:
# 1. Database not reachable → check DB IP in application-prod.yml
# 2. Redis not reachable → check Redis IP in application-prod.yml
# 3. RabbitMQ not reachable → check RabbitMQ IP in application-prod.yml
# 4. Java not found → check 'java -version'
# 5. Port 8080 in use → check 'ss -tlnp | grep 8080'
```

#### Gateway Not Receiving Device Connections

```bash
# Check if TCP ports are listening
ss -tlnp | grep -E '5000|5001|5002'

# Check firewall
ufw status

# Test from local machine
telnet YOUR_GATEWAY_IP 5000
# Or
nc -zv YOUR_GATEWAY_IP 5000
```

---

## 18. Vultr Cost Breakdown

### 18.1 Single-Server Development Setup (Start Here)

For development and early testing, run **everything on one Vultr server**. This is the cheapest option and lets you develop, test, and even onboard your first few customers before scaling.

#### Create One Server

| Setting | Value |
|---------|-------|
| Server Type | Cloud Compute → Regular Performance |
| Location | Choose closest to users |
| Image | Ubuntu 22.04 LTS x64 |
| Size | **8 GB RAM, 4 CPU, 160 GB SSD** ($48/month) |
| Auto Backups | Enable ($5/month) |
| DDoS Protection | Enable ($10/month) |
| Hostname | `yantrago-dev` |
| Label | `YantraGO All-in-One Dev Server` |
| SSH Key | Select `YantraGO Key` |

**Total: ~$63/month**

> If budget is tight, you can start with **4 GB RAM, 2 CPU, 80 GB SSD** ($24/month + backups + DDoS = ~$36/month). This works for development but may need upgrading once you have real devices connected.

#### What Runs on This Single Server

```
┌─────────────────────────────────────────────────────┐
│              YantraGO Dev Server (1 Vultr Instance)  │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  PostgreSQL 15 + PostGIS     (port 5432)       │  │
│  │  Redis 7                     (port 6379)       │  │
│  │  RabbitMQ 3                  (port 5672)       │  │
│  │  Spring Boot Backend API     (port 8080)       │  │
│  │  Spring Boot TCP Gateway     (port 5000-5002)  │  │
│  │  Nginx Reverse Proxy + SSL   (port 80/443)     │  │
│  │  Prometheus + Grafana        (port 9090/3000)  │  │
│  └────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

#### Complete Single-Server Setup Script

Connect to your new server and run this complete setup:

```bash
ssh -i ~/.ssh/yantrago_vultr root@YOUR_SERVER_IP
```

```bash
#!/bin/bash
# ===== YANTRAGO SINGLE-SERVER SETUP =====
# Run this on a fresh Ubuntu 22.04 Vultr instance

set -e

echo "=== Updating system ==="
apt update && apt upgrade -y

echo "=== Installing Java 17 ==="
apt install openjdk-17-jdk -y
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc
java -version

echo "=== Installing PostgreSQL 15 + PostGIS ==="
apt install postgresql-15 postgis postgresql-15-postgis-3 -y
systemctl start postgresql
systemctl enable postgresql

# Create database and user
sudo -u postgres psql <<'EOF'
CREATE DATABASE yantrago;
CREATE USER yantrago WITH PASSWORD 'ChangeThisDbPassword2026!';
GRANT ALL PRIVILEGES ON DATABASE yantrago TO yantrago;
\c yantrago
CREATE EXTENSION postgis;
EOF

# Configure PostgreSQL
sed -i "s/#listen_addresses = 'localhost'/listen_addresses = 'localhost'/" /etc/postgresql/15/main/postgresql.conf
sed -i "s/#max_connections = 100/max_connections = 100/" /etc/postgresql/15/main/postgresql.conf
systemctl restart postgresql

echo "=== Installing Redis ==="
apt install redis-server -y
sed -i 's/# requirepass foobared/requirepass ChangeThisRedisPassword2026!/' /etc/redis/redis.conf
systemctl restart redis
systemctl enable redis

echo "=== Installing RabbitMQ ==="
apt install rabbitmq-server -y
systemctl start rabbitmq-server
systemctl enable rabbitmq-server
rabbitmq-plugins enable rabbitmq_management
rabbitmqctl add_user yantrago ChangeThisRabbitMqPassword2026!
rabbitmqctl set_user_tags yantrago administrator
rabbitmqctl set_permissions -p / yantrago ".*" ".*" ".*"

echo "=== Installing Nginx ==="
apt install nginx -y
systemctl start nginx
systemctl enable nginx

echo "=== Installing useful tools ==="
apt install git curl wget unzip certbot python3-certbot-nginx -y

echo "=== Creating yantrago user ==="
useradd -r -s /bin/false yantrago
mkdir -p /opt/yantrago/backend
mkdir -p /opt/yantrago/gateway
mkdir -p /opt/yantrago/logs
mkdir -p /opt/yantrago/config
chown -R yantrago:yantrago /opt/yantrago

echo "=== Configuring firewall ==="
ufw allow 22/tcp       # SSH
ufw allow 80/tcp       # HTTP
ufw allow 443/tcp      # HTTPS
ufw allow 5000/tcp     # Concox V5 devices
ufw allow 5001/tcp     # JT808 devices
ufw allow 5002/tcp     # YantraGO fencing devices
ufw --force enable
ufw status

echo "=== Setup complete! ==="
echo "Next steps:"
echo "1. Create application-prod.yml in /opt/yantrago/config/"
echo "2. Build and upload backend.jar and gateway.jar"
echo "3. Create systemd services"
echo "4. Configure Nginx"
echo "5. Set up SSL with certbot"
```

#### Create Single-Server application-prod.yml

Since everything runs on one server, all connections use `localhost`:

```bash
nano /opt/yantrago/config/application-prod.yml
```

```yaml
server:
  port: 8080
  compression:
    enabled: true

spring:
  application:
    name: yantrago-backend

  datasource:
    url: jdbc:postgresql://localhost:5432/yantrago
    username: yantrago
    password: ChangeThisDbPassword2026!
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: YantraGoHikariPool
      maximum-pool-size: 30
      minimum-idle: 10
      idle-timeout: 300000
      max-lifetime: 1800000
      connection-timeout: 30000

  data:
    redis:
      host: localhost
      port: 6379
      password: ChangeThisRedisPassword2026!

  rabbitmq:
    host: localhost
    port: 5672
    username: yantrago
    password: ChangeThisRabbitMqPassword2026!

  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  access:
    secret: ChangeThisToRandom256BitHexString
    ttl: 900
  refresh:
    secret: ChangeThisToDifferentRandom256BitHex
    ttl: 2592000

tcp:
  server:
    port:
      concox: 5000
      jt808: 5001
      fencing: 5002
    device:
      auth:
        timeout: 60000

logging:
  level:
    com.yantrago: INFO
  file:
    name: /opt/yantrago/logs/backend.log
```

#### Create Systemd Services (Both on One Server)

**Backend service:**

```bash
nano /etc/systemd/system/yantrago-backend.service
```

```ini
[Unit]
Description=YantraGO Backend API
After=network.target postgresql redis-server rabbitmq-server

[Service]
Type=simple
User=yantrago
Group=yantrago
WorkingDirectory=/opt/yantrago/backend
ExecStart=/usr/bin/java -Xmx2048m -jar /opt/yantrago/backend/backend.jar --spring.config.location=file:/opt/yantrago/config/application-prod.yml --spring.profiles.active=prod
Restart=always
RestartSec=10
StandardOutput=append:/opt/yantrago/logs/backend-stdout.log
StandardError=append:/opt/yantrago/logs/backend-error.log

[Install]
WantedBy=multi-user.target
```

**Gateway service:**

```bash
nano /etc/systemd/system/yantrago-gateway.service
```

```ini
[Unit]
Description=YantraGO TCP Device Gateway
After=network.target postgresql redis-server rabbitmq-server

[Service]
Type=simple
User=yantrago
Group=yantrago
WorkingDirectory=/opt/yantrago/gateway
ExecStart=/usr/bin/java -Xmx1536m -jar /opt/yantrago/gateway/gateway.jar --spring.config.location=file:/opt/yantrago/config/application-prod.yml --spring.profiles.active=prod
Restart=always
RestartSec=10
StandardOutput=append:/opt/yantrago/logs/gateway-stdout.log
StandardError=append:/opt/yantrago/logs/gateway-error.log

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable yantrago-backend yantrago-gateway
```

#### Configure Nginx (Single Server)

```bash
nano /etc/nginx/sites-available/yantrago
```

```nginx
server {
    listen 80;
    server_name _;  # Use your domain or IP

    # API proxy
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket proxy
    location /ws/ {
        proxy_pass http://localhost:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }

    # Admin web (if hosted on same server)
    location / {
        proxy_pass http://localhost:3001/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Actuator health (internal only)
    location /actuator/health {
        proxy_pass http://localhost:8080/actuator/health;
        allow 127.0.0.1;
        deny all;
    }
}
```

```bash
rm -f /etc/nginx/sites-enabled/default
ln -s /etc/nginx/sites-available/yantrago /etc/nginx/sites-enabled/
nginx -t
systemctl restart nginx
```

#### Set Up SSL (When You Have a Domain)

```bash
# Replace api.yantrago.com with your actual domain
certbot --nginx -d api.yantrago.com
certbot renew --dry-run
```

#### Deploy JARs to Single Server

```powershell
# Build on Windows
cd D:\yantrago-platform
.\gradlew.bat :backend:bootJar :device-gateway:bootJar -x test

# Upload both JARs
scp -i $env:USERPROFILE\.ssh\yantrago_vultr `
  backend\build\libs\backend-1.0.0.jar `
  root@YOUR_SERVER_IP:/opt/yantrago/backend/backend.jar

scp -i $env:USERPROFILE\.ssh\yantrago_vultr `
  device-gateway\build\libs\device-gateway-1.0.0.jar `
  root@YOUR_SERVER_IP:/opt/yantrago/gateway/gateway.jar
```

```bash
# Start services
ssh -i ~/.ssh/yantrago_vultr root@YOUR_SERVER_IP
chown yantrago:yantrago /opt/yantrago/backend/backend.jar /opt/yantrago/gateway/gateway.jar
systemctl start yantrago-backend
systemctl start yantrago-gateway
systemctl status yantrago-backend
systemctl status yantrago-gateway
```

#### Verify Everything Works

```bash
# Check all services are running
systemctl is-active postgresql redis-server rabbitmq-server nginx yantrago-backend yantrago-gateway

# Health check
curl http://localhost:8080/actuator/health

# Check TCP gateway ports are listening
ss -tlnp | grep -E '5000|5001|5002'

# Check Nginx
curl http://localhost/api/v1/health

# Check RabbitMQ management
curl http://localhost:15672  # Login: yantrago

# Check Redis
redis-cli -a ChangeThisRedisPassword2026! PING

# Check PostgreSQL
sudo -u postgres psql -d yantrago -c "SELECT version();"
```

### 18.2 When to Scale from 1 Server to Multiple Servers

| Signal | Action |
|-------|--------|
| You have your first paying customer | Take a Vultr snapshot, then consider splitting DB to its own server |
| More than 50 devices connected | Split TCP gateway to its own server (devices need stable connections) |
| More than 200 users | Split backend API to its own server |
| Database CPU consistently > 70% | Upgrade DB server or move PostgreSQL to dedicated server |
| API response time > 500ms | Add a second API instance behind Vultr Load Balancer |
| More than 1,000 devices | Split Redis and RabbitMQ to their own servers |

#### How to Split (Without Downtime)

1. **Take a snapshot** of the single server (backup!)
2. **Create the new server** (e.g., dedicated DB server)
3. **Migrate PostgreSQL** to the new server:
   ```bash
   # On old server: dump database
   sudo -u postgres pg_dump yantrago > /tmp/yantrago_full.sql

   # Copy to new DB server
   scp /tmp/yantrago_full.sql root@NEW_DB_IP:/tmp/

   # On new DB server: restore
   sudo -u postgres psql -d yantrago < /tmp/yantrago_full.sql
   ```
4. **Update `application-prod.yml`** on the app server to point to `NEW_DB_IP`
5. **Restart** the backend service
6. **Verify** everything works, then decommission the old DB on the single server

### 18.3 Production Setup (5 Separate Servers)

For when you have paying customers and need reliability:

| Server | Specs | Base Cost | Backups | DDoS | Total |
|--------|-------|-----------|---------|------|-------|
| Database | 4GB, 2CPU, 80GB | $24 | $2 | $10 | $36 |
| Backend API | 2GB, 1CPU, 40GB | $12 | $2 | $10 | $24 |
| TCP Gateway | 2GB, 1CPU, 40GB | $12 | $2 | — | $14 |
| Redis | 1GB, 1CPU, 20GB | $6 | $1 | — | $7 |
| RabbitMQ | 1GB, 1CPU, 20GB | $6 | $1 | — | $7 |
| **Total** | | **$60** | **$8** | **$20** | **~$98/month** |

### 18.4 Cost-Optimized Setup (3 Servers — Early Customers)

When you have a few customers but don't need full separation yet:

| Server | Specs | What Runs On It | Monthly Cost |
|--------|-------|-----------------|-------------|
| Database | 4GB, 2CPU, 80GB | PostgreSQL + PostGIS | $36 |
| App + Gateway | 4GB, 2CPU, 60GB | Backend API + TCP Gateway + Nginx | $36 |
| Redis + RabbitMQ | 1GB, 1CPU, 20GB | Redis + RabbitMQ | $7 |
| **Total** | | | **~$79/month** |

### 18.5 Scaling Plan

| Phase | Users | Devices | Setup | Est. Cost |
|-------|-------|---------|-------|-----------|
| **Development** | 0-10 | 0-5 | **1 server** (all-in-one) | **~$63/mo** |
| First customers | 10-100 | 5-50 | 1 server (upgraded to 8GB) | ~$63/mo |
| Early growth | 100-500 | 50-200 | 3 servers (split DB + cache) | ~$79/mo |
| Growth | 500-2,000 | 200-1,000 | 5 servers (full separation) | ~$98/mo |
| Scale | 2,000-5,000 | 1,000-5,000 | Upgrade DB to 8GB; 2nd API instance + load balancer | ~$180/mo |
| Enterprise | 5,000-20,000 | 5,000-20,000 | Multiple API + gateway instances, managed DB | ~$400+/mo |

> **Key principle:** Start with 1 server for ~$63/month. Only add more servers when you have real traffic or paying customers that justify the cost. Vultr lets you upgrade server specs or add new servers in minutes — no need to over-provision early.

> **Note:** Vultr Load Balancer is available for $10-15/month when you need multiple API or gateway instances.

---

## Summary

This project structure is designed to:

1. **Reuse ~1,900 lines of proven GPS/TCP protocol code** from HarvestTracker by copying Java source files directly — no porting required
2. **Use Java 17 + Spring Boot for the entire backend** (API server + TCP gateway + shared library + simulator)
3. **Separate the TCP device gateway from the API server** for independent scaling and fault isolation
4. **Support multi-tenancy** with strict tenant isolation at the database and service layers
5. **Scale to 20,000+ users** with horizontal scaling, RabbitMQ decoupling, and database partitioning
6. **Provide a complete platform** with mobile app (Flutter), admin web (Next.js), and backend (Java/Spring Boot)
7. **Enable white-label deployment** through organization-level configuration
8. **Ensure production readiness** with monitoring, CI/CD, testing, and disaster recovery

The architecture follows the key principle: **the device communication layer is completely separated from the business/API layer**, connected only through RabbitMQ. This makes the system reliable, scalable, and maintainable as the number of connected YantraGO machines grows.