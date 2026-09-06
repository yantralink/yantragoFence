package com.yantrago.api.queue;

import com.yantrago.api.model.Alert;
import com.yantrago.api.repository.AlertRepository;
import com.yantrago.api.repository.MachineRepository;
import com.yantrago.api.service.OwnerContextService;
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
 * Consumes AlertEventMessage from the gateway (e.g. SOS, low battery, offline)
 * and persists alert instances to the alerts table.
 *
 * Per AGENTS.md rule 17: uses shared module message contracts.
 */
@Component
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final AlertRepository alertRepository;
    private final MachineRepository machineRepository;
    private final OwnerContextService ownerContextService;

    public AlertConsumer(AlertRepository alertRepository,
                         MachineRepository machineRepository,
                         OwnerContextService ownerContextService) {
        this.alertRepository = alertRepository;
        this.machineRepository = machineRepository;
        this.ownerContextService = ownerContextService;
    }

    @RabbitListener(queues = QueueNames.ALERT_EVENT_QUEUE)
    @Transactional
    public void handleAlertEvent(AlertEventMessage message) {
        log.info("Received alert event: alertId={} machineId={} type={} severity={}",
                message.getAlertId(), message.getMachineId(),
                message.getAlertType(), message.getSeverity());

        try {
            // Resolve organization_id from the machine
            UUID orgId = machineRepository.findById(message.getMachineId())
                    .map(m -> m.getOrganizationId())
                    .orElse(null);

            if (orgId == null) {
                log.warn("Cannot persist alert: machine not found machineId={}", message.getMachineId());
                return;
            }

            Alert alert = new Alert();
            alert.setOrganizationId(orgId);
            alert.setMachineId(message.getMachineId());
            alert.setAlertType(message.getAlertType());
            alert.setSeverity(message.getSeverity());
            alert.setMessage(message.getMessage());
            alert.setIsAcknowledged(false);
            alert.setTriggeredAt(message.getTimestamp() != null
                    ? LocalDateTime.ofInstant(message.getTimestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now());

            alert = alertRepository.save(alert);
            log.info("Persisted alert id={} type={} severity={}", alert.getId(), alert.getAlertType(), alert.getSeverity());
        } catch (Exception e) {
            log.error("Failed to persist alert event for machineId={}: {}",
                    message.getMachineId(), e.getMessage(), e);
        }
    }
}
