package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Feature switches for notification event families and push dispatch.
 *
 * Per notification plan Phase 6 / N11:
 * - "Feature switches separately control each event family and push dispatch."
 * - "Disabling push preserves inbox and incident processing."
 * - "Avoid building an unrelated feature-flag service."
 *
 * This is a lightweight config-based switch service, NOT a separate feature-flag service.
 * Switches are read from application.yml / environment variables at startup.
 *
 * Kill switches available:
 * - notification.push.enabled: global push kill switch (preserves inbox)
 * - notification.event-families.enabled: comma-separated list of enabled event families
 *   (e.g. "LOW_BATTERY,VOLTAGE_DROP,GSM_SIGNAL_LOW,DEVICE_OFFLINE,SIM_EXPIRY")
 *   An empty/all list means all families are enabled.
 *
 * Per AGENTS.md rule 12: production feature with logging.
 */
@Service
public class NotificationFeatureSwitches {

    private static final Logger log = LoggerFactory.getLogger(NotificationFeatureSwitches.class);

    private final boolean pushEnabled;
    private final Set<String> enabledEventFamilies;
    private final boolean allFamiliesEnabled;

    // All known event families.
    // Phase 6: added alarm-code-driven types (EXTERNAL_POWER_LOW, EXTERNAL_POWER_CUT,
    // LOW_POWER_SHUTDOWN, INTERNAL_BATTERY_LOW) from BR05 alarm codes 0x0E/0x0F/0x15/0x19.
    // Command notifications: MACHINE_ON, MACHINE_OFF, COMMAND_ACK, COMMAND_FAILED
    // for relay ON/OFF command lifecycle events.
    private static final Set<String> ALL_FAMILIES = Set.of(
            "LOW_BATTERY", "VOLTAGE_DROP", "GSM_SIGNAL_LOW",
            "DEVICE_OFFLINE", "SIM_EXPIRY",
            "EXTERNAL_POWER_LOW", "EXTERNAL_POWER_CUT",
            "LOW_POWER_SHUTDOWN", "INTERNAL_BATTERY_LOW",
            "MACHINE_ON", "MACHINE_OFF", "COMMAND_ACK", "COMMAND_FAILED"
    );

    public NotificationFeatureSwitches(
            @Value("${notification.push.enabled:false}") boolean pushEnabled,
            @Value("${notification.event-families.enabled:}") String enabledFamilies) {
        this.pushEnabled = pushEnabled;

        if (enabledFamilies == null || enabledFamilies.isBlank() || "*".equals(enabledFamilies.trim())) {
            this.allFamiliesEnabled = true;
            this.enabledEventFamilies = Collections.emptySet();
        } else {
            this.allFamiliesEnabled = false;
            this.enabledEventFamilies = new HashSet<>(Arrays.asList(
                    enabledFamilies.toUpperCase().split("\\s*,\\s*")));
        }

        log.info("Notification feature switches: pushEnabled={} allFamiliesEnabled={} enabledFamilies={}",
                pushEnabled, allFamiliesEnabled,
                allFamiliesEnabled ? "ALL" : enabledEventFamilies);
    }

    /**
     * Returns true if push delivery is globally enabled.
     * When false, inbox delivery and incident processing continue normally.
     */
    public boolean isPushEnabled() {
        return pushEnabled;
    }

    /**
     * Returns true if the given event family is enabled for notification dispatch.
     * When false, alerts of this type are still processed for incident lifecycle
     * but no inbox items or push notifications are created.
     */
    public boolean isEventFamilyEnabled(String alertType) {
        if (alertType == null) return true;
        if (allFamiliesEnabled) return true;
        return enabledEventFamilies.contains(alertType.toUpperCase());
    }

    /**
     * Returns the set of all known event families.
     */
    public Set<String> getAllFamilies() {
        return ALL_FAMILIES;
    }

    /**
     * Returns the set of currently enabled event families.
     */
    public Set<String> getEnabledFamilies() {
        return allFamiliesEnabled ? ALL_FAMILIES : enabledEventFamilies;
    }
}
