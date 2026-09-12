package com.yantrago.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NotificationFeatureSwitches (Phase 6).
 *
 * Per notification plan N11:
 * - "Feature switches separately control each event family and push dispatch."
 * - "Disabling push preserves inbox and incident processing."
 * - "Avoid building an unrelated feature-flag service."
 */
class NotificationFeatureSwitchesTest {

    @Test
    @DisplayName("Default: push enabled, all event families enabled")
    void defaults_allEnabled() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "");

        assertTrue(switches.isPushEnabled());
        assertTrue(switches.isEventFamilyEnabled("LOW_BATTERY"));
        assertTrue(switches.isEventFamilyEnabled("DEVICE_OFFLINE"));
        assertTrue(switches.isEventFamilyEnabled("UNKNOWN_TYPE"));
    }

    @Test
    @DisplayName("Push kill switch: pushEnabled=false disables push only")
    void pushKillSwitch_disablesPushOnly() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(false, "");

        assertFalse(switches.isPushEnabled());
        // Event families are still enabled — inbox and incident processing continue
        assertTrue(switches.isEventFamilyEnabled("LOW_BATTERY"));
    }

    @Test
    @DisplayName("Event family filter: only listed families are enabled")
    void eventFamilyFilter_onlyListedFamiliesEnabled() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "LOW_BATTERY,DEVICE_OFFLINE");

        assertTrue(switches.isEventFamilyEnabled("LOW_BATTERY"));
        assertTrue(switches.isEventFamilyEnabled("DEVICE_OFFLINE"));
        assertFalse(switches.isEventFamilyEnabled("VOLTAGE_DROP"));
        assertFalse(switches.isEventFamilyEnabled("SIM_EXPIRY"));
    }

    @Test
    @DisplayName("Event family filter: case-insensitive")
    void eventFamilyFilter_caseInsensitive() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "low_battery");

        assertTrue(switches.isEventFamilyEnabled("LOW_BATTERY"));
        assertTrue(switches.isEventFamilyEnabled("low_battery"));
    }

    @Test
    @DisplayName("Wildcard '*' enables all families")
    void wildcardEnablesAll() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "*");

        assertTrue(switches.isEventFamilyEnabled("LOW_BATTERY"));
        assertTrue(switches.isEventFamilyEnabled("UNKNOWN_TYPE"));
    }

    @Test
    @DisplayName("Null alertType is treated as enabled")
    void nullAlertType_isEnabled() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "LOW_BATTERY");

        assertTrue(switches.isEventFamilyEnabled(null));
    }

    @Test
    @DisplayName("getEnabledFamilies returns all when wildcard")
    void getEnabledFamilies_wildcard() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "*");

        assertEquals(9, switches.getEnabledFamilies().size());
        assertTrue(switches.getEnabledFamilies().contains("LOW_BATTERY"));
        assertTrue(switches.getEnabledFamilies().contains("EXTERNAL_POWER_LOW"));
        assertTrue(switches.getEnabledFamilies().contains("INTERNAL_BATTERY_LOW"));
    }

    @Test
    @DisplayName("Alarm-code-driven alert types are enabled by default")
    void alarmCodeTypes_enabledByDefault() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "");

        assertTrue(switches.isEventFamilyEnabled("EXTERNAL_POWER_LOW"));
        assertTrue(switches.isEventFamilyEnabled("EXTERNAL_POWER_CUT"));
        assertTrue(switches.isEventFamilyEnabled("LOW_POWER_SHUTDOWN"));
        assertTrue(switches.isEventFamilyEnabled("INTERNAL_BATTERY_LOW"));
    }

    @Test
    @DisplayName("Alarm-code-driven alert types can be filtered")
    void alarmCodeTypes_canBeFiltered() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "EXTERNAL_POWER_LOW,INTERNAL_BATTERY_LOW");

        assertTrue(switches.isEventFamilyEnabled("EXTERNAL_POWER_LOW"));
        assertTrue(switches.isEventFamilyEnabled("INTERNAL_BATTERY_LOW"));
        assertFalse(switches.isEventFamilyEnabled("EXTERNAL_POWER_CUT"));
        assertFalse(switches.isEventFamilyEnabled("LOW_POWER_SHUTDOWN"));
        assertFalse(switches.isEventFamilyEnabled("LOW_BATTERY"));
    }

    @Test
    @DisplayName("getEnabledFamilies returns only listed when filtered")
    void getEnabledFamilies_filtered() {
        NotificationFeatureSwitches switches = new NotificationFeatureSwitches(true, "LOW_BATTERY,DEVICE_OFFLINE");

        assertEquals(2, switches.getEnabledFamilies().size());
        assertTrue(switches.getEnabledFamilies().contains("LOW_BATTERY"));
        assertTrue(switches.getEnabledFamilies().contains("DEVICE_OFFLINE"));
    }
}
