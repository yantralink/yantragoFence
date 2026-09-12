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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
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

    // Firebase Admin SDK for FCM push delivery (Phase 5)
    implementation("com.google.firebase:firebase-admin:9.4.3")

    // Shared library
    implementation(project(":shared"))

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
    testImplementation("org.mockito:mockito-inline:5.2.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Sync Flyway migrations from database/migrations/ into the classpath.
// The source of truth is database/migrations/ — do not edit the copies directly.
val syncMigrations by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("database/migrations"))
    into(layout.projectDirectory.dir("src/main/resources/db/migration"))
}

tasks.named("processResources") {
    dependsOn(syncMigrations)
}
