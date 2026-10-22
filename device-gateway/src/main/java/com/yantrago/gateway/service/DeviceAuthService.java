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
 * This service verifies that the IMEI exists in the devices table AND that the
 * device is active, then returns the device UUID for subsequent
 * telemetry/location/command routing.
 *
 * SEC-10: IMEI existence alone is not authentication. Only active, registered
 * devices may establish routable sessions. IMEI allowlisting is necessary but
 * is not cryptographic authentication — establish vendor-supported
 * authenticated transport (TLS, private APN/VPN) as a compensating control.
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
     * SEC-10: Verifies that the IMEI exists AND the device is active.
     * Inactive or unknown devices are rejected — they cannot establish
     * routable sessions.
     *
     * @param imei the IMEI from the device login packet
     * @return the device UUID, or null if the IMEI is not registered or inactive
     */
    public UUID authenticateByImei(String imei) {
        if (imei == null || imei.isBlank()) {
            log.warn("SEC-10: Authentication failed: IMEI is null or blank");
            return null;
        }

        try {
            // SEC-10: Check both existence AND active status.
            String sql = "SELECT id FROM devices WHERE imei = ? AND is_active = true";
            return jdbcTemplate.queryForObject(sql, UUID.class, imei);
        } catch (Exception e) {
            log.warn("SEC-10: Authentication failed for IMEI={}: device not found or inactive", imei);
            return null;
        }
    }

    /**
     * Authenticates a device by SIM phone number (JT808 dashcams).
     *
     * SEC-10: Verifies that the SIM phone exists AND the device is active.
     *
     * @param simPhone the SIM phone number from the JT808 registration packet
     * @return the device UUID, or null if not registered or inactive
     */
    public UUID authenticateBySimPhone(String simPhone) {
        if (simPhone == null || simPhone.isBlank()) {
            return null;
        }

        try {
            // SEC-10: Check both existence AND active status.
            String sql = "SELECT id FROM devices WHERE sim_phone = ? AND is_active = true";
            return jdbcTemplate.queryForObject(sql, UUID.class, simPhone);
        } catch (Exception e) {
            log.warn("SEC-10: Authentication failed for simPhone={}: device not found or inactive", simPhone);
            return null;
        }
    }
}
