package com.yantrago.api.config;

import com.yantrago.api.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket configuration.
 *
 * Registers the /ws endpoint for client connections and configures
 * a simple in-memory message broker with /topic prefix for subscriptions.
 *
 * Clients connect to /ws with SockJS fallback, then subscribe to:
 *   /topic/location/{machineId} — live GPS updates
 *   /topic/command/{machineId} — command status updates
 *   /topic/telemetry/{machineId} — telemetry updates
 *   /topic/device/{deviceId} — device online/offline events
 *
 * Per AGENTS.md rule 12: production feature requiring security checks (JWT auth via interceptor).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker for /topic destinations
        config.enableSimpleBroker("/topic");
        // Application-level prefix for messages bound for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
