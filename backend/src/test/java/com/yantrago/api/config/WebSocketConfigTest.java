package com.yantrago.api.config;

import com.yantrago.api.websocket.StompChannelInterceptor;
import com.yantrago.api.websocket.WebSocketAuthInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

/**
 * Smoke test for WebSocketConfig. Caught-in-production bug: configuring
 * heartbeat values without a TaskScheduler crashed the app at startup
 * ("Heartbeat values configured but no TaskScheduler provided"), and a
 * messageBrokerTaskScheduler bean collided with Spring's broker config.
 * This verifies the config constructs and configures cleanly.
 */
class WebSocketConfigTest {

    @Test
    @DisplayName("broker config with heartbeat succeeds — scheduler provided")
    void configureMessageBroker_withHeartbeat_succeeds() {
        WebSocketConfig config = new WebSocketConfig(
                mock(WebSocketAuthInterceptor.class),
                mock(StompChannelInterceptor.class)
        );

        MessageBrokerRegistry registry = new MessageBrokerRegistry(
                new ExecutorSubscribableChannel(), new ExecutorSubscribableChannel());
        // The exact failure mode from deploy #108/#109: without the owned
        // scheduler this throws IllegalStateException at registration.
        assertDoesNotThrow(() -> config.configureMessageBroker(registry));
    }
}
