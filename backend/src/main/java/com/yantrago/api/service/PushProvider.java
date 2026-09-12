package com.yantrago.api.service;

import java.util.Map;

/**
 * Push provider abstraction.
 *
 * Per notification plan Phase 5:
 * "Put it behind a small PushProvider interface so the delivery path can be
 * tested with a controlled adapter and so additional providers (APNs direct,
 * other FCM projects) can be added without rewiring PushDeliveryService."
 *
 * Implementations:
 * - FcmPushProvider: real Firebase Cloud Messaging provider
 *
 * Per AGENTS.md rule 12: production feature with error handling.
 */
public interface PushProvider {

    /**
     * Sends a push notification to a single device token.
     *
     * @param token the provider registration token
     * @param title notification title
     * @param body notification body
     * @param data optional data payload (must contain only opaque IDs and minimal text)
     * @return PushSendResult with provider message ID or categorized error
     */
    PushSendResult send(String token, String title, String body, Map<String, String> data);

    /**
     * Result of a push send attempt.
     * Per notification plan Phase 5: honest delivery status.
     * - SUCCESS: provider accepted the message (ACCEPTED_BY_PROVIDER, not device delivery)
     * - INVALID_TOKEN: token is permanently invalid — should be removed
     * - TRANSIENT_FAILURE: temporary error — retryable
     * - SKIPPED: push disabled or no token
     */
    record PushSendResult(Status status, String providerMessageId, String errorCode, String errorMessage) {
        public static PushSendResult success(String messageId) {
            return new PushSendResult(Status.SUCCESS, messageId, null, null);
        }
        public static PushSendResult invalidToken(String errorCode, String errorMsg) {
            return new PushSendResult(Status.INVALID_TOKEN, null, errorCode, errorMsg);
        }
        public static PushSendResult transientFailure(String errorCode, String errorMsg) {
            return new PushSendResult(Status.TRANSIENT_FAILURE, null, errorCode, errorMsg);
        }
        public static PushSendResult skipped(String reason) {
            return new PushSendResult(Status.SKIPPED, null, null, reason);
        }
        public boolean isSuccess() { return status == Status.SUCCESS; }
        public boolean isInvalidToken() { return status == Status.INVALID_TOKEN; }
        public boolean isTransientFailure() { return status == Status.TRANSIENT_FAILURE; }
        public boolean isSkipped() { return status == Status.SKIPPED; }
    }

    enum Status {
        SUCCESS,           // Provider accepted (ACCEPTED_BY_PROVIDER)
        INVALID_TOKEN,     // Token permanently invalid — remove
        TRANSIENT_FAILURE, // Temporary error — retry
        SKIPPED            // Push disabled or no token
    }
}
