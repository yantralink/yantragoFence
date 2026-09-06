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

@Component
public class DashcamConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(DashcamConnectionRegistry.class);

    private final Map<String, OutputStream> simConnections = new ConcurrentHashMap<>();
    private final Map<String, String> clientIdToSim = new ConcurrentHashMap<>();
    private final DashcamRepository dashcamRepository;

    public DashcamConnectionRegistry(DashcamRepository dashcamRepository) {
        this.dashcamRepository = dashcamRepository;
    }

    public void registerConnection(String clientId, String simPhone, OutputStream out) {
        simConnections.put(simPhone, out);
        clientIdToSim.put(clientId, simPhone);
        log.info("[JT808-Registry] Registered: clientId={}, sim={}", clientId, simPhone);
    }

    public void unregisterConnection(String clientId) {
        String sim = clientIdToSim.remove(clientId);
        if (sim != null) {
            simConnections.remove(sim);
            log.info("[JT808-Registry] Unregistered: clientId={}, sim={}", clientId, sim);
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
