package com.yantrago.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Device heartbeat service — tracks device heartbeats in Redis with TTL.
 *
 * Each heartbeat updates a Redis key with the current timestamp and a TTL.
 * If the key expires, the device is considered offline.
 *
 * Adapted from the HarvestTracker pattern but uses Redis instead of in-memory maps
 * for multi-instance gateway deployments.
 */
@Service
public class DeviceHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(DeviceHeartbeatService.class);

    private static final String HEARTBEAT_KEY_PREFIX = "gateway:heartbeat:";
    private static final Duration HEARTBEAT_TTL = Duration.ofMinutes(3);

    private final StringRedisTemplate redisTemplate;

    public DeviceHeartbeatService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Records a heartbeat for a device.
     *
     * @param imei the device IMEI
     */
    public void recordHeartbeat(String imei) {
        if (imei == null || imei.isBlank()) {
            return;
        }
        String key = HEARTBEAT_KEY_PREFIX + imei;
        redisTemplate.opsForValue().set(key, Instant.now().toString(),
                HEARTBEAT_TTL.toSeconds(), TimeUnit.SECONDS);
        log.debug("Recorded heartbeat for imei={}", imei);
    }

    /**
     * Marks a device as offline by deleting its heartbeat key.
     *
     * @param imei the device IMEI
     */
    public void markOffline(String imei) {
        if (imei == null || imei.isBlank()) {
            return;
        }
        String key = HEARTBEAT_KEY_PREFIX + imei;
        redisTemplate.delete(key);
        log.info("Marked device offline: imei={}", imei);
    }

    /**
     * Checks if a device is online (has an active heartbeat).
     *
     * @param imei the device IMEI
     * @return true if the device has a recent heartbeat
     */
    public boolean isOnline(String imei) {
        if (imei == null || imei.isBlank()) {
            return false;
        }
        String key = HEARTBEAT_KEY_PREFIX + imei;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
