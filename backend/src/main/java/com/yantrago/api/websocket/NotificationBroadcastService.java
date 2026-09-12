package com.yantrago.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcasts notification inbox updates to specific users via private WebSocket channels.
 *
 * Clients subscribe to /user/queue/notifications to receive real-time notification
 * invalidation events (new notification, read state change).
 *
 * Per notification plan Phase 3: private WebSocket invalidation.
 * Per AGENTS.md rule 9: security checks (user-specific channel, no cross-user leakage).
 */
@Service
public class NotificationBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends a notification invalidation event to a specific user.
     * The client receives this on /user/queue/notifications and should refresh their inbox.
     *
     * @param userId the user to notify
     * @param eventType NEW | READ | READ_ALL
     * @param unreadCount the current unread count for the user
     */
    public void sendNotificationEvent(UUID userId, String eventType, long unreadCount) {
        if (userId == null) {
            return;
        }

        String destination = "/queue/notifications";
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("unreadCount", unreadCount);
        payload.put("timestamp", java.time.Instant.now().toString());

        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                destination,
                payload
        );

        log.debug("Sent notification event to user={} type={} unread={}", userId, eventType, unreadCount);
    }

    /**
     * Broadcasts a new notification event to a user.
     */
    public void notifyNewNotification(UUID userId, long unreadCount) {
        sendNotificationEvent(userId, "NEW", unreadCount);
    }

    /**
     * Broadcasts a read state change to a user.
     */
    public void notifyReadStateChange(UUID userId, long unreadCount) {
        sendNotificationEvent(userId, "READ", unreadCount);
    }
}
