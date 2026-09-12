package com.yantrago.gateway.tcp.concox;

/**
 * Maps the BR05 protocol's 7-level voltage enum to a battery percentage.
 *
 * The BR05 protocol does NOT provide a numeric battery percentage.
 * It provides a coarse 7-level enum (0x00–0x06) representing the
 * internal backup battery state. This mapper converts that enum to
 * an approximate percentage for display and alert purposes.
 *
 * Per AGENTS.md rule 13: protocol parsing follows the BR05 spec as-is.
 * This mapping is an application-level interpretation, not protocol parsing.
 */
public final class BatteryLevelMapper {

    private BatteryLevelMapper() {}

    /**
     * Maps the BR05 voltage level byte to an approximate battery percentage.
     *
     * @param voltageLevel the raw byte value (0x00–0x06)
     * @return battery percentage (0–100), or null if the value is invalid
     */
    public static Integer toPercentage(int voltageLevel) {
        return switch (voltageLevel) {
            case 0x00 -> 0;   // No power (power off)
            case 0x01 -> 10;  // Extremely low
            case 0x02 -> 20;  // Very low (low battery alarm)
            case 0x03 -> 40;  // Low battery (usable)
            case 0x04 -> 60;  // Normal
            case 0x05 -> 80;  // High
            case 0x06 -> 100; // Extremely high
            default -> null;   // Invalid value
        };
    }

    /**
     * Maps the BR05 GSM signal level byte to a numeric signal strength.
     *
     * @param gsmLevel the raw byte value (0x00–0x04)
     * @return signal strength (0–4), or null if invalid
     */
    public static Integer toGsmSignal(int gsmLevel) {
        if (gsmLevel >= 0x00 && gsmLevel <= 0x04) {
            return gsmLevel;
        }
        return null;
    }
}
