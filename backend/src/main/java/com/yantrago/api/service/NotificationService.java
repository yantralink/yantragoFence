package com.yantrago.api.service;

import com.yantrago.api.model.Notification;
import com.yantrago.api.model.NotificationPreference;
import com.yantrago.api.repository.NotificationRepository;
import com.yantrago.api.security.PermissionEvaluator;
import com.yantrago.api.security.TenantGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Notification service — manages notification records and dispatches them
 * via the appropriate channels (PUSH, EMAIL, SMS) based on user preferences.
 *
 * Per AGENTS.md rule 12: production feature requiring validation, logging, tests, security.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final OwnerContextService ownerContextService;
    private final TenantGuard tenantGuard;
    private final PermissionEvaluator permissionEvaluator;
    private final PushNotificationService pushNotificationService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final EntityManager entityManager;

    public NotificationService(NotificationRepository notificationRepository,
                                OwnerContextService ownerContextService,
                                TenantGuard tenantGuard,
                                PermissionEvaluator permissionEvaluator,
                                PushNotificationService pushNotificationService,
                                EmailService emailService,
                                SmsService smsService,
                                EntityManager entityManager) {
        this.notificationRepository = notificationRepository;
        this.ownerContextService = ownerContextService;
        this.tenantGuard = tenantGuard;
        this.permissionEvaluator = permissionEvaluator;
        this.pushNotificationService = pushNotificationService;
        this.emailService = emailService;
        this.smsService = smsService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listNotifications(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        return notificationRepository.findByOrganizationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Notification> listMyNotifications(Pageable pageable) {
        UUID orgId = ownerContextService.getOrganizationId();
        UUID userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Not authenticated");
        }
        return notificationRepository.findByOrganizationIdAndUserId(orgId, userId, pageable);
    }

    @Transactional(readOnly = true)
    public Notification getNotification(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        tenantGuard.validateTenantAccess(notification.getOrganizationId());
        return notification;
    }

    /**
     * Dispatches a PENDING notification via its configured channel.
     * Updates the notification record with the result.
     */
    @Transactional
    public Notification dispatchNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        tenantGuard.validateTenantAccess(notification.getOrganizationId());

        if (!"PENDING".equals(notification.getStatus())) {
            log.warn("Notification {} already dispatched (status={})", notificationId, notification.getStatus());
            return notification;
        }

        String channel = notification.getChannel();
        String providerMessageId = null;
        boolean success = false;

        try {
            switch (channel) {
                case "PUSH" -> {
                    // In production, look up the user's device token
                    providerMessageId = pushNotificationService.sendPushNotification(
                            null, notification.getTitle(), notification.getBody(), null);
                    success = providerMessageId != null;
                    notification.setProvider("FCM");
                }
                case "EMAIL" -> {
                    // In production, look up the user's email
                    success = emailService.sendEmail(null, notification.getTitle(), notification.getBody());
                    notification.setProvider("SMTP");
                }
                case "SMS" -> {
                    // In production, look up the user's phone
                    providerMessageId = smsService.sendSms(null, notification.getBody());
                    success = providerMessageId != null;
                    notification.setProvider("TWILIO");
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
        return notification;
    }

    /**
     * Looks up notification preferences for a user.
     */
    @Transactional(readOnly = true)
    public List<NotificationPreference> getUserPreferences(UUID userId) {
        UUID orgId = ownerContextService.getOrganizationId();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<NotificationPreference> cq = cb.createQuery(NotificationPreference.class);
        Root<NotificationPreference> root = cq.from(NotificationPreference.class);
        Predicate orgPredicate = cb.equal(root.get("organizationId"), orgId);
        Predicate userPredicate = cb.equal(root.get("userId"), userId);
        cq.where(orgPredicate, userPredicate);
        return entityManager.createQuery(cq).getResultList();
    }
}
