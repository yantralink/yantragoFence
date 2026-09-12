package com.yantrago.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for PushDeliveryScheduler.
 *
 * Verifies Phase 5 acceptance criteria:
 * - Bounded multi-instance safety via row claiming (SKIP LOCKED + lease)
 * - Worker leases prevent duplicate processing
 * - Empty batch — no work done
 * - Exception in one job does not stop processing others
 *
 * Per notification plan Phase 5: worker leases, bounded retries.
 */
class PushDeliverySchedulerTest {

    private JdbcTemplate jdbcTemplate;
    private PushDeliveryService pushDeliveryService;

    private PushDeliveryScheduler scheduler;

    private final UUID jobId1 = UUID.randomUUID();
    private final UUID jobId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        pushDeliveryService = mock(PushDeliveryService.class);

        scheduler = new PushDeliveryScheduler(jdbcTemplate, pushDeliveryService, 50, 60);
    }

    @Test
    @DisplayName("Empty batch — no jobs processed")
    void emptyBatch_noJobsProcessed() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        scheduler.processPendingJobs();

        verify(pushDeliveryService, never()).processJob(any());
    }

    @Test
    @DisplayName("Claims and processes jobs in batch")
    void claimsAndProcessesJobs() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any(), anyInt()))
                .thenReturn(List.of(jobId1, jobId2));
        when(pushDeliveryService.processJob(jobId1)).thenReturn(true);
        when(pushDeliveryService.processJob(jobId2)).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(jobId1)))
                .thenReturn("ACCEPTED_BY_PROVIDER");
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(jobId2)))
                .thenReturn("ACCEPTED_BY_PROVIDER");

        scheduler.processPendingJobs();

        verify(pushDeliveryService).processJob(jobId1);
        verify(pushDeliveryService).processJob(jobId2);
    }

    @Test
    @DisplayName("Exception in one job does not stop processing others")
    void exceptionInOneJob_doesNotStopOthers() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any(), anyInt()))
                .thenReturn(List.of(jobId1, jobId2));
        when(pushDeliveryService.processJob(jobId1))
                .thenThrow(new RuntimeException("DB error"));
        when(pushDeliveryService.processJob(jobId2)).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(jobId2)))
                .thenReturn("ACCEPTED_BY_PROVIDER");

        scheduler.processPendingJobs();

        // Second job should still be processed
        verify(pushDeliveryService).processJob(jobId2);
    }

    @Test
    @DisplayName("Retried job (processJob returns false) is counted as retried")
    void retriedJob_countedAsRetried() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any(), anyInt()))
                .thenReturn(List.of(jobId1));
        when(pushDeliveryService.processJob(jobId1)).thenReturn(false);

        scheduler.processPendingJobs();

        verify(pushDeliveryService).processJob(jobId1);
        // No status query since job is not complete
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(String.class), eq(jobId1));
    }

    @Test
    @DisplayName("Uses SKIP LOCKED + lease for multi-instance safety")
    void usesSkipLockedAndLease() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        scheduler.processPendingJobs();

        // Verify the claim query uses SKIP LOCKED
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("SKIP LOCKED"),
                eq(UUID.class), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Lease expiry recovery: job with expired lease is re-claimable")
    void leaseExpiryRecovery_expiredLeaseIsReclaimable() {
        // The claim query checks: claim_lease_until IS NULL OR claim_lease_until <= now
        // This means a job whose lease has expired (e.g. worker crashed) will be
        // re-claimed by another worker. We verify the claim query includes this condition.
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        scheduler.processPendingJobs();

        // Verify the claim query includes the lease expiry condition
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("claim_lease_until IS NULL OR claim_lease_until <= ?"),
                eq(UUID.class), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Batch size is configurable and limits claims")
    void batchSizeLimitsClaims() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        scheduler.processPendingJobs();

        // Verify the batch size (50) is passed to the query
        verify(jdbcTemplate).queryForList(
                anyString(), eq(UUID.class), any(), any(), any(), eq(50));
    }
}
