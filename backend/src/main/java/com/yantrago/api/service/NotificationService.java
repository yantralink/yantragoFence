package com.yantrago.api.service;

import com.yantrago.api.dto.notification.NotificationDto;
import com.yantrago.api.model.Notification;
import com.yantrago.api.repository.NotificationRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Legacy notification service — manages the original notifications table (per-dispatch records).
 *
 * Per notification plan Phase 3: the new inbox is in NotificationInboxService.
 * This service is kept for backward compatibility with the dispatch endpoint.
 *
 * Per AGENTS.md rule 12: production feature with validation, logging, security.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final EmailService emailService;
    private final SmsService smsService;

    public NotificationService(NotificationRepository notificationRepository,
                                OwnerContextService ownerContextService,
                                TenantGuard tenantGuard,
                                PermissionEvaluator permissionEvaluator,
                                EmailService emailService,
                                SmsService smsService) {
        this.notificationRepository = notificationRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    /**
     * Lists all notifications in the organization. Admin-only (RBAC enforced at controller).
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> listNotifications(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return notificationRepository.findByOrganizationId(orgId, pageable).map(NotificationService::toDto);
    }

    /**
     * Gets a single notification. Tenant-guarded.
     */
    @Transactional(readOnly = true)
    public NotificationDto getNotification(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        tenantGuard.validateTenantAccess(notification.getOrganizationId());
        return toDto(notification);
    }

    /**
     * Dispatches a PENDING notification via its configured channel.
     * Push delivery remains OFF in Phase 3 — this is for admin-triggered dispatch only.
     */
    @Transactional
    public NotificationDto dispatchNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        tenantGuard.validateTenantAccess(notification.getOrganizationId());

        if (!"PENDING".equals(notification.getStatus())) {
            log.warn("Notification {} already dispatched (status={})", notificationId, notification.getStatus());
            return toDto(notification);
        }

        String channel = notification.getChannel();
        String providerMessageId = null;
        boolean success = false;

        try {
            switch (channel) {
                case "PUSH" -> {
                    // Push delivery remains OFF in Phase 3
                    log.info("Push delivery is disabled (Phase 3). Skipping notification {}", notificationId);
                    notification.setStatus("FAILED");
                    notification.setError("Push delivery is disabled");
                }
                case "EMAIL" -> {
                    success = emailService.sendEmail(null, notification.getTitle(), notification.getBody());
                    notification.setProvider("SMTP");
                }
                case "SMS" -> {
                    // SMS delivery remains OFF (no provider configured)
                    log.info("SMS delivery is disabled (no provider). Skipping notification {}", notificationId);
                    notification.setStatus("FAILED");
                    notification.setError("SMS delivery is disabled");
                }
                default -> {
                    log.warn("Unknown notification channel: {}", channel);
                    notification.setStatus("FAILED");
                    notification.setError("Unknown channel: " + channel);
                }
            }

            if (success) {
                notification.setStatus("SENT");
                notification.setSentAt(LocalDateTime.now());
                if (providerMessageId != null) {
                    notification.setProviderMessageId(providerMessageId);
                }
            } else if (!"FAILED".equals(notification.getStatus())) {
                notification.setStatus("FAILED");
                notification.setError("Provider returned null message ID");
            }
        } catch (Exception e) {
            notification.setStatus("FAILED");
            notification.setError(e.getMessage());
            log.error("Failed to dispatch notification {}: {}", notificationId, e.getMessage(), e);
        }

        notification = notificationRepository.save(notification);
        log.info("Dispatched notification id={} channel={} status={}", notificationId, channel, notification.getStatus());
        return toDto(notification);
    }

    /**
     * Converts a Notification entity to its DTO representation.
     * Per AGENTS.md rule 21: never return JPA entities from controllers.
     */
    public static NotificationDto toDto(Notification entity) {
        if (entity == null) return null;
        return new NotificationDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getUserId(),
                entity.getAlertId(),
                entity.getChannel(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getProviderMessageId(),
                entity.getError(),
                entity.getSentAt(),
                entity.getDeliveredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
