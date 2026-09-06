package com.yantrago.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * YantraGO Device Simulator — Spring Boot application entry point.
 *
 * Simulates Concox V5, JT808/T98, and YantraGO fencing devices for load testing
 * the TCP device gateway without real hardware.
 *
 * Scenarios:
 * - single-device: 1 device for manual testing
 * - hundred-devices: 100 devices for integration testing
 * - thousand-devices: 1,000 devices for load testing
 * - ten-thousand-devices: 10,000 devices for stress testing
 */
@SpringBootApplication
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }
}
