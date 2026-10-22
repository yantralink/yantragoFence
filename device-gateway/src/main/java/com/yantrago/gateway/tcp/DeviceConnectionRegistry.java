package com.yantrago.gateway.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps IMEI → active OutputStream for command dispatch.
 *
 * SEC-10: Connection replacement and cleanup verify current ownership.
 * A new connection for an IMEI replaces the old one only if the old
 * connection is different (legitimate reconnect). An old disconnect
 * cannot remove a newer connection's mapping — it checks that the
 * disconnecting clientId still owns the IMEI before removing.
 */
@Component
public class DeviceConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceConnectionRegistry.class);

    private final Map<String, OutputStream> imeiConnections = new ConcurrentHashMap<>();
    private final Map<String, String> clientIdToImei = new ConcurrentHashMap<>();
    private final Map<String, String> imeiToProtocol = new ConcurrentHashMap<>();
    // SEC-10: Track which clientId owns each IMEI so stale disconnects
    // cannot remove a newer connection's mapping.
    private final Map<String, String> imeiToClientId = new ConcurrentHashMap<>();

    public void registerConnection(String clientId, String imei, OutputStream out) {
        registerConnection(clientId, imei, out, null);
    }

    /**
     * SEC-10: Registers a connection for an IMEI.
     *
     * If a previous connection exists for the same IMEI from a different
     * client, it is replaced — this is a legitimate reconnect. The old
     * connection's clientId is overwritten so the old disconnect cannot
     * remove the new mapping.
     */
    public void registerConnection(String clientId, String imei, OutputStream out, String protocol) {
        String previousClientId = imeiToClientId.get(imei);
        if (previousClientId != null && !previousClientId.equals(clientId)) {
            log.info("SEC-10: Replacing connection for imei={}: oldClientId={} -> newClientId={}",
                    imei, previousClientId, clientId);
            // Clean up the old clientId's mapping so its disconnect
            // handler won't try to unregister the IMEI it no longer owns.
            clientIdToImei.remove(previousClientId);
        }
        imeiConnections.put(imei, out);
        clientIdToImei.put(clientId, imei);
        imeiToClientId.put(imei, clientId);
        if (protocol != null) {
            imeiToProtocol.put(imei, protocol);
        }
        log.info("[Registry] Registered connection: clientId={}, imei={}, protocol={}", clientId, imei, protocol);
    }

    /**
     * SEC-10: Unregisters a connection.
     *
     * Only removes the IMEI mapping if the disconnecting clientId still
     * owns the IMEI. A stale disconnect from an old connection cannot
     * remove the newer connection's mapping.
     */
    public void unregisterConnection(String clientId) {
        String imei = clientIdToImei.remove(clientId);
        if (imei != null) {
            // SEC-10: Check that this clientId still owns the IMEI.
            // If a newer connection has replaced it, the ownership has
            // transferred and this stale disconnect must not remove
            // the new mapping.
            String currentOwner = imeiToClientId.get(imei);
            if (clientId.equals(currentOwner)) {
                imeiConnections.remove(imei);
                imeiToProtocol.remove(imei);
                imeiToClientId.remove(imei);
                log.info("[Registry] Unregistered connection: clientId={}, imei={}", clientId, imei);
            } else {
                log.info("SEC-10: Stale disconnect ignored: clientId={}, imei={}, currentOwner={}",
                        clientId, imei, currentOwner);
            }
        }
    }

    public boolean isDeviceOnline(String imei) {
        return imeiConnections.containsKey(imei);
    }

    /**
     * Returns the protocol name for a device (e.g. "YANTRAGO_FENCING", "CONCOX_V5").
     * Returns null if unknown.
     */
    public String getProtocolForImei(String imei) {
        return imeiToProtocol.get(imei);
    }

    public boolean sendCommand(String imei, byte[] commandPacket) {
        OutputStream out = imeiConnections.get(imei);
        if (out == null) {
            log.warn("[Registry] Device not connected: imei={}", imei);
            return false;
        }
        try {
            out.write(commandPacket);
            out.flush();
            log.info("[Registry] Command sent to imei={}, {} bytes", imei, commandPacket.length);
            return true;
        } catch (Exception e) {
            log.error("[Registry] Failed to send command to imei={}: {}", imei, e.getMessage());
            imeiConnections.remove(imei);
            return false;
        }
    }

    public Set<String> getOnlineImeis() {
        return imeiConnections.keySet();
    }
}
