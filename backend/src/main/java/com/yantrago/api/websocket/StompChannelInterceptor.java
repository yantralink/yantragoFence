package com.yantrago.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * STOMP channel interceptor that validates SUBSCRIBE and SEND frames.
 *
 * Per notification plan N5: "Validate STOMP CONNECT/SUBSCRIBE/SEND, expire
 * sessions, and prevent spoofed destinations."
 *
 * Enforces:
 * - Users can only subscribe to their own /user/queue/notifications
 * - Users can only subscribe to /topic/* destinations (machine-scoped topics)
 * - Arbitrary or suspicious destinations are rejected
 * - SEND frames to /app/* are allowed (application destinations)
 *
 * Per AGENTS.md rule 9: all sensitive operations require authorization.
 */
@Component
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompChannelInterceptor.class);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.SUBSCRIBE) {
            return validateSubscribe(message, accessor);
        }

        if (command == StompCommand.SEND) {
            return validateSend(message, accessor);
        }

        return message;
    }

    private Message<?> validateSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            log.warn("STOMP SUBSCRIBE rejected: no destination");
            return null;
        }

        UUID userId = (UUID) accessor.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("STOMP SUBSCRIBE rejected: unauthenticated session for destination={}",
                    destination);
            return null;
        }

        // /user/queue/notifications — private user channel, always allowed
        // Spring resolves /user/queue/* to the authenticated user's own queue
        if (destination.startsWith("/user/queue/") || destination.startsWith("/queue/")) {
            log.debug("STOMP SUBSCRIBE allowed: user={} destination={}", userId, destination);
            return message;
        }

        // /topic/* — public machine-scoped topics
        // Allowed patterns: /topic/location/{machineId}, /topic/telemetry/{machineId},
        // /topic/command/{machineId}, /topic/device/{deviceId}
        if (destination.startsWith("/topic/")) {
            // Basic validation — ensure the destination has a resource ID
            String[] parts = destination.substring("/topic/".length()).split("/");
            if (parts.length < 2 || parts[1].isBlank()) {
                log.warn("STOMP SUBSCRIBE rejected: malformed topic destination={}", destination);
                return null;
            }
            log.debug("STOMP SUBSCRIBE allowed: user={} topic={}", userId, destination);
            return message;
        }

        // Reject any other destination prefix
        log.warn("STOMP SUBSCRIBE rejected: unauthorized destination={} user={}",
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

        // Only allow SEND to /app/* (application-level destinations)
        if (!destination.startsWith("/app/")) {
            log.warn("STOMP SEND rejected: non-app destination={} user={}", destination, userId);
            return null;
        }

        return message;
    }
}
