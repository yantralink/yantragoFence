package com.yantrago.api.service;

import com.yantrago.api.repository.DeviceRepository;
import com.yantrago.api.repository.MachineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks device last-seen timestamps and updates online/offline state.
 * Called by the RabbitMQ consumer when telemetry/heartbeat messages arrive from the gateway.
 *
 * Updates:
 * - devices.last_seen_at
 * - device_states.online + last_seen_at
 * - machines.is_online + last_seen_at (if device is bound to a machine)
 */
@Service
public class DeviceHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(DeviceHeartbeatService.class);

    private final DeviceRepository deviceRepository;
    private final MachineRepository machineRepository;

    public DeviceHeartbeatService(DeviceRepository deviceRepository,
                                  MachineRepository machineRepository) {
        this.deviceRepository = deviceRepository;
        this.machineRepository = machineRepository;
    }

    /**
     * Updates the last-seen timestamp for a device and marks it online.
     * Also updates the bound machine's online status.
     *
     * @param deviceId the device that sent a heartbeat
     */
    @Transactional
    public void recordHeartbeat(UUID deviceId) {
        LocalDateTime now = LocalDateTime.now();

        // Targeted single-column update — a full-entity save() writes back
        // every column with the values loaded at findById time. When a
        // heartbeat event and its telemetry message are processed together,
        // that stale write reverts battery_pct/voltage/etc. (lost update).
        int updated = deviceRepository.updateLastSeenAt(deviceId, now);
        if (updated == 0) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }

        // Update bound machine's online status
        deviceRepository.findMachineIdById(deviceId).ifPresent(machineId -> {
            machineRepository.updateOnlineStatus(machineId, true, now);
            log.debug("Machine {} marked online via device heartbeat", machineId);
        });

        log.debug("Recorded heartbeat for device={} at {}", deviceId, now);
    }

    /**
     * Marks a device as offline (e.g. when heartbeat timeout is detected).
     * Also updates the bound machine's online status.
     *
     * @param deviceId the device that went offline
     */
    @Transactional
    public void markOffline(UUID deviceId) {
        LocalDateTime now = LocalDateTime.now();
        if (!deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }

        deviceRepository.findMachineIdById(deviceId).ifPresent(machineId -> {
            machineRepository.updateOnlineStatus(machineId, false, now);
            log.info("Machine {} marked offline (device {} timeout)", machineId, deviceId);
        });

        log.info("Device {} marked offline", deviceId);
    }
}
