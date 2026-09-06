package com.yantrago.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-cached IMEI → device ID mapping service.
 *
 * On first lookup, queries the database for the device ID by IMEI and caches
 * the result in Redis with a TTL. Subsequent lookups hit the cache.
 *
 * Implements the DeviceMappingCacheService interface used by ConcoxV5ProtocolHandler.
 *
 * Per AGENTS.md rule 17: shared module defines contracts.
 */
@Service
public class DeviceMappingCacheServiceImpl implements DeviceMappingCacheService {

    private static final Logger log = LoggerFactory.getLogger(DeviceMappingCacheServiceImpl.class);

    private static final String CACHE_KEY_PREFIX = "gateway:device-mapping:";
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public DeviceMappingCacheServiceImpl(StringRedisTemplate redisTemplate,
                                          JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getDeviceIdByImei(String imei) {
        if (imei == null || imei.isBlank()) {
            return null;
        }

        String cacheKey = CACHE_KEY_PREFIX + imei;

        // Try cache first
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("NULL".equals(cached)) {
                // Negative cache — device not found
                return null;
            }
            log.debug("Cache hit for imei={} -> deviceId={}", imei, cached);
            return cached;
        }

        // Cache miss — query database
        try {
            String sql = "SELECT id::text FROM devices WHERE imei = ?";
            String deviceId = jdbcTemplate.queryForObject(sql, String.class, imei);

            if (deviceId != null) {
                redisTemplate.opsForValue().set(cacheKey, deviceId, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                log.info("Resolved imei={} -> deviceId={} (cached)", imei, deviceId);
                return deviceId;
            }
        } catch (Exception e) {
            log.warn("Device not found for imei={}: {}", imei, e.getMessage());
        }

        // Negative cache to avoid repeated DB lookups for unknown IMEIs
        redisTemplate.opsForValue().set(cacheKey, "NULL", 60, TimeUnit.SECONDS);
        return null;
    }
}
