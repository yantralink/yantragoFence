package com.yantrago.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

/**
 * STOMP WebSocket controller for machine tracking subscriptions.
 *
 * Clients send messages to /app/subscribe with a machineId to subscribe
 * to real-time updates. The actual broadcasting is done by:
 *   - LocationBroadcastService → /topic/location/{machineId}
 *   - CommandBroadcastService → /topic/command/{machineId}
 *   - TelemetryBroadcastService → /topic/telemetry/{machineId}
 *
 * Clients subscribe directly to /topic/* destinations via STOMP SUBSCRIBE frames.
 * This controller handles application-level messages sent to /app/* prefixes.
 *
 * Per AGENTS.md rule 12: security checks required (JWT auth via WebSocketAuthInterceptor).
 */
@Controller
public class TrackingWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(TrackingWebSocketController.class);

    /**
     * Handles subscription requests. Clients send a message to /app/subscribe
     * with the machineId they want to track.
     *
     * Note: Actual subscription authorization is enforced at the STOMP
     * SUBSCRIBE frame level by {@link StompChannelInterceptor}, which calls
     * {@link com.yantrago.api.security.MachineAccessPolicy} for machine-scoped
     * topics. This controller endpoint is an application-level logging/audit
     * convenience — it does not gate access.
     */
    @MessageMapping("/subscribe")
    public void handleSubscription(@Payload Map<String, String> payload,
                                   SimpMessageHeaderAccessor headerAccessor) {
        UUID userId = (UUID) headerAccessor.getSessionAttributes().get("userId");
        UUID organizationId = (UUID) headerAccessor.getSessionAttributes().get("organizationId");

        String machineIdStr = payload.get("machineId");
        if (machineIdStr == null) {
            log.warn("WebSocket subscribe: no machineId in payload from user={}", userId);
            return;
        }

        log.info("WebSocket subscribe: user={} orgId={} subscribing to machineId={}",
                userId, organizationId, machineIdStr);
    }

    /**
     * Handles unsubscribe requests.
     */
    @MessageMapping("/unsubscribe")
    public void handleUnsubscribe(@Payload Map<String, String> payload,
                                  SimpMessageHeaderAccessor headerAccessor) {
        UUID userId = (UUID) headerAccessor.getSessionAttributes().get("userId");
        String machineIdStr = payload.get("machineId");

        log.info("WebSocket unsubscribe: user={} machineId={}", userId, machineIdStr);
    }
}
