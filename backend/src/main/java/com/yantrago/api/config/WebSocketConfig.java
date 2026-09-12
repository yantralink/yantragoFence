package com.yantrago.api.config;

import com.yantrago.api.websocket.StompChannelInterceptor;
import com.yantrago.api.websocket.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
 *   /user/queue/notifications — private notification invalidation
 *
 * Per AGENTS.md rule 12: production feature requiring security checks.
 * Per notification plan N5: validate STOMP CONNECT/SUBSCRIBE/SEND.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final StompChannelInterceptor stompChannelInterceptor;

    @Value("${websocket.allowed-origins:http://localhost:3000,http://localhost:8081}")
    private String allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor,
                           StompChannelInterceptor stompChannelInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.stompChannelInterceptor = stompChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker for /topic and /user/queue destinations
        // /user/queue/* supports user-specific channels (e.g. notification invalidation)
        config.enableSimpleBroker("/topic", "/queue");
        // Application-level prefix for messages bound for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
        // User-specific destination prefix (for convertAndSendToUser)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Phase 3 fix: restrict allowed origins instead of wildcard
        String[] origins = allowedOrigins.split("\\s*,\\s*");
        registry.addEndpoint("/ws")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Phase 3 fix: validate STOMP SUBSCRIBE/SEND frames
        registration.interceptors(stompChannelInterceptor);
    }
}
