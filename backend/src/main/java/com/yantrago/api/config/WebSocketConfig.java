package com.yantrago.api.config;

import com.yantrago.api.websocket.StompChannelInterceptor;
import com.yantrago.api.websocket.WebSocketAuthInterceptor;
import com.yantrago.api.websocket.WebSocketSessionRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.util.UUID;

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
    private final WebSocketSessionRegistry sessionRegistry;
    private final ThreadPoolTaskScheduler heartbeatScheduler;

    @Value("${websocket.allowed-origins:http://localhost:3000,http://localhost:8081}")
    private String allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor,
                           StompChannelInterceptor stompChannelInterceptor,
                           WebSocketSessionRegistry sessionRegistry) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.stompChannelInterceptor = stompChannelInterceptor;
        this.sessionRegistry = sessionRegistry;
        // Scheduler for STOMP heartbeat processing. Owned by this config
        // (not a bean) — a bean named messageBrokerTaskScheduler collides
        // with Spring's DelegatingWebSocketMessageBrokerConfiguration.
        // Daemon so it never blocks JVM shutdown.
        this.heartbeatScheduler = new ThreadPoolTaskScheduler();
        this.heartbeatScheduler.setPoolSize(1);
        this.heartbeatScheduler.setDaemon(true);
        this.heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        this.heartbeatScheduler.initialize();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker for /topic and /user/queue destinations
        // /user/queue/* supports user-specific channels (e.g. notification invalidation)
        // Heartbeats are negotiated with clients so silently-dead mobile TCP
        // connections are detected (default 0,0 disables them entirely).
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler);
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
                .setAllowedOriginPatterns(origins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Phase 3 fix: validate STOMP SUBSCRIBE/SEND frames
        registration.interceptors(stompChannelInterceptor);
    }

    /**
     * SEC-03/SEC-09: Registers a WebSocket handler decorator that tracks
     * active sessions in {@link WebSocketSessionRegistry}. When a user's
     * sessions are revoked (Phase D), the registry closes all of that
     * user's active WebSocket connections immediately.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                UUID userId = (UUID) session.getAttributes().get("userId");
                if (userId != null) {
                    sessionRegistry.register(userId, session);
                }
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                UUID userId = (UUID) session.getAttributes().get("userId");
                if (userId != null) {
                    sessionRegistry.unregister(userId, session);
                }
                super.afterConnectionClosed(session, status);
            }
        });
    }
}
