package com.yantrago.shared.queue;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Message published by the device gateway back to the backend reporting
 * the lifecycle state of a previously sent command.
 *
 * Status transitions:
 *   PENDING -> QUEUED -> SENT -> ACK -> DONE
 *                              \-> FAILED
 */
public class CommandResultMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACK = "ACK";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    private UUID commandId;
    private String status;
    private int attemptCount;
    private String error;
    private Instant timestamp;

    public CommandResultMessage() {
    }

    public CommandResultMessage(UUID commandId, String status, int attemptCount, String error, Instant timestamp) {
        this.commandId = commandId;
        this.status = status;
        this.attemptCount = attemptCount;
        this.error = error;
        this.timestamp = timestamp;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public void setCommandId(UUID commandId) {
        this.commandId = commandId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
