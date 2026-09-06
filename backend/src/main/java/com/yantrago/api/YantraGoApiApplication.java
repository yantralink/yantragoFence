package com.yantrago.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the YantraGO Backend API.
 *
 * Handles authentication, user management, machine management, command lifecycle,
 * telemetry storage, alerts, notifications, reporting, and WebSocket real-time push.
 */
@SpringBootApplication
public class YantraGoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YantraGoApiApplication.class, args);
    }
}
