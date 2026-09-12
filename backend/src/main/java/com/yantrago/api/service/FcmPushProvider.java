package com.yantrago.api.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Real FCM push provider using the Firebase Admin SDK.
 *
 * Per notification plan Phase 5:
 * - Real FCM provider with honest delivery status
 * - No production mock-success fallback
 * - FCM response IDs are recorded without being mislabeled as device delivery
 * - ACCEPTED_BY_PROVIDER means the message was accepted by FCM, not delivered to device
 *
 * Per AGENTS.md rule 12: production feature with logging + error handling.
 * Per AGENTS.md rule 20: no secrets in source code — credentials from env vars.
 */
@Service
public class FcmPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(FcmPushProvider.class);

    @Value("${push.provider:none}")
    private String pushProvider;

    @Value("${push.fcm.ttl-seconds:86400}")
    private int ttlSeconds;

    /**
     * Sends a push notification to a single FCM token.
     *
     * @param token the FCM registration token
     * @param title notification title
     * @param body notification body
     * @param data optional data payload
     * @return FcmSendResult with provider message ID or error
     */
    @Override
    public PushSendResult send(String token, String title, String body, Map<String, String> data) {
        if ("none".equals(pushProvider)) {
            log.debug("Push provider is 'none' — skipping FCM send for token={}", token);
            return PushSendResult.skipped("Push provider is disabled");
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            // Android-specific config with channel and TTL
            messageBuilder.setAndroidConfig(AndroidConfig.builder()
                    .setTtl(ttlSeconds * 1000L) // milliseconds
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setChannelId("yantrago_alerts")
                            .setPriority(AndroidNotification.Priority.HIGH)
                            .build())
                    .build());

            // iOS-specific config
            messageBuilder.setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setSound("default")
                            .setBadge(1)
                            .build())
                    .build());

            // Data payload (for foreground handling and tap navigation)
            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            Message message = messageBuilder.build();
            String messageId = FirebaseMessaging.getInstance().send(message);

            log.info("FCM send accepted: token={} messageId={}", token, messageId);
            // ACCEPTED_BY_PROVIDER — FCM accepted the message, not device delivery
            return PushSendResult.success(messageId);

        } catch (FirebaseMessagingException e) {
            // Use getMessagingErrorCode() which returns MessagingErrorCode enum
            // (UNREGISTERED, QUOTA_EXCEEDED, UNAVAILABLE, etc.) — not getErrorCode()
            // which returns the generic ErrorCode enum lacking those values.
            String errorCode = e.getMessagingErrorCode() != null
                    ? e.getMessagingErrorCode().name() : "UNKNOWN";
            String errorMsg = e.getMessage();

            // Check for invalid token errors — these are permanent failures
            if (isInvalidTokenError(errorCode)) {
                log.warn("FCM invalid token: token={} errorCode={} — marking for removal", token, errorCode);
                return PushSendResult.invalidToken(errorCode, errorMsg);
            }

            // Check for throttling/quota errors — transient but need longer backoff
            if (isThrottlingError(errorCode)) {
                log.warn("FCM throttled/quota: token={} errorCode={} — retry with backoff", token, errorCode);
                return PushSendResult.transientFailure(errorCode, errorMsg);
            }

            // Other transient errors — retryable
            log.warn("FCM send failed (transient): token={} errorCode={} {}", token, errorCode, errorMsg);
            return PushSendResult.transientFailure(errorCode, errorMsg);

        } catch (Exception e) {
            log.error("FCM send unexpected error: token={} {}", token, e.getMessage(), e);
            return PushSendResult.transientFailure("UNKNOWN", e.getMessage());
        }
    }

    /**
     * Determines if the FCM error indicates an invalid/unregistered token
     * that should be permanently removed.
     */
    private boolean isInvalidTokenError(String errorCode) {
        return "UNREGISTERED".equals(errorCode)
                || "INVALID_REGISTRATION".equals(errorCode)
                || "REGISTRATION_NOT_FOUND".equals(errorCode)
                || "INVALID_ARGUMENT".equals(errorCode)
                || "messaging/registration-token-not-registered".equals(errorCode);
    }

    /**
     * Determines if the FCM error indicates throttling/quota exceeded.
     * These are transient — retryable with backoff.
     */
    private boolean isThrottlingError(String errorCode) {
        return "QUOTA_EXCEEDED".equals(errorCode)
                || "RATE_LIMITED".equals(errorCode)
                || "messaging/quota-exceeded".equals(errorCode)
                || "messaging/rate-exceeded".equals(errorCode)
                || "429".equals(errorCode)
                || "UNAVAILABLE".equals(errorCode)
                || "messaging/server-unavailable".equals(errorCode)
                || "messaging/internal-error".equals(errorCode)
                || "500".equals(errorCode)
                || "503".equals(errorCode);
    }

}

