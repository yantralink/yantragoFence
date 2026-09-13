package com.yantrago.api.queue;

import com.yantrago.api.service.AutoGeofenceService;
import com.yantrago.api.service.DeviceResolverService;
import com.yantrago.api.service.GeofenceBreachService;
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
 * Per AGENTS.md rule 7: organization_id is resolved from the devices table, not from
 * the message payload or JWT (there is no HTTP context in a RabbitMQ consumer).
 * Per AGENTS.md rule 18: time-series tables use batch inserts.
 */
@Component
public class LocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(LocationConsumer.class);

    private final LocationService locationService;
    private final LocationBroadcastService locationBroadcastService;
    private final DeviceResolverService deviceResolverService;
    private final GeofenceBreachService geofenceBreachService;
    private final AutoGeofenceService autoGeofenceService;

    public LocationConsumer(LocationService locationService,
                            LocationBroadcastService locationBroadcastService,
                            DeviceResolverService deviceResolverService,
                            GeofenceBreachService geofenceBreachService,
                            AutoGeofenceService autoGeofenceService) {
        this.locationService = locationService;
        this.locationBroadcastService = locationBroadcastService;
        this.deviceResolverService = deviceResolverService;
        this.geofenceBreachService = geofenceBreachService;
        this.autoGeofenceService = autoGeofenceService;
    }

    @RabbitListener(queues = QueueNames.LOCATION_QUEUE)
    public void handleLocation(LocationMessage message) {
        log.debug("Received location: deviceId={} lat={} lon={} speed={} course={}",
                message.getDeviceId(), message.getLatitude(), message.getLongitude(),
                message.getSpeed(), message.getCourse());

        try {
            // Resolve device metadata (org_id, machine_id, imei) from devices table.
            // RabbitMQ consumers have no HTTP/JWT context, so we cannot use OwnerContextService.
            DeviceResolverService.DeviceInfo deviceInfo = deviceResolverService.resolve(message.getDeviceId());
            if (deviceInfo == null || deviceInfo.organizationId() == null) {
                log.warn("Cannot persist location: device not found or missing org_id for deviceId={}",
                        message.getDeviceId());
                return;
            }

            LocalDateTime recordedAt = message.getTimestamp() != null
                    ? LocalDateTime.ofInstant(message.getTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now();

            locationService.updateLocation(
                    message.getDeviceId(),
                    deviceInfo.organizationId(),
                    deviceInfo.machineId(),
                    deviceInfo.imei(),
                    message.getLatitude(),
                    message.getLongitude(),
                    message.getSpeed(),
                    message.getCourse(),
                    recordedAt
            );

            log.debug("Persisted location for device={}", message.getDeviceId());

            // Broadcast to WebSocket subscribers via /topic/location/{machineId}
            locationBroadcastService.broadcastLocation(
                    deviceInfo.machineId(), message.getDeviceId(),
                    message.getLatitude(), message.getLongitude(),
                    message.getSpeed(), message.getCourse(),
                    recordedAt
            );

            // Auto-create geofence if missing (Phase 10.5, opt-in via config)
            if (deviceInfo.machineId() != null) {
                try {
                    autoGeofenceService.autoCreateIfMissing(
                            deviceInfo.organizationId(),
                            deviceInfo.machineId(),
                            message.getLatitude(),
                            message.getLongitude()
                    );
                } catch (Exception ae) {
                    log.warn("Auto-geofence creation failed for machine={} (location still persisted): {}",
                            deviceInfo.machineId(), ae.getMessage());
                }
            }

            // Check geo-fence breach after location update (Phase 10)
            if (deviceInfo.machineId() != null) {
                try {
                    geofenceBreachService.checkBreach(
                            deviceInfo.organizationId(),
                            deviceInfo.machineId(),
                            message.getLatitude(),
                            message.getLongitude()
                    );
                } catch (Exception ge) {
                    log.warn("Geo-fence breach check failed for machine={} (location still persisted): {}",
                            deviceInfo.machineId(), ge.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to persist location for deviceId={}: {}",
                    message.getDeviceId(), e.getMessage(), e);
        }
    }
}
