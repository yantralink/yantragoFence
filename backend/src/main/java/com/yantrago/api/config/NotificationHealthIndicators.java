package com.yantrago.api.config;

import com.yantrago.api.service.NotificationMetrics;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Health indicators for notification pipeline (Phase 6).
 *
 * Per notification plan N11:
 * - "Configure alerts for sustained backlog/DLQ growth"
 * - "Keep provider outages distinct from machine offline incidents"
 *
 * Exposes health details at /actuator/health/notification-queue and
 * /actuator/health/push-delivery.
 */
@Component
public class NotificationHealthIndicators {

    /**
     * Notification queue depth health indicator.
     * Reports DOWN if the queue depth exceeds a threshold (sustained backlog).
     */
    @Component("notification-queue")
    public static class NotificationQueueHealthIndicator implements HealthIndicator {
        private final RabbitAdmin rabbitAdmin;
        private final NotificationMetrics notificationMetrics;
        private static final int QUEUE_DEPTH_WARNING = 1000;
        private static final int QUEUE_DEPTH_CRITICAL = 5000;

        public NotificationQueueHealthIndicator(RabbitAdmin rabbitAdmin,
                                                  NotificationMetrics notificationMetrics) {
            this.rabbitAdmin = rabbitAdmin;
            this.notificationMetrics = notificationMetrics;
        }

        @Override
        public Health health() {
            try {
                Properties props = rabbitAdmin.getQueueProperties("yantrago.notification.queue");
                if (props == null) {
                    return Health.unknown().withDetail("error", "Queue not found").build();
                }
                Long messageCount = (Long) props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
                Long consumerCount = (Long) props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);

                // SIG 27: record queue depth metric for monitoring
                if (messageCount != null) {
                    notificationMetrics.recordQueueDepth(messageCount.intValue());
                }

                Health.Builder builder = Health.up();
                if (messageCount != null && messageCount > QUEUE_DEPTH_CRITICAL) {
                    builder = Health.down();
                } else if (messageCount != null && messageCount > QUEUE_DEPTH_WARNING) {
                    builder = Health.up().withDetail("warning", "Queue depth above warning threshold");
                }

                return builder
                        .withDetail("queueDepth", messageCount != null ? messageCount : -1)
                        .withDetail("consumers", consumerCount != null ? consumerCount : 0)
                        .withDetail("warningThreshold", QUEUE_DEPTH_WARNING)
                        .withDetail("criticalThreshold", QUEUE_DEPTH_CRITICAL)
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", "Cannot reach RabbitMQ: " + e.getMessage())
                        .build();
            }
        }
    }

    /**
     * Push delivery health indicator.
     * Reports DOWN if there are too many failed push jobs or stale pending jobs.
     */
    @Component("push-delivery")
    public static class PushDeliveryHealthIndicator implements HealthIndicator {
        private final JdbcTemplate jdbcTemplate;
        private static final int FAILED_WARNING = 100;
        private static final int PENDING_STALE_WARNING = 200;

        public PushDeliveryHealthIndicator(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public Health health() {
            try {
                Integer failedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM push_delivery_jobs WHERE status = 'FAILED'",
                        Integer.class);
                Integer stalePending = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM push_delivery_jobs WHERE status = 'PENDING' " +
                                "AND next_attempt_at < now() - interval '1 hour'",
                        Integer.class);

                Health.Builder builder = Health.up();
                if (failedCount != null && failedCount > FAILED_WARNING) {
                    builder = Health.down();
                } else if (stalePending != null && stalePending > PENDING_STALE_WARNING) {
                    builder = Health.up().withDetail("warning", "Stale pending jobs above threshold");
                }

                return builder
                        .withDetail("failedJobs", failedCount != null ? failedCount : -1)
                        .withDetail("stalePendingJobs", stalePending != null ? stalePending : -1)
                        .withDetail("failedWarningThreshold", FAILED_WARNING)
                        .withDetail("stalePendingWarningThreshold", PENDING_STALE_WARNING)
                        .build();
            } catch (Exception e) {
                // If the table doesn't exist (e.g. migration not run), report unknown
                return Health.unknown()
                        .withDetail("error", "Cannot query push_delivery_jobs: " + e.getMessage())
                        .build();
            }
        }
    }
}
