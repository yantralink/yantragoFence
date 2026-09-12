package com.yantrago.api.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for FcmPushProvider.
 *
 * Verifies Phase 5 acceptance criteria:
 * - Provider disabled returns SKIPPED (no mock-success fallback)
 * - Honest delivery status (SUCCESS, INVALID_TOKEN, TRANSIENT_FAILURE, SKIPPED)
 * - FCM response IDs recorded without being mislabeled as device delivery
 *
 * Per notification plan Phase 5: provider-adapter tests with controlled responses.
 * No production mock-success fallback.
 */
class FcmPushProviderTest {

    private FcmPushProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FcmPushProvider();
        ReflectionTestUtils.setField(provider, "pushProvider", "none");
        ReflectionTestUtils.setField(provider, "ttlSeconds", 86400);
    }

    @Test
    @DisplayName("When push provider is 'none' — returns SKIPPED (no mock-success)")
    void whenProviderNone_returnsSkipped() {
        PushProvider.PushSendResult result = provider.send(
                "token123", "Title", "Body", new HashMap<>());

        assertTrue(result.isSkipped());
        assertNull(result.providerMessageId());
        assertFalse(result.isSuccess());
        assertFalse(result.isInvalidToken());
        assertFalse(result.isTransientFailure());
    }

    @Test
    @DisplayName("SKIPPED result carries reason message")
    void skippedResult_carriesReason() {
        PushProvider.PushSendResult result = provider.send(
                "token123", "Title", "Body", null);

        assertTrue(result.isSkipped());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("disabled"));
    }

    @Test
    @DisplayName("PushSendResult SUCCESS carries provider message ID")
    void successResult_carriesMessageId() {
        PushProvider.PushSendResult result = PushProvider.PushSendResult.success("msg-123");

        assertTrue(result.isSuccess());
        assertEquals("msg-123", result.providerMessageId());
        assertNull(result.errorCode());
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("PushSendResult INVALID_TOKEN carries error code and message")
    void invalidTokenResult_carriesErrorCode() {
        PushProvider.PushSendResult result = PushProvider.PushSendResult.invalidToken(
                "UNREGISTERED", "Token not registered");

        assertTrue(result.isInvalidToken());
        assertEquals("UNREGISTERED", result.errorCode());
        assertEquals("Token not registered", result.errorMessage());
        assertNull(result.providerMessageId());
    }

    @Test
    @DisplayName("PushSendResult TRANSIENT_FAILURE carries error code and message")
    void transientFailureResult_carriesErrorCode() {
        PushProvider.PushSendResult result = PushProvider.PushSendResult.transientFailure(
                "UNAVAILABLE", "FCM temporarily unavailable");

        assertTrue(result.isTransientFailure());
        assertEquals("UNAVAILABLE", result.errorCode());
        assertEquals("FCM temporarily unavailable", result.errorMessage());
        assertNull(result.providerMessageId());
    }

    @Test
    @DisplayName("PushSendResult SKIPPED carries reason")
    void skippedResult_carriesReasonMessage() {
        PushProvider.PushSendResult result = PushProvider.PushSendResult.skipped("No token");

        assertTrue(result.isSkipped());
        assertEquals("No token", result.errorMessage());
    }

    @Test
    @DisplayName("PushProvider Status enum has all required values")
    void statusEnum_hasAllValues() {
        PushProvider.Status[] statuses = PushProvider.Status.values();
        assertEquals(4, statuses.length);
        // Verify all expected statuses exist
        boolean hasSuccess = false, hasInvalid = false, hasTransient = false, hasSkipped = false;
        for (PushProvider.Status s : statuses) {
            if (s == PushProvider.Status.SUCCESS) hasSuccess = true;
            if (s == PushProvider.Status.INVALID_TOKEN) hasInvalid = true;
            if (s == PushProvider.Status.TRANSIENT_FAILURE) hasTransient = true;
            if (s == PushProvider.Status.SKIPPED) hasSkipped = true;
        }
        assertTrue(hasSuccess);
        assertTrue(hasInvalid);
        assertTrue(hasTransient);
        assertTrue(hasSkipped);
    }

    @Test
    @DisplayName("No production mock-success fallback — SKIPPED is not SUCCESS")
    void skippedIsNotSuccess() {
        PushProvider.PushSendResult result = provider.send(
                "token123", "Title", "Body", Map.of("key", "value"));

        // Must NOT return success when provider is disabled
        assertFalse(result.isSuccess());
        assertTrue(result.isSkipped());
    }

    // ------------------------------------------------------------------
    // SIG 26: Tests with mocked FirebaseMessaging.getInstance().send()
    // ------------------------------------------------------------------

    /**
     * Helper: enables the FCM provider on the test instance so that the
     * real send path (not the "none" early-return) is exercised.
     */
    private void enableFcmProvider() {
        ReflectionTestUtils.setField(provider, "pushProvider", "fcm");
    }

    @Test
    @DisplayName("FCM success response — returns PushSendResult.success(messageId)")
    void fcmSend_success_returnsSuccessResult() throws Exception {
        enableFcmProvider();

        try (MockedStatic<FirebaseMessaging> mocked = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockFcm = mock(FirebaseMessaging.class);
            mocked.when(FirebaseMessaging::getInstance).thenReturn(mockFcm);
            when(mockFcm.send(any(Message.class))).thenReturn("projects/x/messages/12345");

            PushProvider.PushSendResult result = provider.send(
                    "valid-token", "Alert", "Body text", new HashMap<>());

            assertTrue(result.isSuccess());
            assertEquals("projects/x/messages/12345", result.providerMessageId());
            assertNull(result.errorCode());
            assertNull(result.errorMessage());
            verify(mockFcm).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("FCM UNREGISTERED error — returns PushSendResult.invalidToken")
    void fcmSend_unregisteredError_returnsInvalidToken() throws Exception {
        enableFcmProvider();

        try (MockedStatic<FirebaseMessaging> mocked = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockFcm = mock(FirebaseMessaging.class);
            mocked.when(FirebaseMessaging::getInstance).thenReturn(mockFcm);

            FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
            when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
            when(mockException.getMessage()).thenReturn("Requested entity was not found");
            when(mockFcm.send(any(Message.class))).thenThrow(mockException);

            PushProvider.PushSendResult result = provider.send(
                    "stale-token", "Alert", "Body text", new HashMap<>());

            assertTrue(result.isInvalidToken());
            assertEquals("UNREGISTERED", result.errorCode());
            assertNotNull(result.errorMessage());
            assertNull(result.providerMessageId());
        }
    }

    @Test
    @DisplayName("FCM QUOTA_EXCEEDED error — returns PushSendResult.transientFailure")
    void fcmSend_quotaExceededError_returnsTransientFailure() throws Exception {
        enableFcmProvider();

        try (MockedStatic<FirebaseMessaging> mocked = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockFcm = mock(FirebaseMessaging.class);
            mocked.when(FirebaseMessaging::getInstance).thenReturn(mockFcm);

            FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
            when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.QUOTA_EXCEEDED);
            when(mockException.getMessage()).thenReturn("Quota exceeded for the project");
            when(mockFcm.send(any(Message.class))).thenThrow(mockException);

            PushProvider.PushSendResult result = provider.send(
                    "valid-token", "Alert", "Body text", new HashMap<>());

            assertTrue(result.isTransientFailure());
            assertEquals("QUOTA_EXCEEDED", result.errorCode());
            assertNotNull(result.errorMessage());
            assertNull(result.providerMessageId());
        }
    }

    @Test
    @DisplayName("FCM UNAVAILABLE error — returns PushSendResult.transientFailure")
    void fcmSend_unavailableError_returnsTransientFailure() throws Exception {
        enableFcmProvider();

        try (MockedStatic<FirebaseMessaging> mocked = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockFcm = mock(FirebaseMessaging.class);
            mocked.when(FirebaseMessaging::getInstance).thenReturn(mockFcm);

            FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
            when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
            when(mockException.getMessage()).thenReturn("FCM service temporarily unavailable");
            when(mockFcm.send(any(Message.class))).thenThrow(mockException);

            PushProvider.PushSendResult result = provider.send(
                    "valid-token", "Alert", "Body text", new HashMap<>());

            assertTrue(result.isTransientFailure());
            assertEquals("UNAVAILABLE", result.errorCode());
            assertNotNull(result.errorMessage());
            assertNull(result.providerMessageId());
        }
    }
}
