package com.yantrago.api.repository;

import com.yantrago.api.model.NotificationInbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationInboxRepository extends JpaRepository<NotificationInbox, UUID>,
        JpaSpecificationExecutor<NotificationInbox> {

    /**
     * Find all inbox items for a user in an organization, ordered by creation time desc.
     */
    Page<NotificationInbox> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(
            UUID organizationId, UUID userId, Pageable pageable);

    /**
     * Find unread inbox items for a user in an organization.
     */
    Page<NotificationInbox> findByOrganizationIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(
            UUID organizationId, UUID userId, Pageable pageable);

    /**
     * Find inbox items for a user filtered by alert type.
     */
    Page<NotificationInbox> findByOrganizationIdAndUserIdAndAlertTypeOrderByCreatedAtDesc(
            UUID organizationId, UUID userId, String alertType, Pageable pageable);

    /**
     * Count unread inbox items for a user.
     */
    long countByOrganizationIdAndUserIdAndIsReadFalse(UUID organizationId, UUID userId);

    /**
     * Check if an inbox item already exists for this event and user (dedup).
     */
    Optional<NotificationInbox> findByEventIdAndUserId(UUID eventId, UUID userId);

    /**
     * Mark all unread items as read for a user. Returns count of updated rows.
     */
    @Modifying
    @Query("UPDATE NotificationInbox n SET n.isRead = true, n.readAt = :readAt, n.updatedAt = :readAt " +
            "WHERE n.organizationId = :orgId AND n.userId = :userId AND n.isRead = false")
    int markAllRead(@Param("orgId") UUID orgId,
                    @Param("userId") UUID userId,
                    @Param("readAt") LocalDateTime readAt);
}
