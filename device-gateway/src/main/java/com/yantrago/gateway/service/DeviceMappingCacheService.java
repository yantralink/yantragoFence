package com.yantrago.gateway.service;

/**
 * Device mapping cache service — resolves IMEI to device ID using Redis-cached lookups.
 *
 * This is the gateway-side interface used by ConcoxV5ProtocolHandler.
 * Phase 12 will implement this with Redis caching backed by PostgreSQL device table lookups.
 */
public interface DeviceMappingCacheService {

    /**
     * Resolves a device ID from an IMEI number.
     *
     * @param imei the IMEI number from the device login packet
     * @return the device ID string, or null if no mapping exists
     */
    String getDeviceIdByImei(String imei);
}
