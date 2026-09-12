package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bounded multi-instance-safe push delivery scheduler.
 *
 * Uses row claiming (SKIP LOCKED + lease) to allow safe parallel execution
 * across multiple backend replicas. Claims a batch of pending push jobs,
 * processes each one, and releases the lease.
 *
 * Per notification plan Phase 5: worker leases, bounded retries.
 * Per AGENTS.md rule 12: production feature with logging + error handling.
 */
@Component
public class PushDeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushDeliveryScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final PushDeliveryService pushDeliveryService;

    private final int batchSize;
    private final int leaseSeconds;

    public PushDeliveryScheduler(JdbcTemplate jdbcTemplate,
                                  PushDeliveryService pushDeliveryService,
                                  @Value("${push.scheduler.batch-size:50}") int batchSize,
                                  @Value("${push.scheduler.lease-seconds:60}") int leaseSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.pushDeliveryService = pushDeliveryService;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * Runs every 15 seconds (configurable). Claims a batch of pending push jobs
     * whose next_attempt_at has arrived and lease is available.
     */
    @Scheduled(fixedDelayString = "${push.scheduler.check-interval-ms:15000}")
    public void processPendingJobs() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusSeconds(leaseSeconds);

        // Claim a batch of pending jobs using SKIP LOCKED + lease
        List<UUID> jobIds = claimJobs(now, leaseUntil);
        if (jobIds.isEmpty()) {
            return;
        }

        log.debug("Push scheduler: claimed {} jobs to process", jobIds.size());

        int sent = 0, failed = 0, retried = 0, expired = 0, cancelled = 0;
        for (UUID jobId : jobIds) {
            try {
                boolean complete = pushDeliveryService.processJob(jobId);
                if (complete) {
                    // Check final status for logging
                    String status = getJobStatus(jobId);
                    // SIG 23: use ACCEPTED_BY_PROVIDER status (was SENT)
                    if ("ACCEPTED_BY_PROVIDER".equals(status)) sent++;
                    else if ("FAILED".equals(status)) failed++;
                    else if ("EXPIRED".equals(status)) expired++;
                    else if ("CANCELLED".equals(status)) cancelled++;
                } else {
                    retried++;
                }
            } catch (Exception e) {
                log.error("Push job processing failed for job={}: {}", jobId, e.getMessage(), e);
                failed++;
            }
        }

        log.info("Push scheduler batch: {} sent, {} failed, {} retried, {} expired, {} cancelled",
                sent, failed, retried, expired, cancelled);
    }

    /**
     * Claims a batch of pending push jobs using SKIP LOCKED + lease for multi-instance safety.
     */
    private List<UUID> claimJobs(LocalDateTime now, LocalDateTime leaseUntil) {
        return jdbcTemplate.queryForList(
                "UPDATE push_delivery_jobs SET claim_lease_until = ? " +
                        "WHERE id IN (" +
                        "  SELECT id FROM push_delivery_jobs " +
                        "  WHERE status = 'PENDING' " +
                        "    AND next_attempt_at <= ? " +
                        "    AND (claim_lease_until IS NULL OR claim_lease_until <= ?) " +
                        "  LIMIT ? " +
                        "  FOR UPDATE SKIP LOCKED" +
                        ") RETURNING id",
                UUID.class,
                leaseUntil, now, now, batchSize
        );
    }

    private String getJobStatus(UUID jobId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT status FROM push_delivery_jobs WHERE id = ?",
                    String.class, jobId
            );
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
