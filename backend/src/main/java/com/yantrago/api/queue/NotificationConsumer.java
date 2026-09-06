package com.yantrago.api.queue;

import com.yantrago.api.model.Notification;
import com.yantrago.api.repository.NotificationRepository;
import com.yantrago.shared.queue.AlertEventMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Consumes AlertEventMessage (from the alert exchange) and dispatches
 * notifications asynchronously. In a full implementation, this would:
 * 1. Look up notification_preferences for affected users
 * 2. Create notification records (one per channel per user)
 * 3. Dispatch via PushNotificationService / EmailService / SmsService
 *
 * For now, this creates a PENDING notification record for each alert.
 * The actual dispatch (FCM/Expo/SMTP/Twilio) will be implemented in Phase 9.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository notificationRepository;

    public NotificationConsumer(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @RabbitListener(queues = QueueNames.ALERT_EVENT_QUEUE)
    @Transactional
    public void handleAlertForNotification(AlertEventMessage message) {
        log.info("Processing notification for alert: alertId={} machineId={} type={}",
                message.getAlertId(), message.getMachineId(), message.getAlertType());

        try {
            // Create a PENDING notification record.
            // In Phase 9, this will look up user preferences and dispatch to
            // the appropriate channels (PUSH, EMAIL, SMS, WHATSAPP).
            Notification notification = new Notification();
            notification.setOrganizationId(UUID.randomUUID()); // placeholder — resolved from machine in production
            notification.setAlertId(message.getAlertId());
            notification.setChannel("PUSH"); // default channel — will be driven by preferences in Phase 9
            notification.setTitle("Alert: " + message.getAlertType());
            notification.setBody(message.getMessage());
            notification.setStatus("PENDING");
            notification.setSentAt(null);
            notification.setDeliveredAt(null);

            notification = notificationRepository.save(notification);
            log.info("Created PENDING notification id={} for alertId={}",
                    notification.getId(), message.getAlertId());
        } catch (Exception e) {
            log.error("Failed to create notification for alertId={}: {}",
                    message.getAlertId(), e.getMessage(), e);
        }
    }
}
