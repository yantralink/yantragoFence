package com.yantrago.api.websocket;

import com.yantrago.api.security.MachineAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SEC-03 / SEC-09: Regression tests for StompChannelInterceptor.
 *
 * Covers:
 * - Machine-scoped topic subscriptions authorized via MachineAccessPolicy
 * - Cross-tenant/cross-customer subscriptions denied
 * - Raw /queue/ subscriptions rejected
 * - Wildcard destinations rejected
 * - Malformed UUIDs rejected
 * - Unrecognized /topic/ patterns rejected
 * - /user/queue/ allowed (Spring-resolved user queue)
 * - SEND only to /app/* destinations
 * - Token expiry enforcement (SEC-09)
 * - Unauthenticated session rejected
 */
class StompChannelInterceptorTest {

    private MachineAccessPolicy machineAccessPolicy;
    private StompChannelInterceptor interceptor;
    private MessageChannel channel;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MACHINE_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        machineAccessPolicy = mock(MachineAccessPolicy.class);
        interceptor = new StompChannelInterceptor(machineAccessPolicy);
        channel = mock(MessageChannel.class);
    }

    // --- Helper: build a STOMP frame with session attributes ---

    private Message<?> buildSubscribeMessage(String destination, Map<String, Object> sessionAttrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttrs);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> buildSendMessage(String destination, Map<String, Object> sessionAttrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttrs);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Map<String, Object> validSessionAttrs() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", USER_ID);
        attrs.put("organizationId", ORG_ID);
        attrs.put("isSuperAdmin", false);
        // Non-expired token
        attrs.put("tokenExpiration", new Date(System.currentTimeMillis() + 600_000));
        return attrs;
    }

    private Map<String, Object> expiredSessionAttrs() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", USER_ID);
        attrs.put("organizationId", ORG_ID);
        attrs.put("isSuperAdmin", false);
        // Expired token
        attrs.put("tokenExpiration", new Date(System.currentTimeMillis() - 60_000));
        return attrs;
    }

    private Map<String, Object> unauthenticatedSessionAttrs() {
        return new HashMap<>();
    }

    // --- SEC-03: Machine-scoped topic authorization ---

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /topic/location/{machineId} allowed when MachineAccessPolicy grants access")
    void subscribe_locationTopic_allowed() {
        when(machineAccessPolicy.loadAndValidate(MACHINE_ID))
                .thenReturn(mock(com.yantrago.api.model.Machine.class));

        Message<?> msg = buildSubscribeMessage(
                "/topic/location/" + MACHINE_ID, validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result, "Subscription should be allowed");
        verify(machineAccessPolicy).loadAndValidate(MACHINE_ID);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /topic/command/{machineId} allowed when MachineAccessPolicy grants access")
    void subscribe_commandTopic_allowed() {
        when(machineAccessPolicy.loadAndValidate(MACHINE_ID))
                .thenReturn(mock(com.yantrago.api.model.Machine.class));

        Message<?> msg = buildSubscribeMessage(
                "/topic/command/" + MACHINE_ID, validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result);
        verify(machineAccessPolicy).loadAndValidate(MACHINE_ID);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /topic/telemetry/{machineId} allowed when MachineAccessPolicy grants access")
    void subscribe_telemetryTopic_allowed() {
        when(machineAccessPolicy.loadAndValidate(MACHINE_ID))
                .thenReturn(mock(com.yantrago.api.model.Machine.class));

        Message<?> msg = buildSubscribeMessage(
                "/topic/telemetry/" + MACHINE_ID, validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result);
        verify(machineAccessPolicy).loadAndValidate(MACHINE_ID);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to another customer's machine topic denied")
    void subscribe_crossCustomerMachine_denied() {
        when(machineAccessPolicy.loadAndValidate(MACHINE_ID))
                .thenThrow(new SecurityException("Customer not assigned to machine"));

        Message<?> msg = buildSubscribeMessage(
                "/topic/location/" + MACHINE_ID, validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result, "Cross-customer subscription should be denied");
        verify(machineAccessPolicy).loadAndValidate(MACHINE_ID);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /topic/device/{deviceId} allowed for user with org context")
    void subscribe_deviceTopic_allowedWithOrg() {
        Message<?> msg = buildSubscribeMessage(
                "/topic/device/" + DEVICE_ID, validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result);
        verifyNoInteractions(machineAccessPolicy);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /topic/device/{deviceId} denied for non-super-admin without org context")
    void subscribe_deviceTopic_deniedWithoutOrg() {
        Map<String, Object> attrs = validSessionAttrs();
        attrs.put("organizationId", null);

        Message<?> msg = buildSubscribeMessage(
                "/topic/device/" + DEVICE_ID, attrs);
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /topic/device/{deviceId} allowed for super_admin without org context")
    void subscribe_deviceTopic_allowedForSuperAdmin() {
        Map<String, Object> attrs = validSessionAttrs();
        attrs.put("organizationId", null);
        attrs.put("isSuperAdmin", true);

        Message<?> msg = buildSubscribeMessage(
                "/topic/device/" + DEVICE_ID, attrs);
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result);
    }

    // --- SEC-03: Raw queue and wildcard rejection ---

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to raw /queue/ rejected")
    void subscribe_rawQueue_rejected() {
        Message<?> msg = buildSubscribeMessage("/queue/notifications", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result, "Raw /queue/ subscriptions should be rejected");
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /user/queue/notifications allowed (Spring user queue)")
    void subscribe_userQueue_allowed() {
        Message<?> msg = buildSubscribeMessage("/user/queue/notifications", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE with wildcard rejected")
    void subscribe_wildcard_rejected() {
        Message<?> msg = buildSubscribeMessage("/topic/location/*", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result, "Wildcard subscriptions should be rejected");
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE with path traversal rejected")
    void subscribe_pathTraversal_rejected() {
        Message<?> msg = buildSubscribeMessage("/topic/location/../command/" + MACHINE_ID,
                validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    // --- SEC-03: Malformed IDs and unrecognized patterns ---

    @Test
    @DisplayName("SEC-03: SUBSCRIBE with malformed UUID rejected")
    void subscribe_malformedUuid_rejected() {
        Message<?> msg = buildSubscribeMessage("/topic/location/not-a-uuid", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
        verifyNoInteractions(machineAccessPolicy);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to unrecognized /topic/ pattern rejected")
    void subscribe_unrecognizedTopic_rejected() {
        Message<?> msg = buildSubscribeMessage("/topic/unknown/" + MACHINE_ID, validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to /topic/ with missing ID rejected")
    void subscribe_topicNoId_rejected() {
        Message<?> msg = buildSubscribeMessage("/topic/location", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE to unknown prefix rejected")
    void subscribe_unknownPrefix_rejected() {
        Message<?> msg = buildSubscribeMessage("/amq/queue/test", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    // --- SEC-03: SEND destination validation ---

    @Test
    @DisplayName("SEC-03: SEND to /app/* allowed")
    void send_appDestination_allowed() {
        Message<?> msg = buildSendMessage("/app/subscribe", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result);
    }

    @Test
    @DisplayName("SEC-03: SEND to /topic/* rejected (only /app/* allowed)")
    void send_topicDestination_rejected() {
        Message<?> msg = buildSendMessage("/topic/location/" + MACHINE_ID, validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-03: SEND to /queue/* rejected")
    void send_queueDestination_rejected() {
        Message<?> msg = buildSendMessage("/queue/test", validSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    // --- SEC-09: Token expiry enforcement ---

    @Test
    @DisplayName("SEC-09: SUBSCRIBE rejected when access token expired")
    void subscribe_expiredToken_rejected() {
        when(machineAccessPolicy.loadAndValidate(any()))
                .thenReturn(mock(com.yantrago.api.model.Machine.class));

        Message<?> msg = buildSubscribeMessage(
                "/topic/location/" + MACHINE_ID, expiredSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result, "Subscription should be rejected when token is expired");
        verifyNoInteractions(machineAccessPolicy);
    }

    @Test
    @DisplayName("SEC-09: SEND rejected when access token expired")
    void send_expiredToken_rejected() {
        Message<?> msg = buildSendMessage("/app/subscribe", expiredSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-09: SUBSCRIBE rejected when no token expiration stored (fail-closed)")
    void subscribe_noExpiration_rejected() {
        Message<?> msg = buildSubscribeMessage(
                "/topic/location/" + MACHINE_ID, unauthenticatedSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    // --- Unauthenticated session ---

    @Test
    @DisplayName("SEC-03: SUBSCRIBE from unauthenticated session rejected")
    void subscribe_unauthenticated_rejected() {
        Message<?> msg = buildSubscribeMessage(
                "/topic/location/" + MACHINE_ID, unauthenticatedSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-03: SEND from unauthenticated session rejected")
    void send_unauthenticated_rejected() {
        Message<?> msg = buildSendMessage("/app/subscribe", unauthenticatedSessionAttrs());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-03: SUBSCRIBE with no destination rejected")
    void subscribe_noDestination_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionAttributes(validSessionAttrs());
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result);
    }

    @Test
    @DisplayName("SEC-09: CONNECT with authenticated session allowed")
    void connectFrame_authenticated_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(validSessionAttrs());
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result, "CONNECT from authenticated session should be allowed");
    }

    @Test
    @DisplayName("SEC-09: CONNECT without authenticated session rejected")
    void connectFrame_unauthenticated_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(unauthenticatedSessionAttrs());
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNull(result, "CONNECT without userId should be rejected");
    }

    @Test
    @DisplayName("Non-SUBSCRIBE/SEND/CONNECT frames pass through (e.g. DISCONNECT)")
    void disconnectFrame_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(msg, channel);

        assertNotNull(result, "DISCONNECT frames should pass through");
    }
}
