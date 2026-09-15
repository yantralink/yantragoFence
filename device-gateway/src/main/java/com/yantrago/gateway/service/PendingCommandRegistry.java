package com.yantrago.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending commands by IMEI so that device ACKs can be correlated
 * back to the original commandId.
 *
 * When a command is dispatched to a device, the (imei -> commandId) mapping
 * is registered here. When the device replies (ACK/FAILED), the CommandResultService
 * looks up the commandId by IMEI and publishes the result to the backend.
 *
 * Only one command can be pending per IMEI at a time. If a new command is
 * dispatched while one is already pending, the old one is overwritten (the
 * backend's timeout mechanism will handle the orphaned command).
 */
@Component
public class PendingCommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandRegistry.class);

    private final ConcurrentHashMap<String, UUID> pendingByImei = new ConcurrentHashMap<>();

    /**
     * Registers a pending command for the given IMEI.
     *
     * @param imei the device IMEI
     * @param commandId the command ID being dispatched
     */
    public void register(String imei, UUID commandId) {
        UUID previous = pendingByImei.put(imei, commandId);
        if (previous != null) {
            log.warn("Overwrote pending command for imei={}: old={} new={}", imei, previous, commandId);
        }
        log.debug("Registered pending command: imei={} commandId={}", imei, commandId);
    }

    /**
     * Removes and returns the pending commandId for the given IMEI.
     *
     * @param imei the device IMEI
     * @return the commandId, or null if no pending command exists
     */
    public UUID remove(String imei) {
        UUID commandId = pendingByImei.remove(imei);
        if (commandId != null) {
            log.debug("Removed pending command: imei={} commandId={}", imei, commandId);
        }
        return commandId;
    }

    /**
     * Returns the pending commandId for the given IMEI without removing it.
     *
     * @param imei the device IMEI
     * @return the commandId, or null if no pending command exists
     */
    public UUID get(String imei) {
        return pendingByImei.get(imei);
    }
}
