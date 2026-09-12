package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves notification recipients for a machine's alert event.
 *
 * Resolution chain: machine -> customer (via current assignment) -> user (via customer.user_id).
 *
 * Per notification plan Phase 3:
 * - Event-time recipient snapshots: capture the current assignee at event time.
 * - Current-access revalidation: verify the user still has access at delivery time.
 * - Revoked access suppresses queued fanout.
 * - Non-assignees/admins receive no automatic copy.
 *
 * Per AGENTS.md rule 7: organization_id resolved from machine, never from input.
 */
@Service
public class RecipientResolutionService {

    private static final Logger log = LoggerFactory.getLogger(RecipientResolutionService.class);

    private final JdbcTemplate jdbcTemplate;

    public RecipientResolutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolves the user IDs that should receive notifications for a machine's alert.
     *
     * Only the currently assigned customer's linked user receives notifications.
     * Admins do not receive automatic copies (they can view alerts via the admin web).
     *
     * @param organizationId the organization that owns the machine
     * @param machineId the machine that triggered the alert
     * @return list of user IDs eligible to receive the notification (may be empty)
     */
    public List<UUID> resolveRecipientsForMachine(UUID organizationId, UUID machineId) {
        return resolveRecipientSnapshots(organizationId, machineId)
                .stream()
                .map(RecipientSnapshot::userId)
                .distinct()
                .toList();
    }

    /**
     * Resolves recipient snapshots for a machine's alert, including event-time
     * customer identity for the inbox snapshot.
     *
     * Per notification plan Phase 3: event-time recipient snapshots.
     *
     * @param organizationId the organization that owns the machine
     * @param machineId the machine that triggered the alert
     * @return list of recipient snapshots (may be empty)
     */
    public List<RecipientSnapshot> resolveRecipientSnapshots(UUID organizationId, UUID machineId) {
        if (organizationId == null || machineId == null) {
            return List.of();
        }

        List<RecipientSnapshot> snapshots = new ArrayList<>();

        try {
            snapshots = jdbcTemplate.query(
                    "SELECT c.user_id, c.id AS customer_id, c.name AS customer_name " +
                            "FROM machine_assignments ma " +
                            "JOIN customers c ON c.id = ma.customer_id " +
                            "WHERE ma.organization_id = ? AND ma.machine_id = ? " +
                            "AND ma.unassigned_at IS NULL " +
                            "AND c.user_id IS NOT NULL " +
                            "AND c.is_active = true",
                    (rs, rowNum) -> new RecipientSnapshot(
                            rs.getObject("user_id", UUID.class),
                            rs.getObject("customer_id", UUID.class),
                            rs.getString("customer_name")
                    ),
                    organizationId, machineId
            );
        } catch (Exception e) {
            log.error("Failed to resolve recipient snapshots for machine={}: {}", machineId, e.getMessage(), e);
        }

        // Deduplicate by userId (keep first)
        Map<UUID, RecipientSnapshot> deduped = new java.util.LinkedHashMap<>();
        for (RecipientSnapshot s : snapshots) {
            deduped.putIfAbsent(s.userId(), s);
        }
        return new ArrayList<>(deduped.values());
    }

    /**
     * Revalidates that a user still has access to the machine at delivery time.
     * This handles the case where access was revoked between event time and delivery time.
     *
     * @param organizationId the organization
     * @param machineId the machine
     * @param userId the user to revalidate
     * @return true if the user still has access, false if access was revoked
     */
    public boolean revalidateAccess(UUID organizationId, UUID machineId, UUID userId) {
        if (organizationId == null || machineId == null || userId == null) {
            return false;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM machine_assignments ma " +
                            "JOIN customers c ON c.id = ma.customer_id " +
                            "WHERE ma.organization_id = ? AND ma.machine_id = ? " +
                            "AND ma.unassigned_at IS NULL " +
                            "AND c.user_id = ? " +
                            "AND c.is_active = true",
                    Integer.class, organizationId, machineId, userId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Failed to revalidate access for user={} machine={}: {}",
                    userId, machineId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Event-time recipient snapshot — captures the user ID, customer ID, and
     * customer name at the time of notification creation.
     */
    public record RecipientSnapshot(UUID userId, UUID customerId, String customerName) {}
}
