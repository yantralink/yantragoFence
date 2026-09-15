package com.yantrago.api.service;

import com.yantrago.api.service.CommandStateMachine.CommandState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommandStateMachine.
 *
 * Verifies valid and invalid transitions, terminal states, and TIMEOUT handling.
 */
class CommandStateMachineTest {

    private final CommandStateMachine stateMachine = new CommandStateMachine();

    // --- Valid transitions ---

    @Test
    @DisplayName("PENDING → QUEUED is valid")
    void pendingToQueuedIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.PENDING, CommandState.QUEUED));
    }

    @Test
    @DisplayName("PENDING → SENT is valid")
    void pendingToSentIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.PENDING, CommandState.SENT));
    }

    @Test
    @DisplayName("PENDING → FAILED is valid")
    void pendingToFailedIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.PENDING, CommandState.FAILED));
    }

    @Test
    @DisplayName("PENDING → ACK is valid (device can ACK before SENT result is processed)")
    void pendingToAckIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.PENDING, CommandState.ACK));
    }

    @Test
    @DisplayName("QUEUED → SENT is valid")
    void queuedToSentIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.QUEUED, CommandState.SENT));
    }

    @Test
    @DisplayName("QUEUED → FAILED is valid")
    void queuedToFailedIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.QUEUED, CommandState.FAILED));
    }

    @Test
    @DisplayName("SENT → ACK is valid")
    void sentToAckIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.SENT, CommandState.ACK));
    }

    @Test
    @DisplayName("SENT → FAILED is valid")
    void sentToFailedIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.SENT, CommandState.FAILED));
    }

    @Test
    @DisplayName("ACK → DONE is valid")
    void ackToDoneIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.ACK, CommandState.DONE));
    }

    @Test
    @DisplayName("ACK → FAILED is valid")
    void ackToFailedIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.ACK, CommandState.FAILED));
    }

    // --- TIMEOUT transitions ---

    @Test
    @DisplayName("QUEUED → TIMEOUT is valid")
    void queuedToTimeoutIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.QUEUED, CommandState.TIMEOUT));
    }

    @Test
    @DisplayName("SENT → TIMEOUT is valid")
    void sentToTimeoutIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.SENT, CommandState.TIMEOUT));
    }

    @Test
    @DisplayName("ACK → TIMEOUT is valid")
    void ackToTimeoutIsValid() {
        assertDoesNotThrow(() ->
                stateMachine.validateTransition(CommandState.ACK, CommandState.TIMEOUT));
    }

    @Test
    @DisplayName("PENDING → TIMEOUT is NOT valid")
    void pendingToTimeoutIsInvalid() {
        assertThrows(IllegalStateException.class, () ->
                stateMachine.validateTransition(CommandState.PENDING, CommandState.TIMEOUT));
    }

    // --- Invalid transitions ---

    @Test
    @DisplayName("DONE → PENDING is invalid (terminal state)")
    void doneToPendingIsInvalid() {
        assertThrows(IllegalStateException.class, () ->
                stateMachine.validateTransition(CommandState.DONE, CommandState.PENDING));
    }

    @Test
    @DisplayName("FAILED → ACK is invalid (terminal state)")
    void failedToAckIsInvalid() {
        assertThrows(IllegalStateException.class, () ->
                stateMachine.validateTransition(CommandState.FAILED, CommandState.ACK));
    }

    @Test
    @DisplayName("TIMEOUT → DONE is invalid (terminal state)")
    void timeoutToDoneIsInvalid() {
        assertThrows(IllegalStateException.class, () ->
                stateMachine.validateTransition(CommandState.TIMEOUT, CommandState.DONE));
    }

    @Test
    @DisplayName("TIMEOUT → FAILED is invalid (terminal state)")
    void timeoutToFailedIsInvalid() {
        assertThrows(IllegalStateException.class, () ->
                stateMachine.validateTransition(CommandState.TIMEOUT, CommandState.FAILED));
    }

    @Test
    @DisplayName("ACK → PENDING is invalid (reverse transition)")
    void ackToPendingIsInvalid() {
        assertThrows(IllegalStateException.class, () ->
                stateMachine.validateTransition(CommandState.ACK, CommandState.PENDING));
    }

    // --- Terminal states ---

    @Test
    @DisplayName("DONE is terminal")
    void doneIsTerminal() {
        assertTrue(stateMachine.isTerminal(CommandState.DONE));
    }

    @Test
    @DisplayName("FAILED is terminal")
    void failedIsTerminal() {
        assertTrue(stateMachine.isTerminal(CommandState.FAILED));
    }

    @Test
    @DisplayName("TIMEOUT is terminal")
    void timeoutIsTerminal() {
        assertTrue(stateMachine.isTerminal(CommandState.TIMEOUT));
    }

    @Test
    @DisplayName("PENDING is not terminal")
    void pendingIsNotTerminal() {
        assertFalse(stateMachine.isTerminal(CommandState.PENDING));
    }

    @Test
    @DisplayName("SENT is not terminal")
    void sentIsNotTerminal() {
        assertFalse(stateMachine.isTerminal(CommandState.SENT));
    }

    @Test
    @DisplayName("ACK is not terminal")
    void ackIsNotTerminal() {
        assertFalse(stateMachine.isTerminal(CommandState.ACK));
    }

    // --- canTransition ---

    @Test
    @DisplayName("canTransition returns true for valid transitions")
    void canTransitionReturnsTrueForValid() {
        assertTrue(stateMachine.canTransition(CommandState.SENT, CommandState.TIMEOUT));
        assertTrue(stateMachine.canTransition(CommandState.SENT, CommandState.ACK));
    }

    @Test
    @DisplayName("canTransition returns false for invalid transitions")
    void canTransitionReturnsFalseForInvalid() {
        assertFalse(stateMachine.canTransition(CommandState.DONE, CommandState.SENT));
        assertFalse(stateMachine.canTransition(CommandState.TIMEOUT, CommandState.ACK));
    }

    // --- fromString ---

    @Test
    @DisplayName("fromString parses valid states")
    void fromStringParsesValidStates() {
        assertEquals(CommandState.PENDING, stateMachine.fromString("PENDING"));
        assertEquals(CommandState.QUEUED, stateMachine.fromString("QUEUED"));
        assertEquals(CommandState.SENT, stateMachine.fromString("SENT"));
        assertEquals(CommandState.ACK, stateMachine.fromString("ACK"));
        assertEquals(CommandState.DONE, stateMachine.fromString("DONE"));
        assertEquals(CommandState.FAILED, stateMachine.fromString("FAILED"));
        assertEquals(CommandState.TIMEOUT, stateMachine.fromString("TIMEOUT"));
    }

    @Test
    @DisplayName("fromString parses lowercase states")
    void fromStringParsesLowercase() {
        assertEquals(CommandState.TIMEOUT, stateMachine.fromString("timeout"));
        assertEquals(CommandState.DONE, stateMachine.fromString("done"));
    }

    @Test
    @DisplayName("fromString throws for unknown state")
    void fromStringThrowsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> stateMachine.fromString("UNKNOWN"));
    }
}
