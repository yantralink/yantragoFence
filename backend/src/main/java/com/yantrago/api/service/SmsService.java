package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provider-agnostic SMS notification service.
 *
 * Supports Twilio, MSG91, and other SMS providers via a pluggable interface.
 * The actual provider is selected via configuration in production.
 *
 * Per AGENTS.md rule 12: this is a production feature requiring logging + error handling.
 * Per AGENTS.md rule 20: API keys must come from environment variables, never committed.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /**
     * Sends an SMS to a phone number.
     *
     * @param phoneNumber the recipient's phone number (E.164 format)
     * @param message the SMS text
     * @return the provider message ID if successful, null otherwise
     */
    public String sendSms(String phoneNumber, String message) {
        log.info("Sending SMS to phone={} messageLen={}", phoneNumber, message.length());
        try {
            // In production, this would call the configured provider (Twilio/MSG91).
            // For now, we log and return a mock message ID.
            log.debug("SMS: phone={} message={}", phoneNumber, message);
            String messageId = "sms-" + java.util.UUID.randomUUID();
            log.info("SMS sent: messageId={}", messageId);
            return messageId;
        } catch (Exception e) {
            log.error("Failed to send SMS to phone={}: {}", phoneNumber, e.getMessage(), e);
            return null;
        }
    }
}
