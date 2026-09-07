package com.yantrago.api.service;

import com.yantrago.api.model.Device;
import com.yantrago.api.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves device metadata (organization_id, machine_id, imei) from the devices
 * table by device_id. Used by RabbitMQ consumers that have no HTTP/JWT tenant
 * context and therefore cannot use OwnerContextService.
 *
 * Results are cached in-memory with a TTL to avoid repeated DB lookups for
 * high-volume location and telemetry messages from the same device.
 *
 * Per AGENTS.md rule 7: organization_id is resolved from the device record,
 * never from the message payload.
 */
@Service
public class DeviceResolverService {

    private static final Logger log = LoggerFactory.getLogger(DeviceResolverService.class);
    private static final long CACHE_TTL_MILLIS = 60_000L; // 1 minute

    private final DeviceRepository deviceRepository;
    private final ConcurrentHashMap<UUID, CachedDevice> cache = new ConcurrentHashMap<>();

    public DeviceResolverService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * Resolves device metadata by device_id.
     *
     * @param deviceId the device UUID
     * @return DeviceInfo with org_id, machine_id, imei; or null if device not found
     */
    public DeviceInfo resolve(UUID deviceId) {
        if (deviceId == null) {
            return null;
        }

        CachedDevice cached = cache.get(deviceId);
        if (cached != null && !cached.isExpired()) {
            return cached.info;
        }

        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            log.warn("Device not found for deviceId={}", deviceId);
            return null;
        }

        Device device = deviceOpt.get();
        DeviceInfo info = new DeviceInfo(
                device.getOrganizationId(),
                device.getMachineId(),
                device.getImei()
        );

        cache.put(deviceId, new CachedDevice(info, System.currentTimeMillis()));
        log.debug("Resolved deviceId={} -> orgId={}, machineId={}, imei={}",
                deviceId, info.organizationId(), info.machineId(), info.imei());
        return info;
    }

    /**
     * Evicts a device from the cache (e.g. when device binding changes).
     */
    public void evict(UUID deviceId) {
        cache.remove(deviceId);
    }

    // ===== Inner types =====

    public record DeviceInfo(UUID organizationId, UUID machineId, String imei) {}

    private record CachedDevice(DeviceInfo info, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MILLIS;
        }
    }
}
