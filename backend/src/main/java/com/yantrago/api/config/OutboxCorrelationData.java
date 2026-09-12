package com.yantrago.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Correlation data for outbox publisher confirms.
 *
 * Tracks whether a published outbox row was acknowledged by the broker.
 * The OutboxPublisher uses this to decide whether to mark the row as
 * published or schedule a retry.
 *
 * Per notification plan N5: "mark publication complete only after confirmation
 * with no routing return."
 */
public class OutboxCorrelationData extends CorrelationData {

    private static final Logger log = LoggerFactory.getLogger(OutboxCorrelationData.class);

    private final UUID outboxId;
    private final CompletableFuture<Boolean> confirmFuture = new CompletableFuture<>();

    public OutboxCorrelationData(UUID outboxId) {
        super(outboxId.toString());
        this.outboxId = outboxId;
    }

    public UUID getOutboxId() {
        return outboxId;
    }

    /**
     * Called by the ConfirmCallback when the broker acknowledges the message.
     */
    public void markPublished() {
        log.debug("Outbox row {} confirmed by broker (ACK)", outboxId);
        confirmFuture.complete(true);
    }

    /**
     * Called by the ConfirmCallback when the broker rejects the message.
     */
    public void markNacked(String cause) {
        log.warn("Outbox row {} rejected by broker (NACK): {}", outboxId, cause);
        confirmFuture.complete(false);
    }

    /**
     * Waits for the broker confirm with a timeout.
     * Returns true if acknowledged, false if rejected or timed out.
     */
    public boolean awaitConfirm(long timeoutSeconds) {
        try {
            return confirmFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Outbox row {} confirm timed out or failed: {}", outboxId, e.getMessage());
            return false;
        }
    }
}
