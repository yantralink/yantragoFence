package com.yantrago.api.queue;

import com.yantrago.api.service.CanonicalAlertService;
import com.yantrago.shared.queue.AlertEventMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes AlertEventMessage from the gateway (e.g. SOS, low battery, offline)
 * and routes all persistence through the CanonicalAlertService.
 *
 * Per AGENTS.md rule 17: uses shared module message contracts.
 * Per notification plan N2: only one logical handler on ALERT_EVENT_QUEUE.
 * The unrelated notification listener has been removed from this queue.
 *
 * Phase 1 fix: source-event idempotency — uses processed_events to guard
 * against duplicate broker deliveries of the same gateway alert event.
 */
@Component
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final CanonicalAlertService canonicalAlertService;
    private final JdbcTemplate jdbcTemplate;

    public AlertConsumer(CanonicalAlertService canonicalAlertService,
                         JdbcTemplate jdbcTemplate) {
        this.canonicalAlertService = canonicalAlertService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @RabbitListener(queues = QueueNames.ALERT_EVENT_QUEUE)
    public void handleAlertEvent(AlertEventMessage message) {
        UUID sourceEventId = message.getAlertId();
        log.info("Received alert event: alertId={} machineId={} type={} severity={}",
                sourceEventId, message.getMachineId(),
                message.getAlertType(), message.getSeverity());

        if (sourceEventId == null) {
            log.warn("Alert event has no alertId — cannot enforce source-event idempotency. " +
                    "Processing without deduplication.");
            sourceEventId = UUID.randomUUID();
        }

        // Phase 1 fix: source-event idempotency via processed_events.
        // INSERT ... ON CONFLICT DO NOTHING — if 0 rows inserted, this is a redelivery.
        int inserted = jdbcTemplate.update(
                "INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                sourceEventId
        );

        if (inserted == 0) {
            log.info("Skipping duplicate alert event (already processed): alertId={}", sourceEventId);
            return;
        }

        try {
            canonicalAlertService.processAlertEvent(
                    message.getMachineId(),
                    message.getAlertType(),
                    message.getSeverity(),
                    message.getMessage(),
                    message.getTimestamp(),
                    null, // observedValue — not in current AlertEventMessage contract
                    null, // observedUnit
                    sourceEventId // Phase 1 fix: pass source event ID for correlation
            );
        } catch (Exception e) {
            log.error("Failed to process alert event for machineId={} alertId={}: {}",
                    message.getMachineId(), sourceEventId, e.getMessage(), e);
            // Do not catch-and-log silently — let the exception propagate for RabbitMQ retry
            throw e;
        }
    }
}
