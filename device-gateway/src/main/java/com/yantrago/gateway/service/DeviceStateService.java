package com.yantrago.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Device state service — maintains last-known device state in Redis.
 *
 * Stores per-device state including:
 * - online/offline status
 * - last known position (lat, lng)
 * - last heartbeat timestamp
 * - voltage, battery, GSM signal
 * - relay/fuel-cut state
 *
 * This provides fast lookups for the gateway without hitting the database.
 */
@Service
public class DeviceStateService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateService.class);

    private static final String STATE_KEY_PREFIX = "gateway:device-state:";
    private static final long STATE_TTL_SECONDS = 600; // 10 minutes

    private final StringRedisTemplate redisTemplate;

    public DeviceStateService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Updates the last-known position for a device.
     */
    public void updatePosition(String imei, double latitude, double longitude, double speed, double course) {
        String key = STATE_KEY_PREFIX + imei;
        String value = String.format("lat=%.6f,lng=%.6f,speed=%.1f,course=%.1f,ts=%s",
                latitude, longitude, speed, course, Instant.now().toString());
        redisTemplate.opsForValue().set(key, value, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Updated position for imei={}: {}", imei, value);
    }

    /**
     * Updates telemetry state for a device.
     */
    public void updateTelemetry(String imei, Double voltage, Double battery, Integer gsmSignal) {
        String key = STATE_KEY_PREFIX + imei + ":telemetry";
        String value = String.format("voltage=%s,battery=%s,gsm=%s,ts=%s",
                voltage, battery, gsmSignal, Instant.now().toString());
        redisTemplate.opsForValue().set(key, value, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Updated telemetry for imei={}: {}", imei, value);
    }

    /**
     * Updates the relay/fuel-cut state for a device.
     */
    public void updateRelayState(String imei, boolean relayOn) {
        String key = STATE_KEY_PREFIX + imei + ":relay";
        String value = String.format("relayOn=%s,ts=%s", relayOn, Instant.now().toString());
        redisTemplate.opsForValue().set(key, value, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Updated relay state for imei={}: relayOn={}", imei, relayOn);
    }

    /**
     * Gets the last-known state for a device.
     */
    public String getDeviceState(String imei) {
        String key = STATE_KEY_PREFIX + imei;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Clears the state for a device (on disconnect).
     */
    public void clearState(String imei) {
        redisTemplate.delete(STATE_KEY_PREFIX + imei);
        redisTemplate.delete(STATE_KEY_PREFIX + imei + ":telemetry");
        redisTemplate.delete(STATE_KEY_PREFIX + imei + ":relay");
        log.debug("Cleared state for imei={}", imei);
    }
}
