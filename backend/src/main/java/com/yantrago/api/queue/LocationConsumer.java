package com.yantrago.api.queue;

import com.yantrago.api.service.LocationService;
import com.yantrago.api.websocket.LocationBroadcastService;
import com.yantrago.shared.queue.LocationMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Consumes LocationMessage from the gateway and persists GPS data via LocationService.
 * - Upserts device_locations (current location)
 * - Adds to location_history buffer (batch insert via LocationPersistenceService)
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
@Component
public class LocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(LocationConsumer.class);

    private final LocationService locationService;
    private final LocationBroadcastService locationBroadcastService;

    public LocationConsumer(LocationService locationService,
                            LocationBroadcastService locationBroadcastService) {
        this.locationService = locationService;
        this.locationBroadcastService = locationBroadcastService;
    }

    @RabbitListener(queues = QueueNames.LOCATION_QUEUE)
    public void handleLocation(LocationMessage message) {
        log.debug("Received location: deviceId={} lat={} lon={} speed={} course={}",
                message.getDeviceId(), message.getLatitude(), message.getLongitude(),
                message.getSpeed(), message.getCourse());

        try {
            LocalDateTime recordedAt = message.getTimestamp() != null
                    ? LocalDateTime.ofInstant(message.getTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now();

            locationService.updateLocation(
                    message.getDeviceId(),
                    null, // machineId resolved by service from device binding
                    message.getLatitude(),
                    message.getLongitude(),
                    message.getSpeed(),
                    message.getCourse(),
                    recordedAt
            );

            log.debug("Persisted location for device={}", message.getDeviceId());

            // Broadcast to WebSocket subscribers via /topic/location/{machineId}
            // machineId is null for now (would be resolved from device binding in production)
            locationBroadcastService.broadcastLocation(
                    null, message.getDeviceId(),
                    message.getLatitude(), message.getLongitude(),
                    message.getSpeed(), message.getCourse(),
                    recordedAt
            );
        } catch (Exception e) {
            log.error("Failed to persist location for deviceId={}: {}",
                    message.getDeviceId(), e.getMessage(), e);
        }
    }
}
