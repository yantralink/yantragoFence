package com.yantrago.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SEC-10: Tests for DeviceAuthService admission control.
 *
 * Covers:
 * - Active device is authenticated
 * - Inactive device is rejected
 * - Unknown IMEI is rejected
 * - Null/blank IMEI is rejected
 * - DB failure returns null (fail-closed)
 */
class DeviceAuthServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DeviceAuthService deviceAuthService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        deviceAuthService = new DeviceAuthService(jdbcTemplate);
    }

    @Test
    @DisplayName("SEC-10: active device is authenticated")
    void authenticateByImei_activeDevice_returnsUuid() {
        UUID deviceId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT id FROM devices WHERE imei = ? AND is_active = true"),
                eq(UUID.class), eq("867010070113452")))
                .thenReturn(deviceId);

        UUID result = deviceAuthService.authenticateByImei("867010070113452");
        assertEquals(deviceId, result);
    }

    @Test
    @DisplayName("SEC-10: inactive device is rejected")
    void authenticateByImei_inactiveDevice_returnsNull() {
        // Simulate no row found (inactive device)
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), anyString()))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        UUID result = deviceAuthService.authenticateByImei("867010070113452");
        assertNull(result, "Inactive device must not be authenticated");
    }

    @Test
    @DisplayName("SEC-10: unknown IMEI is rejected")
    void authenticateByImei_unknownImei_returnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), anyString()))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        UUID result = deviceAuthService.authenticateByImei("999999999999999");
        assertNull(result, "Unknown IMEI must not be authenticated");
    }

    @Test
    @DisplayName("SEC-10: null IMEI is rejected")
    void authenticateByImei_nullImei_returnsNull() {
        UUID result = deviceAuthService.authenticateByImei(null);
        assertNull(result);
    }

    @Test
    @DisplayName("SEC-10: blank IMEI is rejected")
    void authenticateByImei_blankImei_returnsNull() {
        UUID result = deviceAuthService.authenticateByImei("  ");
        assertNull(result);
    }

    @Test
    @DisplayName("SEC-10: DB failure returns null (fail-closed)")
    void authenticateByImei_dbFailure_returnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), anyString()))
                .thenThrow(new RuntimeException("DB connection failed"));

        UUID result = deviceAuthService.authenticateByImei("867010070113452");
        assertNull(result, "DB failure must fail-closed (reject)");
    }

    @Test
    @DisplayName("SEC-10: active device authenticated by SIM phone")
    void authenticateBySimPhone_activeDevice_returnsUuid() {
        UUID deviceId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT id FROM devices WHERE sim_phone = ? AND is_active = true"),
                eq(UUID.class), eq("+919876543210")))
                .thenReturn(deviceId);

        UUID result = deviceAuthService.authenticateBySimPhone("+919876543210");
        assertEquals(deviceId, result);
    }

    @Test
    @DisplayName("SEC-10: null SIM phone is rejected")
    void authenticateBySimPhone_null_returnsNull() {
        UUID result = deviceAuthService.authenticateBySimPhone(null);
        assertNull(result);
    }
}
