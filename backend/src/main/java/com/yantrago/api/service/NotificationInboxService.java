package com.yantrago.api.service;

import com.yantrago.api.dto.notification.NotificationInboxDto;
import com.yantrago.api.dto.notification.NotificationMapper;
import com.yantrago.api.model.NotificationInbox;
import com.yantrago.api.repository.NotificationInboxRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import com.yantrago.api.websocket.NotificationBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification inbox service — manages the user-facing notification inbox.
 *
 * Per notification plan Phase 3:
 * - Pagination, filtering, unread count
 * - Mark-read / mark-all-read (idempotent)
 * - Private read state (cross-user rejection)
 * - WebSocket invalidation on read state change
 * - Authorized acknowledgement (explicit user action, separate from read)
 *
 * Per AGENTS.md rule 7: organization_id from JWT, never from request body.
 * Per AGENTS.md rule 9: RBAC on all operations.
 * Per AGENTS.md rule 21: returns DTOs, never JPA entities.
 */
@Service
public class NotificationInboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationInboxService.class);

    private final NotificationInboxRepository inboxRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final NotificationBroadcastService broadcastService;

    public NotificationInboxService(NotificationInboxRepository inboxRepository,
                                     OwnerContextService ownerContextService,
                                     TenantGuard tenantGuard,
                                     PermissionEvaluator permissionEvaluator,
                                     NotificationBroadcastService broadcastService) {
        this.inboxRepository = inboxRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.broadcastService = broadcastService;
    }

    /**
     * Lists the current user's inbox items with pagination.
     * Optional filters: unreadOnly and alertType — applied simultaneously.
     * Per SIG 14: page size capped at 100 to prevent excessive result sets.
     * Per SIG 15: both unreadOnly and alertType filters are applied together.
     */
    @Transactional(readOnly = true)
    public Page<NotificationInboxDto> listMyNotifications(Pageable pageable, Boolean unreadOnly, String alertType) {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Not authenticated");
        }

        // SIG 14: cap page size at 100
        Pageable capped = pageable.getPageSize() > 100
                ? PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort())
                : pageable;

        // SIG 15: build a specification that applies BOTH unreadOnly and alertType together
        Specification<NotificationInbox> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            predicates.add(cb.equal(root.get("userId"), userId));
            if (Boolean.TRUE.equals(unreadOnly)) {
                predicates.add(cb.equal(root.get("isRead"), false));
            }
            if (alertType != null && !alertType.isBlank()) {
                predicates.add(cb.equal(root.get("alertType"), alertType));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<NotificationInbox> page = inboxRepository.findAll(spec, capped);
        return page.map(NotificationMapper::toDto);
    }

    /**
     * Gets a single inbox item. Rejects cross-user access.
     */
    @Transactional(readOnly = true)
    public NotificationInboxDto getNotification(UUID id) {
        NotificationInbox inbox = inboxRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        tenantGuard.validateTenantAccess(inbox.getOrganizationId());

        // Private read state: only the owner can view their inbox items
        UUID currentUserId = permissionEvaluator.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(inbox.getUserId())) {
            log.warn("Cross-user access attempt: user={} tried to read notification {} owned by user={}",
                    currentUserId, id, inbox.getUserId());
            throw new SecurityException("Cannot access another user's notification");
        }

        return NotificationMapper.toDto(inbox);
    }

    /**
     * Counts unread notifications for the current user.
     */
    @Transactional(readOnly = true)
    public long countUnread() {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Not authenticated");
        }
        return inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId);
    }

    /**
     * Marks a single notification as read. Idempotent — if already read, no-op.
     */
    @Transactional
    public NotificationInboxDto markAsRead(UUID id) {
        NotificationInbox inbox = inboxRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        tenantGuard.validateTenantAccess(inbox.getOrganizationId());

        // Private: only the owner can mark their own notifications as read
        UUID currentUserId = permissionEvaluator.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(inbox.getUserId())) {
            log.warn("Cross-user mark-read attempt: user={} tried to mark notification {} owned by user={}",
                    currentUserId, id, inbox.getUserId());
            throw new SecurityException("Cannot modify another user's notification");
        }

        // Idempotent: if already read, return as-is
        if (Boolean.TRUE.equals(inbox.getIsRead())) {
            return NotificationMapper.toDto(inbox);
        }

        LocalDateTime now = LocalDateTime.now();
        inbox.setIsRead(true);
        inbox.setReadAt(now);
        inbox = inboxRepository.save(inbox);

        // Send WebSocket invalidation with updated unread count
        long unreadCount = inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(
                inbox.getOrganizationId(), currentUserId);
        broadcastService.notifyReadStateChange(currentUserId, unreadCount);

        log.info("Marked notification {} as read for user={}", id, currentUserId);
        return NotificationMapper.toDto(inbox);
    }

    /**
     * Marks all unread notifications as read for the current user. Idempotent.
     */
    @Transactional
    public int markAllAsRead() {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Not authenticated");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = inboxRepository.markAllRead(orgId, userId, now);

        if (updated > 0) {
            broadcastService.notifyReadStateChange(userId, 0L);
            log.info("Marked {} notifications as read for user={}", updated, userId);
        }

        return updated;
    }

    /**
     * Acknowledges a notification — explicit user action separate from read.
     * Per notification plan Phase 4: authorized acknowledgement.
     * Idempotent — if already acknowledged, no-op.
     */
    @Transactional
    public NotificationInboxDto acknowledge(UUID id) {
        NotificationInbox inbox = inboxRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        tenantGuard.validateTenantAccess(inbox.getOrganizationId());

        // Private: only the owner can acknowledge their own notifications
        UUID currentUserId = permissionEvaluator.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(inbox.getUserId())) {
            log.warn("Cross-user acknowledge attempt: user={} tried to acknowledge notification {} owned by user={}",
                    currentUserId, id, inbox.getUserId());
            throw new SecurityException("Cannot acknowledge another user's notification");
        }

        // Idempotent: if already acknowledged, return as-is
        if (Boolean.TRUE.equals(inbox.getIsAcknowledged())) {
            return NotificationMapper.toDto(inbox);
        }

        LocalDateTime now = LocalDateTime.now();
        inbox.setIsAcknowledged(true);
        inbox.setAcknowledgedAt(now);
        // Acknowledgement also marks as read
        if (!Boolean.TRUE.equals(inbox.getIsRead())) {
            inbox.setIsRead(true);
            inbox.setReadAt(now);
        }
        inbox = inboxRepository.save(inbox);

        log.info("Acknowledged notification {} for user={}", id, currentUserId);
        log.info("AUDIT: notification_acknowledged user={} inboxId={}", currentUserId, id);
        return NotificationMapper.toDto(inbox);
    }
}
