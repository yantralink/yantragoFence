package com.yantrago.api.service;

import com.yantrago.api.service.RecipientResolutionService.RecipientSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for RecipientResolutionService.
 *
 * Verifies Phase 3 acceptance criteria:
 * - Eligible assignees each get exactly one inbox item
 * - Non-assignees/admins receive no automatic copy
 * - Revoked access suppresses queued fanout
 * - Recipient snapshots capture customer identity at event time
 *
 * Per notification plan Phase 3 acceptance.
 */
class RecipientResolutionServiceTest {

    private JdbcTemplate jdbcTemplate;
    private RecipientResolutionService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID machineId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new RecipientResolutionService(jdbcTemplate);
    }

    @Test
    @DisplayName("resolveRecipients: returns assigned customer's user ID")
    void resolveRecipients_returnsAssignedCustomerUser() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orgId), eq(machineId)))
                .thenReturn(List.of(createSnapshot()));

        List<UUID> recipients = service.resolveRecipientsForMachine(orgId, machineId);

        assertEquals(1, recipients.size());
        assertEquals(userId, recipients.get(0));
    }

    @Test
    @DisplayName("resolveRecipients: returns empty when no active assignment")
    void resolveRecipients_returnsEmptyWhenNoAssignment() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orgId), eq(machineId)))
                .thenReturn(List.of());

        List<UUID> recipients = service.resolveRecipientsForMachine(orgId, machineId);

        assertTrue(recipients.isEmpty());
    }

    @Test
    @DisplayName("resolveRecipients: returns empty when orgId is null")
    void resolveRecipients_returnsEmptyWhenOrgIdNull() {
        List<UUID> recipients = service.resolveRecipientsForMachine(null, machineId);
        assertTrue(recipients.isEmpty());
    }

    @Test
    @DisplayName("resolveRecipients: returns empty when machineId is null")
    void resolveRecipients_returnsEmptyWhenMachineIdNull() {
        List<UUID> recipients = service.resolveRecipientsForMachine(orgId, null);
        assertTrue(recipients.isEmpty());
    }

    @Test
    @DisplayName("resolveRecipients: deduplicates user IDs")
    void resolveRecipients_deduplicatesUserIds() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orgId), eq(machineId)))
                .thenReturn(List.of(createSnapshot(), createSnapshot(), createSnapshot()));

        List<UUID> recipients = service.resolveRecipientsForMachine(orgId, machineId);

        assertEquals(1, recipients.size());
    }

    @Test
    @DisplayName("resolveRecipientSnapshots: returns snapshot with customer info")
    void resolveRecipientSnapshots_returnsSnapshotWithCustomerInfo() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orgId), eq(machineId)))
                .thenReturn(List.of(createSnapshot()));

        List<RecipientSnapshot> snapshots = service.resolveRecipientSnapshots(orgId, machineId);

        assertEquals(1, snapshots.size());
        assertEquals(userId, snapshots.get(0).userId());
        assertEquals(customerId, snapshots.get(0).customerId());
        assertEquals("John Farmer", snapshots.get(0).customerName());
    }

    @Test
    @DisplayName("resolveRecipientSnapshots: returns empty when no assignment")
    void resolveRecipientSnapshots_returnsEmptyWhenNoAssignment() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orgId), eq(machineId)))
                .thenReturn(List.of());

        List<RecipientSnapshot> snapshots = service.resolveRecipientSnapshots(orgId, machineId);

        assertTrue(snapshots.isEmpty());
    }

    @Test
    @DisplayName("resolveRecipientSnapshots: deduplicates by userId")
    void resolveRecipientSnapshots_deduplicatesByUserId() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orgId), eq(machineId)))
                .thenReturn(List.of(createSnapshot(), createSnapshot()));

        List<RecipientSnapshot> snapshots = service.resolveRecipientSnapshots(orgId, machineId);

        assertEquals(1, snapshots.size());
    }

    @Test
    @DisplayName("revalidateAccess: returns true when user still has access")
    void revalidateAccess_returnsTrueWhenAccessExists() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq(orgId), eq(machineId), eq(userId)))
                .thenReturn(1);

        boolean hasAccess = service.revalidateAccess(orgId, machineId, userId);

        assertTrue(hasAccess);
    }

    @Test
    @DisplayName("revalidateAccess: returns false when access revoked")
    void revalidateAccess_returnsFalseWhenAccessRevoked() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq(orgId), eq(machineId), eq(userId)))
                .thenReturn(0);

        boolean hasAccess = service.revalidateAccess(orgId, machineId, userId);

        assertFalse(hasAccess);
    }

    @Test
    @DisplayName("revalidateAccess: returns false when any parameter is null")
    void revalidateAccess_returnsFalseWhenNullParams() {
        assertFalse(service.revalidateAccess(null, machineId, userId));
        assertFalse(service.revalidateAccess(orgId, null, userId));
        assertFalse(service.revalidateAccess(orgId, machineId, null));
    }

    @Test
    @DisplayName("revalidateAccess: returns false on exception (safe default)")
    void revalidateAccess_returnsFalseOnException() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq(orgId), eq(machineId), eq(userId)))
                .thenThrow(new RuntimeException("DB error"));

        boolean hasAccess = service.revalidateAccess(orgId, machineId, userId);

        assertFalse(hasAccess);
    }

    private RecipientSnapshot createSnapshot() {
        return new RecipientSnapshot(userId, customerId, "John Farmer");
    }
}
