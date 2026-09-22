package com.yantrago.gateway.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceConnectionRegistry.class);

    private final Map<String, OutputStream> imeiConnections = new ConcurrentHashMap<>();
    private final Map<String, String> clientIdToImei = new ConcurrentHashMap<>();
    private final Map<String, String> imeiToProtocol = new ConcurrentHashMap<>();

    public void registerConnection(String clientId, String imei, OutputStream out) {
        registerConnection(clientId, imei, out, null);
    }

    public void registerConnection(String clientId, String imei, OutputStream out, String protocol) {
        imeiConnections.put(imei, out);
        clientIdToImei.put(clientId, imei);
        if (protocol != null) {
            imeiToProtocol.put(imei, protocol);
        }
        log.info("[Registry] Registered connection: clientId={}, imei={}, protocol={}", clientId, imei, protocol);
    }

    public void unregisterConnection(String clientId) {
        String imei = clientIdToImei.remove(clientId);
        if (imei != null) {
            imeiConnections.remove(imei);
            imeiToProtocol.remove(imei);
            log.info("[Registry] Unregistered connection: clientId={}, imei={}", clientId, imei);
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
