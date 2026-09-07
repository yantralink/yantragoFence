package com.yantrago.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcasts telemetry updates to subscribers via STOMP WebSocket.
 *
 * Clients subscribe to /topic/telemetry/{machineId} to receive live
 * voltage, battery, and GSM signal updates.
 *
 * Called by TelemetryConsumer when a TelemetryMessage is received from the gateway.
 */
@Service
public class TelemetryBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public TelemetryBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts a telemetry update to /topic/telemetry/{machineId}.
     *
     * @param machineId the machine whose telemetry updated
     * @param deviceId the device that reported the telemetry
     * @param voltage voltage reading (optional)
     * @param battery battery percentage (optional)
     * @param gsmSignal GSM signal strength (optional)
     */
    public void broadcastTelemetry(UUID machineId, UUID deviceId,
                                    Double voltage, Double battery, Integer gsmSignal) {
        if (machineId == null) {
            log.debug("Skipping telemetry broadcast: machineId is null (device={})", deviceId);
            return;
        }

        String destination = "/topic/telemetry/" + machineId;
        // Use HashMap because Map.of() does not allow null values, and
        // voltage/battery/gsmSignal may be null.
        Map<String, Object> payload = new HashMap<>();
        payload.put("machineId", machineId.toString());
        payload.put("deviceId", deviceId != null ? deviceId.toString() : null);
        payload.put("voltage", voltage);
        payload.put("battery", battery);
        payload.put("gsmSignal", gsmSignal);
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Broadcasted telemetry to {} voltage={} battery={}", destination, voltage, battery);
    }

    /**
     * Broadcasts a device online/offline event to /topic/device/{deviceId}.
     *
     * @param deviceId the device whose state changed
     * @param eventType LOGIN, HEARTBEAT, or DISCONNECT
     * @param isOnline true if the device is now online
     */
    public void broadcastDeviceEvent(UUID deviceId, String eventType, boolean isOnline) {
        if (deviceId == null) {
            return;
        }

        String destination = "/topic/device/" + deviceId;
        Map<String, Object> payload = Map.of(
                "deviceId", deviceId.toString(),
                "eventType", eventType,
                "isOnline", isOnline,
                "timestamp", LocalDateTime.now().toString()
        );

        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Broadcasted device event to {} type={} isOnline={}", destination, eventType, isOnline);
    }
}
