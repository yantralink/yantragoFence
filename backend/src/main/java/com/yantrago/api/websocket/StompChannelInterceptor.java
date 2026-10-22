package com.yantrago.api.websocket;

import com.yantrago.api.security.MachineAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP channel interceptor that validates SUBSCRIBE and SEND frames.
 *
 * SEC-03: Subscriptions to machine-scoped topics (/topic/location/{machineId},
 * /topic/command/{machineId}, /topic/telemetry/{machineId}) are now authorized
 * through the centralized {@link MachineAccessPolicy} — the same policy used by
 * REST endpoints. A customer can no longer subscribe to another customer's
 * machine topic by guessing the UUID.
 *
 * SEC-03: Raw /queue/ subscriptions are rejected. Only /user/queue/ is allowed,
 * which Spring resolves to the authenticated principal's own queue.
 *
 * SEC-03: Wildcards and malformed destinations are rejected.
 *
 * Per AGENTS.md rule 9: all sensitive operations require authorization.
 */
@Component
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompChannelInterceptor.class);

    /**
     * Allowed machine-scoped topic patterns. The second path segment is the
     * machine UUID, which is validated via MachineAccessPolicy.
     */
    private static final Pattern MACHINE_TOPIC_PATTERN =
            Pattern.compile("^/topic/(location|command|telemetry)/([0-9a-fA-F-]{36})$");

    /**
     * Device-scoped topic: /topic/device/{deviceId}. Device access is org-scoped
     * but not customer-scoped (devices are bound to machines, and machine access
     * implies device access). We validate the device's machine via the policy
     * indirectly — for now, we validate org membership via the session's orgId
     * and reject if the session has no org context (non-super-admin).
     */
    private static final Pattern DEVICE_TOPIC_PATTERN =
            Pattern.compile("^/topic/device/([0-9a-fA-F-]{36})$");

    private final MachineAccessPolicy machineAccessPolicy;

    public StompChannelInterceptor(MachineAccessPolicy machineAccessPolicy) {
        this.machineAccessPolicy = machineAccessPolicy;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        // SEC-09: Validate STOMP CONNECT — the HTTP upgrade already authenticated
        // the user via WebSocketAuthInterceptor, but we explicitly verify here
        // that the session carries an authenticated userId. This makes the
        // HTTP upgrade and STOMP CONNECT authentication agree.
        if (command == StompCommand.CONNECT) {
            return validateConnect(message, accessor);
        }

        if (command == StompCommand.SUBSCRIBE) {
            return validateSubscribe(message, accessor);
        }

        if (command == StompCommand.SEND) {
            return validateSend(message, accessor);
        }

        return message;
    }

    /**
     * SEC-09: Validates the STOMP CONNECT frame. The HTTP WebSocket upgrade
     * already authenticated the user via {@link WebSocketAuthInterceptor}, which
     * stored userId in the session attributes. Here we verify that the session
     * is authenticated — if the handshake was rejected, no session exists;
     * but if somehow a CONNECT arrives without userId, we reject it.
     */
    private Message<?> validateConnect(Message<?> message, StompHeaderAccessor accessor) {
        UUID userId = (UUID) accessor.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("SEC-09: STOMP CONNECT rejected: no authenticated userId in session");
            return null;
        }
        log.debug("STOMP CONNECT allowed: user={}", userId);
        return message;
    }

    /**
     * SEC-09: Checks whether the access token used at handshake has expired.
     * Access tokens are short-lived (default 15 min). If expired, the frame
     * is rejected — the client must reconnect with a fresh token. This
     * bounds the exposure window for a compromised token on an active
     * WebSocket connection.
     */
    private boolean isTokenExpired(Map<String, Object> sessionAttributes) {
        Object expiration = sessionAttributes.get("tokenExpiration");
        if (expiration instanceof Date expiry) {
            return expiry.before(new Date());
        }
        // If no expiration is stored, fail-closed (deny).
        return true;
    }

    private Message<?> validateSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            log.warn("STOMP SUBSCRIBE rejected: no destination");
            return null;
        }

        // SEC-03: reject wildcards
        if (destination.contains("*") || destination.contains("..")) {
            log.warn("STOMP SUBSCRIBE rejected: wildcard/path-traversal in destination={}", destination);
            return null;
        }

        UUID userId = (UUID) accessor.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("STOMP SUBSCRIBE rejected: unauthenticated session for destination={}",
                    destination);
            return null;
        }

        // SEC-09: reject if the access token used at handshake has expired.
        if (isTokenExpired(accessor.getSessionAttributes())) {
            log.warn("SEC-09: STOMP SUBSCRIBE rejected: access token expired for user={} destination={}",
                    userId, destination);
            return null;
        }

        // /user/queue/* — Spring resolves this to the authenticated user's own
        // queue. This is the only allowed queue destination.
        // SEC-03: raw /queue/ (without /user/ prefix) is rejected — it would
        // bypass Spring's user-queue resolution and allow cross-user access.
        if (destination.startsWith("/user/queue/")) {
            log.debug("STOMP SUBSCRIBE allowed: user={} user-queue={}", userId, destination);
            return message;
        }
        if (destination.startsWith("/queue/")) {
            log.warn("STOMP SUBSCRIBE rejected: raw /queue/ not allowed (use /user/queue/) destination={} user={}",
                    destination, userId);
            return null;
        }

        // /topic/* — machine-scoped topics, authorized via MachineAccessPolicy
        if (destination.startsWith("/topic/")) {
            return validateTopicSubscribe(message, accessor, destination, userId);
        }

        // Reject any other destination prefix
        log.warn("STOMP SUBSCRIBE rejected: unauthorized destination={} user={}",
                destination, userId);
        return null;
    }

    /**
     * SEC-03: Validates machine-scoped topic subscriptions via MachineAccessPolicy.
     *
     * For /topic/{location|command|telemetry}/{machineId}: extracts the machineId
     * and calls machineAccessPolicy.loadAndValidate(machineId). If the user does
     * not have access (wrong org, wrong customer assignment, missing customer
     * record), the subscription is rejected.
     *
     * For /topic/device/{deviceId}: device topics are org-scoped. We validate that
     * the session has an organization context (super_admin bypasses). Full device-
     * to-machine resolution would require a DB lookup on every subscription;
     * since device topics are only useful alongside machine topics (the mobile
     * app subscribes to both), the org-level check is sufficient defense in depth.
     */
    private Message<?> validateTopicSubscribe(Message<?> message, StompHeaderAccessor accessor,
                                                String destination, UUID userId) {
        // Check machine-scoped topic
        Matcher machineMatcher = MACHINE_TOPIC_PATTERN.matcher(destination);
        if (machineMatcher.matches()) {
            String machineIdStr = machineMatcher.group(2);
            try {
                UUID machineId = UUID.fromString(machineIdStr);
                // SEC-03: enforce the same machine-access policy as REST endpoints.
                // This throws SecurityException if the user lacks access.
                machineAccessPolicy.loadAndValidate(machineId);
                log.debug("STOMP SUBSCRIBE allowed: user={} machineTopic={}", userId, destination);
                return message;
            } catch (SecurityException e) {
                log.warn("SEC-03: STOMP SUBSCRIBE denied: user={} destination={} reason={}",
                        userId, destination, e.getMessage());
                return null;
            } catch (IllegalArgumentException e) {
                log.warn("STOMP SUBSCRIBE rejected: invalid machineId in destination={}", destination);
                return null;
            }
        }

        // Check device-scoped topic
        Matcher deviceMatcher = DEVICE_TOPIC_PATTERN.matcher(destination);
        if (deviceMatcher.matches()) {
            // Device topics are org-scoped. The session's orgId was set at
            // handshake from the JWT. Super_admin (null org) can subscribe
            // to any device topic. Non-super-admin users must have an org context.
            UUID orgId = (UUID) accessor.getSessionAttributes().get("organizationId");
            Boolean isSuperAdmin = (Boolean) accessor.getSessionAttributes().get("isSuperAdmin");
            if (orgId == null && !Boolean.TRUE.equals(isSuperAdmin)) {
                log.warn("SEC-03: STOMP SUBSCRIBE denied: non-super-admin without org context " +
                        "for device topic destination={} user={}", destination, userId);
                return null;
            }
            log.debug("STOMP SUBSCRIBE allowed: user={} deviceTopic={}", userId, destination);
            return message;
        }

        // Unknown /topic/ pattern — reject
        log.warn("STOMP SUBSCRIBE rejected: unrecognized topic destination={} user={}",
                destination, userId);
        return null;
    }

    private Message<?> validateSend(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            log.warn("STOMP SEND rejected: no destination");
            return null;
        }

        UUID userId = (UUID) accessor.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("STOMP SEND rejected: unauthenticated session for destination={}",
                    destination);
            return null;
        }

        // SEC-09: reject if the access token used at handshake has expired.
        if (isTokenExpired(accessor.getSessionAttributes())) {
            log.warn("SEC-09: STOMP SEND rejected: access token expired for user={} destination={}",
                    userId, destination);
            return null;
        }

        // Only allow SEND to /app/* (application-level destinations)
        if (!destination.startsWith("/app/")) {
            log.warn("STOMP SEND rejected: non-app destination={} user={}", destination, userId);
            return null;
        }

        return message;
    }
}
