package com.yantrago.gateway.tcp.fencing;

import com.yantrago.gateway.queue.DeviceEventProducer;
import com.yantrago.gateway.service.DeviceHeartbeatService;
import com.yantrago.gateway.service.DeviceMappingCacheService;
import com.yantrago.gateway.service.DeviceStateService;
import com.yantrago.gateway.service.TelemetryForwardService;
import com.yantrago.gateway.service.VehicleCommandService;
import com.yantrago.gateway.tcp.DeviceConnectionRegistry;
import com.yantrago.gateway.tcp.ProtocolHandler;
import com.yantrago.shared.queue.DeviceEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YantraGO fencing protocol handler.
 *
 * Implements ProtocolHandler for the custom YantraGO fencing machine protocol.
 * Handles login, heartbeat, GPS, telemetry, fencing state, and command reply packets.
 * Forwards telemetry and location data to the backend via TelemetryForwardService.
 * Publishes device lifecycle events via DeviceEventProducer.
 *
 * Per AGENTS.md rule 3: TCP protocol handling is in the gateway, not REST controllers.
 * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 */
@Component
public class FencingProtocolHandler implements ProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(FencingProtocolHandler.class);

    private final Map<String, String> clientImeiMap = new ConcurrentHashMap<>();

    private final FencingParser parser;
    private final FencingEncoder encoder;
    private final DeviceConnectionRegistry connectionRegistry;
    private final DeviceMappingCacheService deviceMappingCacheService;
    private final TelemetryForwardService telemetryForwardService;
    private final VehicleCommandService vehicleCommandService;
    private final DeviceEventProducer deviceEventProducer;
    private final DeviceHeartbeatService deviceHeartbeatService;
    private final DeviceStateService deviceStateService;

    public FencingProtocolHandler(
            FencingParser parser,
            FencingEncoder encoder,
            DeviceConnectionRegistry connectionRegistry,
            DeviceMappingCacheService deviceMappingCacheService,
            @Lazy TelemetryForwardService telemetryForwardService,
            @Lazy VehicleCommandService vehicleCommandService,
            DeviceEventProducer deviceEventProducer,
            DeviceHeartbeatService deviceHeartbeatService,
            DeviceStateService deviceStateService
    ) {
        this.parser = parser;
        this.encoder = encoder;
        this.connectionRegistry = connectionRegistry;
        this.deviceMappingCacheService = deviceMappingCacheService;
        this.telemetryForwardService = telemetryForwardService;
        this.vehicleCommandService = vehicleCommandService;
        this.deviceEventProducer = deviceEventProducer;
        this.deviceHeartbeatService = deviceHeartbeatService;
        this.deviceStateService = deviceStateService;
        log.info("[Fencing] Protocol handler initialized");
    }

    @Override
    public String getProtocolName() {
        return "YANTRAGO_FENCING";
    }

    @Override
    public boolean canHandle(byte[] firstBytes) {
        if (firstBytes == null || firstBytes.length < 2) {
            return false;
        }
        return firstBytes[0] == FencingConstants.START_BYTES[0]
                && firstBytes[1] == FencingConstants.START_BYTES[1];
    }

    @Override
    public String getImeiForClient(String clientId) {
        return clientImeiMap.get(clientId);
    }

    @Override
    public void removeClient(String clientId) {
        String imei = clientImeiMap.remove(clientId);
        if (imei != null) {
            deviceHeartbeatService.markOffline(imei);
            deviceStateService.clearState(imei);
            publishDeviceEvent(imei, DeviceEventMessage.EVENT_DISCONNECT);
        }
    }

    @Override
    public byte[] handlePacket(byte[] packet, String clientId) {
        if (packet.length < 6) {
            log.warn("[Fencing] Packet too short: {} bytes", packet.length);
            return null;
        }

        // Validate CRC
        if (!parser.validateCrc(packet)) {
            log.warn("[Fencing] Invalid CRC from client={}", clientId);
            return null;
        }

        int opcode = parser.getOpcode(packet);
        byte[] payload = parser.getPayload(packet);

        log.debug("[Fencing] Packet from {}: opcode=0x{} payloadLen={}",
                clientId, String.format("%02X", opcode), payload.length);

        return switch (opcode) {
            case FencingConstants.OP_LOGIN -> handleLogin(payload, clientId);
            case FencingConstants.OP_HEARTBEAT -> handleHeartbeat(payload, clientId);
            case FencingConstants.OP_GPS -> handleGps(payload, clientId);
            case FencingConstants.OP_TELEMETRY -> handleTelemetry(payload, clientId);
            case FencingConstants.OP_FENCING_STATE -> handleFencingState(payload, clientId);
            case FencingConstants.OP_COMMAND_REPLY -> handleCommandReply(payload, clientId);
            case FencingConstants.OP_ALARM -> handleAlarm(payload, clientId);
            default -> {
                log.warn("[Fencing] Unknown opcode: 0x{}", String.format("%02X", opcode));
                yield null;
            }
        };
    }

    /**
     * Builds an ON command packet for fencing machines.
     */
    public byte[] buildOnCommand() {
        return encoder.buildOnCommand();
    }

    /**
     * Builds an OFF command packet for fencing machines.
     */
    public byte[] buildOffCommand() {
        return encoder.buildOffCommand();
    }

    // ===== Packet handlers =====

    private byte[] handleLogin(byte[] payload, String clientId) {
        String imei = parser.parseImei(payload);
        if (imei == null) {
            log.warn("[Fencing] Login failed: could not parse IMEI from client={}", clientId);
            return encoder.buildAck(FencingConstants.OP_LOGIN);
        }

        log.info("[Fencing] Login from IMEI: {}", imei);
        clientImeiMap.put(clientId, imei);

        deviceHeartbeatService.recordHeartbeat(imei);
        publishDeviceEvent(imei, DeviceEventMessage.EVENT_LOGIN);

        return encoder.buildAck(FencingConstants.OP_LOGIN);
    }

    private byte[] handleHeartbeat(byte[] payload, String clientId) {
        String imei = clientImeiMap.get(clientId);
        if (imei == null) {
            log.warn("[Fencing] Heartbeat from unregistered client: {}", clientId);
            return encoder.buildAck(FencingConstants.OP_HEARTBEAT);
        }

        log.debug("[Fencing] Heartbeat from imei={}", imei);
        deviceHeartbeatService.recordHeartbeat(imei);
        publishDeviceEvent(imei, DeviceEventMessage.EVENT_HEARTBEAT);

        return encoder.buildAck(FencingConstants.OP_HEARTBEAT);
    }

    private byte[] handleGps(byte[] payload, String clientId) {
        String imei = clientImeiMap.get(clientId);
        if (imei == null) {
            log.warn("[Fencing] GPS from unregistered client: {}", clientId);
            return encoder.buildAck(FencingConstants.OP_GPS);
        }

        FencingParser.GpsData gps = parser.parseGps(payload);
        if (gps == null) {
            return encoder.buildAck(FencingConstants.OP_GPS);
        }

        // Resolve device ID and forward location to backend
        UUID deviceId = resolveDeviceId(imei);
        if (deviceId != null) {
            telemetryForwardService.forwardLocation(
                    deviceId, imei,
                    gps.latitude(), gps.longitude(),
                    (double) gps.speed(), (double) gps.course()
            );
            deviceStateService.updatePosition(imei, gps.latitude(), gps.longitude(),
                    gps.speed(), gps.course());
        }

        return encoder.buildAck(FencingConstants.OP_GPS);
    }

    private byte[] handleTelemetry(byte[] payload, String clientId) {
        String imei = clientImeiMap.get(clientId);
        if (imei == null) {
            log.warn("[Fencing] Telemetry from unregistered client: {}", clientId);
            return encoder.buildAck(FencingConstants.OP_TELEMETRY);
        }

        FencingParser.TelemetryData telemetry = parser.parseTelemetry(payload);
        if (telemetry == null) {
            return encoder.buildAck(FencingConstants.OP_TELEMETRY);
        }

        // Forward telemetry to backend
        UUID deviceId = resolveDeviceId(imei);
        if (deviceId != null) {
            telemetryForwardService.forwardTelemetry(
                    deviceId, imei,
                    telemetry.voltage(), (double) telemetry.battery(), telemetry.gsmSignal()
            );
            deviceStateService.updateTelemetry(imei, telemetry.voltage(),
                    (double) telemetry.battery(), telemetry.gsmSignal());
        }

        return encoder.buildAck(FencingConstants.OP_TELEMETRY);
    }

    private byte[] handleFencingState(byte[] payload, String clientId) {
        String imei = clientImeiMap.get(clientId);
        if (imei == null) {
            log.warn("[Fencing] State from unregistered client: {}", clientId);
            return encoder.buildAck(FencingConstants.OP_FENCING_STATE);
        }

        FencingParser.FencingStateData state = parser.parseFencingState(payload);
        if (state == null) {
            return encoder.buildAck(FencingConstants.OP_FENCING_STATE);
        }

        log.info("[Fencing] State from imei={}: state={} outputV={}V current={}mA energy={}",
                imei, FencingParser.stateName(state.state()),
                state.outputVoltage(), state.current(), state.energyPulses());

        // Update relay state in Redis
        deviceStateService.updateRelayState(imei, state.state() == FencingConstants.FENCING_ON);

        return encoder.buildAck(FencingConstants.OP_FENCING_STATE);
    }

    private byte[] handleCommandReply(byte[] payload, String clientId) {
        String imei = clientImeiMap.get(clientId);
        if (imei == null) {
            log.warn("[Fencing] Command reply from unregistered client: {}", clientId);
            return encoder.buildAck(FencingConstants.OP_COMMAND_REPLY);
        }

        FencingParser.CommandReplyData reply = parser.parseCommandReply(payload);
        if (reply == null) {
            return encoder.buildAck(FencingConstants.OP_COMMAND_REPLY);
        }

        log.info("[Fencing] Command reply from imei={}: success={} state={}",
                imei, reply.success(), FencingParser.stateName(reply.fencingState()));

        // Process the reply via VehicleCommandService (publishes CommandResultMessage)
        try {
            vehicleCommandService.processCommandReply(imei, reply.success(),
                    "fencing_state=" + reply.fencingState(), reply.fencingState() == FencingConstants.FENCING_ON);
        } catch (Exception e) {
            log.error("[Fencing] Failed to process command reply: {}", e.getMessage());
        }

        return encoder.buildAck(FencingConstants.OP_COMMAND_REPLY);
    }

    private byte[] handleAlarm(byte[] payload, String clientId) {
        String imei = clientImeiMap.get(clientId);
        log.warn("[Fencing] Alarm from imei={} clientId={} payloadLen={}",
                imei, clientId, payload.length);
        // In production, forward alarm to backend via AlertEventMessage
        return encoder.buildAck(FencingConstants.OP_ALARM);
    }

    // ===== Helpers =====

    private UUID resolveDeviceId(String imei) {
        String deviceIdStr = deviceMappingCacheService.getDeviceIdByImei(imei);
        if (deviceIdStr == null) {
            log.warn("[Fencing] No device mapping for IMEI: {}", imei);
            return null;
        }
        try {
            return UUID.fromString(deviceIdStr);
        } catch (IllegalArgumentException e) {
            log.error("[Fencing] Invalid device ID format: {}", deviceIdStr);
            return null;
        }
    }

    private void publishDeviceEvent(String imei, String eventType) {
        try {
            UUID deviceId = resolveDeviceId(imei);
            DeviceEventMessage event = new DeviceEventMessage(
                    deviceId, imei, eventType, Instant.now()
            );
            deviceEventProducer.publishDeviceEvent(event);
        } catch (Exception e) {
            log.error("[Fencing] Failed to publish device event: {}", e.getMessage());
        }
    }
}
