package com.yantrago.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcasts GPS location updates to subscribers via STOMP WebSocket.
 *
 * Clients subscribe to /topic/location/{machineId} to receive live
 * location updates for a specific machine.
 *
 * Called by LocationConsumer when a LocationMessage is received from the gateway.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * This service bridges the RabbitMQ consumer to the WebSocket subscribers.
 */
@Service
public class LocationBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(LocationBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public LocationBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts a location update to /topic/location/{machineId}.
     *
     * @param machineId the machine whose location updated
     * @param deviceId the device that reported the location
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param speed speed in kph (optional)
     * @param course heading in degrees (optional)
     * @param recordedAt timestamp of the reading
     */
    public void broadcastLocation(UUID machineId, UUID deviceId,
                                   Double latitude, Double longitude,
                                   Double speed, Double course,
                                   LocalDateTime recordedAt) {
        if (machineId == null) {
            log.debug("Skipping location broadcast: machineId is null (device={})", deviceId);
            return;
        }

        String destination = "/topic/location/" + machineId;
        Map<String, Object> payload = Map.of(
                "machineId", machineId.toString(),
                "deviceId", deviceId != null ? deviceId.toString() : null,
                "latitude", latitude,
                "longitude", longitude,
                "speed", speed,
                "course", course,
                "recordedAt", recordedAt != null ? recordedAt.toString() : null,
                "timestamp", LocalDateTime.now().toString()
        );

        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Broadcasted location to {} lat={} lon={}", destination, latitude, longitude);
    }
}
