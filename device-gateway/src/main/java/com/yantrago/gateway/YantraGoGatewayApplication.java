package com.yantrago.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * YantraGO TCP Device Gateway — Spring Boot application entry point.
 *
 * This service:
 * - Listens for TCP connections from Concox V5, JT808, and fencing devices
 * - Parses device protocol packets (reused from HarvestTracker)
 * - Forwards telemetry, location, and device events to the backend via RabbitMQ
 * - Receives machine commands from the backend via RabbitMQ and dispatches them to devices
 *
 * Per AGENTS.md rule 11: this is one of exactly two services (backend API + TCP gateway).
 */
@SpringBootApplication
public class YantraGoGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(YantraGoGatewayApplication.class, args);
    }
}
