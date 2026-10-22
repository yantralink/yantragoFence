package com.yantrago.gateway.tcp;

import com.yantrago.gateway.model.Dashcam;
import com.yantrago.gateway.repository.DashcamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps SIM phone → active OutputStream for JT808 dashcam command dispatch.
 *
 * SEC-10: Connection replacement and cleanup verify current ownership.
 * A new connection for a SIM phone replaces the old one only if the old
 * connection is different (legitimate reconnect). An old disconnect
 * cannot remove a newer connection's mapping — it checks that the
 * disconnecting clientId still owns the SIM phone before removing.
 */
@Component
public class DashcamConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(DashcamConnectionRegistry.class);

    private final Map<String, OutputStream> simConnections = new ConcurrentHashMap<>();
    private final Map<String, String> clientIdToSim = new ConcurrentHashMap<>();
    // SEC-10: Track which clientId owns each SIM phone so stale disconnects
    // cannot remove a newer connection's mapping.
    private final Map<String, String> simToClientId = new ConcurrentHashMap<>();
    private final DashcamRepository dashcamRepository;

    public DashcamConnectionRegistry(DashcamRepository dashcamRepository) {
        this.dashcamRepository = dashcamRepository;
    }

    /**
     * SEC-10: Registers a connection for a SIM phone.
     *
     * If a previous connection exists for the same SIM from a different
     * client, it is replaced — this is a legitimate reconnect. The old
     * connection's clientId is overwritten so the old disconnect cannot
     * remove the new mapping.
     */
    public void registerConnection(String clientId, String simPhone, OutputStream out) {
        String previousClientId = simToClientId.get(simPhone);
        if (previousClientId != null && !previousClientId.equals(clientId)) {
            log.info("SEC-10: Replacing connection for sim={}: oldClientId={} -> newClientId={}",
                    simPhone, previousClientId, clientId);
            clientIdToSim.remove(previousClientId);
        }
        simConnections.put(simPhone, out);
        clientIdToSim.put(clientId, simPhone);
        simToClientId.put(simPhone, clientId);
        log.info("[JT808-Registry] Registered: clientId={}, sim={}", clientId, simPhone);
    }

    /**
     * SEC-10: Unregisters a connection.
     *
     * Only removes the SIM mapping if the disconnecting clientId still
     * owns the SIM phone. A stale disconnect from an old connection cannot
     * remove the newer connection's mapping.
     */
    public void unregisterConnection(String clientId) {
        String sim = clientIdToSim.remove(clientId);
        if (sim != null) {
            String currentOwner = simToClientId.get(sim);
            if (clientId.equals(currentOwner)) {
                simConnections.remove(sim);
                simToClientId.remove(sim);
                log.info("[JT808-Registry] Unregistered: clientId={}, sim={}", clientId, sim);
            } else {
                log.info("SEC-10: Stale disconnect ignored: clientId={}, sim={}, currentOwner={}",
                        clientId, sim, currentOwner);
            }
        }
    }

    public boolean isDeviceOnline(String simPhone) {
        return simConnections.containsKey(simPhone);
    }

    public boolean sendCommand(String simPhone, byte[] commandPacket) {
        OutputStream out = simConnections.get(simPhone);
        if (out == null) {
            log.warn("[JT808-Registry] Device not connected: sim={}", simPhone);
            return false;
        }
        try {
            out.write(commandPacket);
            out.flush();
            log.info("[JT808-Registry] Command sent to sim={}, {} bytes", simPhone, commandPacket.length);
            return true;
        } catch (Exception e) {
            log.error("[JT808-Registry] Failed to send command to sim={}: {}", simPhone, e.getMessage());
            simConnections.remove(simPhone);
            return false;
        }
    }

    public Set<String> getOnlineSims() {
        return simConnections.keySet();
    }

    public void updateDashcamStatus(String simPhone, Dashcam.DashcamStatus status) {
        dashcamRepository.findBySimPhone(simPhone).ifPresent(d -> {
            dashcamRepository.updateStatus(d.getId(), status);
            log.info("[JT808-Registry] Status update: sim={} -> {}", simPhone, status);
        });
    }
}
