package com.yantrago.api.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Legacy notification consumer — DISABLED in Phase 1.
 *
 * Previously this consumer competed with AlertConsumer on ALERT_EVENT_QUEUE,
 * causing unreliable message distribution. It used a random organization UUID
 * and referenced an alert ID that AlertConsumer did not preserve, leading to
 * foreign-key failures and lost processing.
 *
 * The notification pipeline now uses a dedicated NOTIFICATION_QUEUE consumed
 * by {@link NotificationEventConsumer}, fed by the outbox publisher.
 *
 * This class is retained as a disabled placeholder to document the change.
 * It will be removed in a later phase once the full notification pipeline
 * (inbox, preferences, delivery) is verified.
 *
 * Per notification plan N2: remove the unrelated notification listener from
 * raw alert ingestion; route all raw alert persistence through the canonical
 * service.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    public NotificationConsumer() {
        log.info("NotificationConsumer disabled — notification pipeline now uses NOTIFICATION_QUEUE via outbox");
    }
}
