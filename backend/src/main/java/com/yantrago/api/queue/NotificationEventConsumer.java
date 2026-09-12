package com.yantrago.api.queue;

import com.yantrago.api.model.NotificationInbox;
import com.yantrago.api.repository.NotificationInboxRepository;
import com.yantrago.api.service.NotificationFeatureSwitches;
import com.yantrago.api.service.NotificationMetrics;
import com.yantrago.api.service.NotificationTemplateService;
import com.yantrago.api.service.PushDeliveryService;
import com.yantrago.api.service.RecipientResolutionService;
import com.yantrago.api.service.RecipientResolutionService.RecipientSnapshot;
import com.yantrago.api.websocket.NotificationBroadcastService;
import com.yantrago.shared.queue.AlertTransitionMessage;
import com.yantrago.shared.queue.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes AlertTransitionMessage from the dedicated NOTIFICATION_QUEUE
 * (published by the outbox publisher after a committed alert state change).
 *
 * Phase 3: resolves recipients, renders templates, creates inbox items with
 * per-event/recipient deduplication, and sends WebSocket invalidation.
 * Push delivery remains OFF.
 *
 * Per AGENTS.md rule 17: uses shared module message contracts.
 * Per notification plan Phase 3: eligible assignees each get exactly one inbox item.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final JdbcTemplate jdbcTemplate;
    private final RecipientResolutionService recipientResolutionService;
    private final NotificationTemplateService templateService;
    private final NotificationInboxRepository inboxRepository;
    private final NotificationBroadcastService broadcastService;
    private final PushDeliveryService pushDeliveryService;
    private final NotificationFeatureSwitches featureSwitches;
    private final NotificationMetrics notificationMetrics;

    public NotificationEventConsumer(JdbcTemplate jdbcTemplate,
                                      RecipientResolutionService recipientResolutionService,
                                      NotificationTemplateService templateService,
                                      NotificationInboxRepository inboxRepository,
                                      NotificationBroadcastService broadcastService,
                                      PushDeliveryService pushDeliveryService,
                                      NotificationFeatureSwitches featureSwitches,
                                      NotificationMetrics notificationMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.recipientResolutionService = recipientResolutionService;
        this.templateService = templateService;
        this.inboxRepository = inboxRepository;
        this.broadcastService = broadcastService;
        this.pushDeliveryService = pushDeliveryService;
        this.featureSwitches = featureSwitches;
        this.notificationMetrics = notificationMetrics;
    }

    @RabbitListener(queues = QueueNames.NOTIFICATION_QUEUE)
    public void handleAlertTransition(AlertTransitionMessage message) {
        log.info("Received alert transition: eventId={} alertId={} type={} state={} severity={} machineId={}",
                message.getEventId(), message.getAlertId(), message.getAlertType(),
                message.getIncidentState(), message.getSeverity(), message.getMachineId());

        UUID eventId = message.getEventId();
        if (eventId == null) {
            log.warn("Alert transition has no eventId — rejecting");
            throw new IllegalArgumentException("Missing eventId");
        }

        // Idempotency check — prevent duplicate processing on redelivery
        int inserted = jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, event_type) VALUES (?, ?) " +
                        "ON CONFLICT (event_id) DO NOTHING",
                eventId, "ALERT_TRANSITION"
        );

        if (inserted == 0) {
            log.info("Event {} already processed — skipping (idempotent)", eventId);
            return;
        }

        UUID orgId = message.getOrganizationId();
        UUID machineId = message.getMachineId();
        String alertType = message.getAlertType();
        String incidentState = message.getIncidentState();
        String severity = message.getSeverity();

        if (orgId == null || machineId == null) {
            log.warn("Alert transition missing orgId or machineId — skipping inbox creation");
            return;
        }

        // Phase 6: per-event-family feature switch
        if (!featureSwitches.isEventFamilyEnabled(alertType)) {
            log.info("Event family {} disabled by feature switch — skipping inbox creation eventId={}",
                    alertType, eventId);
            notificationMetrics.recordEventFamilySkipped(alertType);
            return;
        }

        // Phase 6: record alert transition by type
        notificationMetrics.recordAlertTransition(alertType, incidentState);

        // Resolve recipients: only the currently assigned customer's user
        List<RecipientSnapshot> recipients = recipientResolutionService.resolveRecipientSnapshots(orgId, machineId);

        if (recipients.isEmpty()) {
            // No-recipient behavior: event is durable (outbox + processed_events),
            // but no inbox item is created. This is correct — no automatic copy to admins.
            log.info("No recipients for alert transition eventId={} machine={} (no active assignment)",
                    eventId, machineId);
            notificationMetrics.recordNoRecipient(alertType);
            return;
        }

        // Resolve machine name for template rendering
        String machineName = resolveMachineName(machineId);

        // Build template variables
        Map<String, String> variables = new HashMap<>();
        variables.put("machineName", machineName != null ? machineName : "Unknown");
        variables.put("observedValue", message.getObservedValue() != null
                ? String.valueOf(message.getObservedValue()) : "");
        variables.put("observedUnit", message.getObservedUnit() != null ? message.getObservedUnit() : "");
        variables.put("message", message.getMessage() != null ? message.getMessage() : "");

        // Render template (English default for Phase 3; locale resolution is Phase 4)
        NotificationTemplateService.RenderedTemplate template = templateService.render(
                orgId, alertType, incidentState, "en", variables
        );

        String title;
        String body;
        int templateVersion;

        if (template != null) {
            title = template.title();
            body = template.body();
            templateVersion = template.templateVersion();
        } else {
            // Fallback: use the message from the transition
            title = alertType + " — " + incidentState;
            body = message.getMessage() != null ? message.getMessage() : "Alert: " + alertType;
            templateVersion = 0;
        }

        // Create inbox items for each recipient with per-event/recipient deduplication
        for (RecipientSnapshot recipient : recipients) {
            try {
                createInboxItem(message, eventId, orgId, machineId, recipient,
                        title, body, templateVersion);
            } catch (Exception e) {
                log.error("Failed to create inbox item for user={} eventId={}: {}",
                        recipient.userId(), eventId, e.getMessage(), e);
            }
        }

        log.info("Processed alert transition eventId={} alertId={} state={} recipients={}",
                eventId, message.getAlertId(), incidentState, recipients.size());
    }

    /**
     * Creates a single inbox item with deduplication.
     * If an inbox item already exists for this (event_id, user_id), it is skipped.
     */
    private void createInboxItem(AlertTransitionMessage message, UUID eventId,
                                  UUID orgId, UUID machineId, RecipientSnapshot recipient,
                                  String title, String body, int templateVersion) {
        UUID userId = recipient.userId();

        // Check for existing inbox item (dedup)
        if (inboxRepository.findByEventIdAndUserId(eventId, userId).isPresent()) {
            log.debug("Inbox item already exists for eventId={} userId={} — skipping", eventId, userId);
            notificationMetrics.recordDuplicateSuppressed(message.getAlertType());
            return;
        }

        // Revalidate access at delivery time (revoked access suppresses fanout)
        if (!recipientResolutionService.revalidateAccess(orgId, machineId, userId)) {
            log.info("Access revoked for user={} machine={} — suppressing inbox item", userId, machineId);
            notificationMetrics.recordRecipientExcluded("access_revoked");
            return;
        }

        long startTime = System.nanoTime();

        NotificationInbox inbox = new NotificationInbox();
        inbox.setOrganizationId(orgId);
        inbox.setUserId(userId);
        inbox.setAlertId(message.getAlertId());
        inbox.setEventId(eventId);
        inbox.setAlertType(message.getAlertType());
        inbox.setSeverity(message.getSeverity());
        inbox.setIncidentState(message.getIncidentState());
        inbox.setTitle(title);
        inbox.setBody(body);
        inbox.setMachineId(machineId);
        inbox.setObservedValue(message.getObservedValue());
        inbox.setObservedUnit(message.getObservedUnit());
        inbox.setLocale("en");
        inbox.setTemplateVersion(templateVersion);
        inbox.setIsRead(false);
        // Event-time recipient snapshot
        inbox.setRecipientCustomerId(recipient.customerId());
        inbox.setRecipientCustomerName(recipient.customerName());

        inbox = inboxRepository.save(inbox);

        // Phase 6: record inbox creation latency
        notificationMetrics.recordInboxCreationLatency(System.nanoTime() - startTime);

        // Send WebSocket invalidation to the user
        long unreadCount = inboxRepository.countByOrganizationIdAndUserIdAndIsReadFalse(orgId, userId);
        broadcastService.notifyNewNotification(userId, unreadCount);

        // Enqueue push delivery for all active device tokens (Phase 5)
        // Phase 3 fix: only enqueue if push is globally enabled
        if (featureSwitches.isPushEnabled()) {
            Map<String, String> pushData = new HashMap<>();
            pushData.put("alertType", message.getAlertType());
            pushData.put("incidentState", message.getIncidentState());
            pushData.put("severity", message.getSeverity());
            pushData.put("machineId", machineId.toString());
            pushData.put("inboxId", inbox.getId().toString());
            try {
                pushDeliveryService.enqueuePushDelivery(orgId, inbox.getId(), userId,
                        title, body, pushData);
            } catch (Exception e) {
                log.error("Failed to enqueue push delivery for inbox={} user={}: {}",
                        inbox.getId(), userId, e.getMessage(), e);
            }
        } else {
            log.debug("Push disabled — skipping push enqueue for inbox={} user={}",
                    inbox.getId(), userId);
        }

        log.info("Created inbox item id={} for user={} eventId={} unread={}",
                inbox.getId(), userId, eventId, unreadCount);
    }

    private String resolveMachineName(UUID machineId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM machines WHERE id = ?",
                    String.class, machineId
            );
        } catch (Exception e) {
            log.debug("Could not resolve machine name for id={}", machineId);
            return null;
        }
    }
}
