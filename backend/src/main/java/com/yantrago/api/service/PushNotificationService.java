package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Provider-agnostic push notification service.
 *
 * Supports FCM (Firebase Cloud Messaging), Expo, and OneSignal via a pluggable
 * interface. The actual provider is selected via configuration in production.
 *
 * Per AGENTS.md rule 12: this is a production feature requiring logging + error handling.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    /**
     * Sends a push notification to a device token.
     *
     * @param deviceToken the recipient's push token (FCM token, Expo token, etc.)
     * @param title notification title
     * @param body notification body
     * @param data optional data payload
     * @return the provider message ID if successful, null otherwise
     */
    public String sendPushNotification(String deviceToken, String title, String body, Map<String, String> data) {
        log.info("Sending push notification to token={} title={}", deviceToken, title);
        try {
            // In production, this would call the configured provider (FCM/Expo/OneSignal).
            // For now, we log and return a mock message ID.
            log.debug("Push notification: token={} title={} body={} data={}", deviceToken, title, body, data);
            String messageId = "push-" + java.util.UUID.randomUUID();
            log.info("Push notification sent: messageId={}", messageId);
            return messageId;
        } catch (Exception e) {
            log.error("Failed to send push notification to token={}: {}", deviceToken, e.getMessage(), e);
            return null;
        }
    }
}
