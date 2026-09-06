package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Command state machine: PENDING → QUEUED → SENT → ACK → DONE (or FAILED).
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 * Per AGENTS.md rule 6: all commands must be auditable via machine_commands + command_attempts.
 *
 * Valid transitions:
 *   PENDING → QUEUED, FAILED
 *   QUEUED  → SENT, FAILED
 *   SENT    → ACK, FAILED, TIMEOUT (→ FAILED)
 *   ACK     → DONE, FAILED
 *   DONE    → (terminal)
 *   FAILED  → (terminal)
 */
@Component
public class CommandStateMachine {

    private static final Logger log = LoggerFactory.getLogger(CommandStateMachine.class);

    public enum CommandState {
        PENDING, QUEUED, SENT, ACK, DONE, FAILED
    }

    private static final Map<CommandState, Set<CommandState>> TRANSITIONS;

    static {
        TRANSITIONS = new EnumMap<>(CommandState.class);
        TRANSITIONS.put(CommandState.PENDING, EnumSet.of(CommandState.QUEUED, CommandState.FAILED));
        TRANSITIONS.put(CommandState.QUEUED, EnumSet.of(CommandState.SENT, CommandState.FAILED));
        TRANSITIONS.put(CommandState.SENT, EnumSet.of(CommandState.ACK, CommandState.FAILED));
        TRANSITIONS.put(CommandState.ACK, EnumSet.of(CommandState.DONE, CommandState.FAILED));
        TRANSITIONS.put(CommandState.DONE, EnumSet.noneOf(CommandState.class));
        TRANSITIONS.put(CommandState.FAILED, EnumSet.noneOf(CommandState.class));
    }

    /**
     * Validates that the transition from current state to target state is allowed.
     * @throws IllegalStateException if the transition is invalid
     */
    public void validateTransition(CommandState current, CommandState target) {
        Set<CommandState> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalStateException(
                    "Invalid command state transition: " + current + " → " + target);
        }
        log.debug("Command state transition: {} → {}", current, target);
    }

    /**
     * Returns true if the transition is valid.
     */
    public boolean canTransition(CommandState current, CommandState target) {
        Set<CommandState> allowed = TRANSITIONS.get(current);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Returns true if the state is terminal (no further transitions).
     */
    public boolean isTerminal(CommandState state) {
        return state == CommandState.DONE || state == CommandState.FAILED;
    }

    /**
     * Parses a string status into a CommandState enum.
     * @throws IllegalArgumentException if the status is not a valid state
     */
    public CommandState fromString(String status) {
        try {
            return CommandState.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown command state: " + status);
        }
    }
}
