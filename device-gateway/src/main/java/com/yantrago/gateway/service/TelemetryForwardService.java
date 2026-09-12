package com.yantrago.gateway.service;

import com.yantrago.gateway.model.GpsIngestRequest;
import com.yantrago.gateway.queue.DeviceEventProducer;
import com.yantrago.gateway.queue.TelemetryProducer;
import com.yantrago.shared.queue.LocationMessage;
import com.yantrago.shared.queue.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Telemetry forward service — receives parsed GPS/telemetry data from protocol
 * handlers and forwards it to the backend via RabbitMQ.
 *
 * Implements the GpsIngestService interface used by ConcoxV5ProtocolHandler.
 * Converts GpsIngestRequest into TelemetryMessage and LocationMessage and
 * publishes them via the respective producers.
 *
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 17: use shared message contracts.
 */
@Service
public class TelemetryForwardService implements GpsIngestService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryForwardService.class);

    private final TelemetryProducer telemetryProducer;
    private final DeviceEventProducer deviceEventProducer;
    private final DeviceMappingCacheService deviceMappingCacheService;

    public TelemetryForwardService(TelemetryProducer telemetryProducer,
                                    DeviceEventProducer deviceEventProducer,
                                    DeviceMappingCacheService deviceMappingCacheService) {
        this.telemetryProducer = telemetryProducer;
        this.deviceEventProducer = deviceEventProducer;
        this.deviceMappingCacheService = deviceMappingCacheService;
    }

    @Override
    public void ingest(GpsIngestRequest request) {
        if (request == null || request.getDeviceId() == null) {
            log.warn("Skipping telemetry forward: request or deviceId is null");
            return;
        }

        UUID deviceId;
        try {
            deviceId = UUID.fromString(request.getDeviceId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid deviceId format: {}", request.getDeviceId());
            return;
        }

        Instant timestamp = request.getSentAt() != null
                ? Instant.ofEpochMilli(request.getSentAt())
                : Instant.now();

        // Forward telemetry (voltage, battery, GSM signal, charging)
        // Per BR05 protocol: battery percentage comes from the voltage level byte
        // in heartbeat/alarm packets, not from the GPS packet. The GPS packet (0x22)
        // does not include battery data. We forward whatever the protocol handler
        // extracted; if battery is null (no heartbeat received yet), we do not fake it.
        Double batteryValue = request.getBatteryLevel() != null
                ? request.getBatteryLevel().doubleValue() : null;
        TelemetryMessage telemetryMessage = new TelemetryMessage(
                deviceId,
                null, // IMEI not in GpsIngestRequest; would be resolved from device mapping
                null, // voltage in volts — BR05 does not provide this, only a 7-level enum
                batteryValue,
                request.getGsmSignalStrength() != null
                        ? request.getGsmSignalStrength() : null,
                request.getExternalPowerConnected(), // charging status from Terminal Info Bit2
                timestamp
        );
        telemetryProducer.publishTelemetry(telemetryMessage);

        // Forward location
        if (request.getLat() != null && request.getLng() != null) {
            LocationMessage locationMessage = new LocationMessage(
                    deviceId,
                    null, // IMEI
                    request.getLat(),
                    request.getLng(),
                    request.getSpeedKph(),
                    request.getHeading(),
                    timestamp
            );
            deviceEventProducer.publishLocation(locationMessage);
        }

        log.debug("Forwarded telemetry+location for deviceId={}", deviceId);
    }

    /**
     * Forwards a telemetry reading directly (used by fencing protocol handler).
     */
    public void forwardTelemetry(UUID deviceId, String imei,
                                  Double voltage, Double battery, Integer gsmSignal) {
        TelemetryMessage message = new TelemetryMessage(
                deviceId, imei, voltage, battery, gsmSignal, Instant.now()
        );
        telemetryProducer.publishTelemetry(message);
    }

    /**
     * Forwards a telemetry-only reading (battery, GSM, charging) extracted from
     * heartbeat or alarm packets. Implements GpsIngestService.forwardTelemetry.
     *
     * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
     * Per AGENTS.md rule 17: use shared message contracts (TelemetryMessage).
     */
    @Override
    public void forwardTelemetry(UUID deviceId, String imei,
                                  Double batteryPct, Integer gsmSignal, Boolean charging) {
        if (deviceId == null) {
            log.warn("Skipping telemetry forward: deviceId is null");
            return;
        }
        // BR05 does not provide voltage in volts — only a 7-level enum mapped to percentage.
        // We pass null for voltage and use batteryPct for the battery field.
        TelemetryMessage message = new TelemetryMessage(
                deviceId, imei, null, batteryPct, gsmSignal, charging, Instant.now()
        );
        telemetryProducer.publishTelemetry(message);
        log.debug("Forwarded heartbeat/alarm telemetry: deviceId={} battery={}%, gsm={}, charging={}",
                deviceId, batteryPct, gsmSignal, charging);
    }

    /**
     * Forwards an external voltage reading extracted from the 0x94 info packet
     * (information type 0x00 = external voltage). Only the voltage field is
     * populated; battery/GSM/charging remain null so the backend's COALESCE
     * logic does not overwrite existing values from heartbeat packets.
     *
     * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
     * Per AGENTS.md rule 17: use shared message contracts (TelemetryMessage).
     */
    @Override
    public void forwardVoltage(UUID deviceId, String imei, Double voltage) {
        if (deviceId == null) {
            log.warn("Skipping voltage forward: deviceId is null");
            return;
        }
        TelemetryMessage message = new TelemetryMessage(
                deviceId, imei, voltage, null, null, null, Instant.now()
        );
        telemetryProducer.publishTelemetry(message);
        log.info("Forwarded external voltage telemetry: deviceId={} imei={} voltage={}V",
                deviceId, imei, voltage);
    }

    /**
     * Forwards a location reading directly (used by fencing protocol handler).
     */
    public void forwardLocation(UUID deviceId, String imei,
                                 Double latitude, Double longitude,
                                 Double speed, Double course) {
        LocationMessage message = new LocationMessage(
                deviceId, imei, latitude, longitude, speed, course, Instant.now()
        );
        deviceEventProducer.publishLocation(message);
    }
}
