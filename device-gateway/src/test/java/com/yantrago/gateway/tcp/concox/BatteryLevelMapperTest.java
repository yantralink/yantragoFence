package com.yantrago.gateway.tcp.concox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BatteryLevelMapper.
 *
 * Verifies the BR05 protocol's 7-level voltage enum is correctly mapped
 * to an approximate battery percentage, and the GSM signal level byte
 * is correctly mapped to a numeric signal strength.
 */
class BatteryLevelMapperTest {

    @Test
    @DisplayName("toPercentage should return 0 for voltage level 0x00 (no power)")
    void toPercentage_noPower() {
        assertEquals(0, BatteryLevelMapper.toPercentage(0x00));
    }

    @Test
    @DisplayName("toPercentage should return 10 for voltage level 0x01 (extremely low)")
    void toPercentage_extremelyLow() {
        assertEquals(10, BatteryLevelMapper.toPercentage(0x01));
    }

    @Test
    @DisplayName("toPercentage should return 20 for voltage level 0x02 (very low / alarm)")
    void toPercentage_veryLow() {
        assertEquals(20, BatteryLevelMapper.toPercentage(0x02));
    }

    @Test
    @DisplayName("toPercentage should return 40 for voltage level 0x03 (low / usable)")
    void toPercentage_lowUsable() {
        assertEquals(40, BatteryLevelMapper.toPercentage(0x03));
    }

    @Test
    @DisplayName("toPercentage should return 60 for voltage level 0x04 (normal)")
    void toPercentage_normal() {
        assertEquals(60, BatteryLevelMapper.toPercentage(0x04));
    }

    @Test
    @DisplayName("toPercentage should return 80 for voltage level 0x05 (high)")
    void toPercentage_high() {
        assertEquals(80, BatteryLevelMapper.toPercentage(0x05));
    }

    @Test
    @DisplayName("toPercentage should return 100 for voltage level 0x06 (extremely high)")
    void toPercentage_extremelyHigh() {
        assertEquals(100, BatteryLevelMapper.toPercentage(0x06));
    }

    @Test
    @DisplayName("toPercentage should return null for invalid voltage level (0x07)")
    void toPercentage_invalid() {
        assertNull(BatteryLevelMapper.toPercentage(0x07));
    }

    @Test
    @DisplayName("toPercentage should return null for invalid voltage level (0xFF)")
    void toPercentage_invalidHigh() {
        assertNull(BatteryLevelMapper.toPercentage(0xFF));
    }

    @Test
    @DisplayName("toGsmSignal should return 0 for no signal (0x00)")
    void toGsmSignal_noSignal() {
        assertEquals(0, BatteryLevelMapper.toGsmSignal(0x00));
    }

    @Test
    @DisplayName("toGsmSignal should return 1 for extremely weak signal (0x01)")
    void toGsmSignal_extremelyWeak() {
        assertEquals(1, BatteryLevelMapper.toGsmSignal(0x01));
    }

    @Test
    @DisplayName("toGsmSignal should return 2 for weak signal (0x02)")
    void toGsmSignal_weak() {
        assertEquals(2, BatteryLevelMapper.toGsmSignal(0x02));
    }

    @Test
    @DisplayName("toGsmSignal should return 3 for good signal (0x03)")
    void toGsmSignal_good() {
        assertEquals(3, BatteryLevelMapper.toGsmSignal(0x03));
    }

    @Test
    @DisplayName("toGsmSignal should return 4 for strong signal (0x04)")
    void toGsmSignal_strong() {
        assertEquals(4, BatteryLevelMapper.toGsmSignal(0x04));
    }

    @Test
    @DisplayName("toGsmSignal should return null for invalid signal level (0x05)")
    void toGsmSignal_invalid() {
        assertNull(BatteryLevelMapper.toGsmSignal(0x05));
    }

    @Test
    @DisplayName("toGsmSignal should return null for invalid signal level (0xFF)")
    void toGsmSignal_invalidHigh() {
        assertNull(BatteryLevelMapper.toGsmSignal(0xFF));
    }
}
