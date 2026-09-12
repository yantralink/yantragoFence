package com.yantrago.api.service;

import com.yantrago.api.dto.notification.EventCatalogDto;
import com.yantrago.api.dto.notification.NotificationMapper;
import com.yantrago.api.dto.notification.NotificationPreferenceDto;
import com.yantrago.api.model.NotificationPreference;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Notification preference service — manages per-user channel preferences.
 *
 * Per notification plan Phase 3:
 * - Deterministic preference uniqueness (V24 migration fixes NULL alert_type issue)
 * - Per-user preferences (users can only modify their own)
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: RBAC on all operations.
 * Per AGENTS.md rule 21: returns DTOs, never JPA entities.
 */
@Service
public class NotificationPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferenceService.class);

    private final EntityManager entityManager;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final JdbcTemplate jdbcTemplate;

    public NotificationPreferenceService(EntityManager entityManager,
                                          OwnerContextService ownerContextService,
                                          TenantGuard tenantGuard,
                                          PermissionEvaluator permissionEvaluator,
                                          JdbcTemplate jdbcTemplate) {
        this.entityManager = entityManager;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gets the current user's notification preferences.
     */
    @Transactional(readOnly = true)
    public List<NotificationPreferenceDto> getMyPreferences() {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Not authenticated");
        }
        return findPreferences(orgId, userId).stream()
                .map(NotificationMapper::toDto)
                .toList();
    }

    /**
     * Gets a specific user's preferences. Only the user themselves or super_admin can access.
     */
    @Transactional(readOnly = true)
    public List<NotificationPreferenceDto> getUserPreferences(UUID userId) {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID currentUserId = permissionEvaluator.getCurrentUserId();

        // Users can only view their own preferences (or super_admin)
        if (currentUserId == null || (!currentUserId.equals(userId) && !isSuperAdmin())) {
            throw new SecurityException("Cannot access another user's preferences");
        }

        return findPreferences(orgId, userId).stream()
                .map(NotificationMapper::toDto)
                .toList();
    }

    /**
     * Updates or creates a preference for the current user.
     * Uses deterministic uniqueness via the V24 partial index.
     */
    @Transactional
    public NotificationPreferenceDto setMyPreference(String channel, String alertType, boolean isEnabled) {
        return setMyPreference(channel, alertType, isEnabled, true);
    }

    /**
     * Updates or creates a preference for the current user with push_enabled flag.
     * Phase 5: push_enabled allows users to opt out of push per alert type.
     */
    @Transactional
    public NotificationPreferenceDto setMyPreference(String channel, String alertType,
                                                       boolean isEnabled, boolean pushEnabled) {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Not authenticated");
        }

        // Upsert using the deterministic uniqueness index
        // COALESCE handles NULL alert_type -> '' for the unique index
        int updated = jdbcTemplate.update(
                "UPDATE notification_preferences SET is_enabled = ?, push_enabled = ?, updated_at = now() " +
                        "WHERE user_id = ? AND organization_id = ? AND channel = ? " +
                        "AND COALESCE(alert_type, '') = COALESCE(?, '')",
                isEnabled, pushEnabled, userId, orgId, channel, alertType
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO notification_preferences (user_id, organization_id, channel, alert_type, " +
                            "is_enabled, push_enabled) VALUES (?, ?, ?, ?, ?, ?)",
                    userId, orgId, channel, alertType, isEnabled, pushEnabled
            );
            log.info("Created preference for user={} channel={} alertType={} enabled={} pushEnabled={}",
                    userId, channel, alertType, isEnabled, pushEnabled);
            log.info("AUDIT: preference_changed user={} channel={} alertType={} enabled={} pushEnabled={} action=created",
                    userId, channel, alertType, isEnabled, pushEnabled);
        } else {
            log.info("Updated preference for user={} channel={} alertType={} enabled={} pushEnabled={}",
                    userId, channel, alertType, isEnabled, pushEnabled);
            log.info("AUDIT: preference_changed user={} channel={} alertType={} enabled={} pushEnabled={} action=updated",
                    userId, channel, alertType, isEnabled, pushEnabled);
        }

        // Return the updated preference
        return NotificationMapper.toDto(findPreference(orgId, userId, channel, alertType));
    }

    /**
     * Checks if push delivery is enabled for a user and alert type.
     * Phase 5: respects push_enabled preference per alert type.
     * Returns true by default if no preference is set.
     */
    @Transactional(readOnly = true)
    public boolean isPushEnabled(UUID orgId, UUID userId, String alertType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_preferences " +
                        "WHERE user_id = ? AND organization_id = ? AND channel = 'PUSH' " +
                        "AND COALESCE(alert_type, '') = COALESCE(?, '') " +
                        "AND push_enabled = false",
                Integer.class, userId, orgId, alertType
        );
        return count == null || count == 0;
    }

    /**
     * Returns the catalog of supported notification event types and their descriptions.
     * Per SIG 16: allows clients to discover configurable event types.
     */
    @Transactional(readOnly = true)
    public List<EventCatalogDto> getEventCatalog() {
        return List.of(
                new EventCatalogDto("LOW_BATTERY", "Low Battery",
                        "Triggered when machine battery level drops below threshold.",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                new EventCatalogDto("VOLTAGE_DROP", "Voltage Drop",
                        "Triggered when machine voltage drops below threshold.",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                new EventCatalogDto("GSM_SIGNAL_LOW", "Low GSM Signal",
                        "Triggered when GSM signal strength falls below threshold.",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                new EventCatalogDto("DEVICE_OFFLINE", "Device Offline",
                        "Triggered when a device goes offline or remains offline for an extended period.",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                new EventCatalogDto("SIM_EXPIRY", "SIM Expiry",
                        "Triggered when the SIM plan is nearing expiry or has expired.",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                // Phase 6: alarm-code-driven alert types from BR05 protocol
                new EventCatalogDto("EXTERNAL_POWER_LOW", "External Power Low",
                        "Triggered when the BR05 device reports external power voltage is low (alarm code 0x0E).",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                new EventCatalogDto("EXTERNAL_POWER_CUT", "External Power Cut",
                        "Triggered when external power protection is activated — imminent shutdown (alarm code 0x0F).",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                new EventCatalogDto("LOW_POWER_SHUTDOWN", "Low Power Shutdown",
                        "Triggered when the device is shutting down due to low battery (alarm code 0x15).",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP")),
                new EventCatalogDto("INTERNAL_BATTERY_LOW", "Internal Battery Low",
                        "Triggered when the internal backup battery is low (alarm code 0x19).",
                        List.of("PUSH", "EMAIL", "SMS", "IN_APP"))
        );
    }

    private List<NotificationPreference> findPreferences(UUID orgId, UUID userId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<NotificationPreference> cq = cb.createQuery(NotificationPreference.class);
        Root<NotificationPreference> root = cq.from(NotificationPreference.class);
        Predicate orgPredicate = cb.equal(root.get("organizationId"), orgId);
        Predicate userPredicate = cb.equal(root.get("userId"), userId);
        cq.where(orgPredicate, userPredicate);
        return entityManager.createQuery(cq).getResultList();
    }

    private NotificationPreference findPreference(UUID orgId, UUID userId, String channel, String alertType) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<NotificationPreference> cq = cb.createQuery(NotificationPreference.class);
        Root<NotificationPreference> root = cq.from(NotificationPreference.class);
        Predicate orgPredicate = cb.equal(root.get("organizationId"), orgId);
        Predicate userPredicate = cb.equal(root.get("userId"), userId);
        Predicate channelPredicate = cb.equal(root.get("channel"), channel);
        Predicate typePredicate = alertType != null
                ? cb.equal(root.get("alertType"), alertType)
                : cb.isNull(root.get("alertType"));
        cq.where(orgPredicate, userPredicate, channelPredicate, typePredicate);
        List<NotificationPreference> results = entityManager.createQuery(cq).getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private boolean isSuperAdmin() {
        return permissionEvaluator.hasPermission("notification_preference:read")
                && permissionEvaluator.hasPermission("notification_preference:write");
    }
}
