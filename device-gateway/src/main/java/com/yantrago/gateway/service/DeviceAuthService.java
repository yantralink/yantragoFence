package com.yantrago.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Device authentication service — authenticates devices on TCP connect using IMEI lookup.
 *
 * On login, the protocol handler extracts the IMEI from the device's login packet.
 * This service verifies that the IMEI exists in the devices table and returns the
 * device UUID for subsequent telemetry/location/command routing.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 */
@Service
public class DeviceAuthService {

    private static final Logger log = LoggerFactory.getLogger(DeviceAuthService.class);

    private final JdbcTemplate jdbcTemplate;

    public DeviceAuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Authenticates a device by IMEI and returns the device UUID.
     *
     * @param imei the IMEI from the device login packet
     * @return the device UUID, or null if the IMEI is not registered
     */
    public UUID authenticateByImei(String imei) {
        if (imei == null || imei.isBlank()) {
            log.warn("Authentication failed: IMEI is null or blank");
            return null;
        }

        try {
            String sql = "SELECT id FROM devices WHERE imei = ?";
            return jdbcTemplate.queryForObject(sql, UUID.class, imei);
        } catch (Exception e) {
            log.warn("Authentication failed for IMEI={}: {}", imei, e.getMessage());
            return null;
        }
    }

    /**
     * Authenticates a device by SIM phone number (JT808 dashcams).
     *
     * @param simPhone the SIM phone number from the JT808 registration packet
     * @return the device UUID, or null if not registered
     */
    public UUID authenticateBySimPhone(String simPhone) {
        if (simPhone == null || simPhone.isBlank()) {
            return null;
        }

        try {
            String sql = "SELECT id FROM devices WHERE sim_phone = ?";
            return jdbcTemplate.queryForObject(sql, UUID.class, simPhone);
        } catch (Exception e) {
            log.warn("Authentication failed for simPhone={}: {}", simPhone, e.getMessage());
            return null;
        }
    }
}
