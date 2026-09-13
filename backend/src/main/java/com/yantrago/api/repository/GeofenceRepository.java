package com.yantrago.api.repository;

import com.yantrago.api.model.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {

    List<Geofence> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<Geofence> findByOrganizationIdAndMachineId(UUID organizationId, UUID machineId);

    @Query("SELECT g FROM Geofence g WHERE g.organizationId = :orgId AND g.machineId = :machineId AND g.isActive = true")
    Optional<Geofence> findActiveByMachineId(@Param("orgId") UUID orgId,
                                              @Param("machineId") UUID machineId);

    /**
     * Finds the active geofence for a machine regardless of organization.
     * Used by TheftProtectionService.enable() to update an existing geofence
     * in place (the machine may have been reassigned across orgs, or the
     * geofence may have been created by an admin from a different org).
     * Machine ownership is validated by the caller before this query.
     */
    @Query("SELECT g FROM Geofence g WHERE g.machineId = :machineId AND g.isActive = true")
    Optional<Geofence> findActiveByMachineIdOnly(@Param("machineId") UUID machineId);

    @Query("SELECT g FROM Geofence g WHERE g.organizationId = :orgId AND g.isActive = true")
    List<Geofence> findActiveByOrganizationId(@Param("orgId") UUID orgId);

    /**
     * Returns geofences for machines owned by a specific customer within an org.
     * Used for customer-level isolation (Phase 11).
     */
    @Query("SELECT g FROM Geofence g WHERE g.organizationId = :orgId " +
            "AND g.machineId IN (SELECT m.id FROM Machine m WHERE m.customerId = :customerId) " +
            "ORDER BY g.createdAt DESC")
    List<Geofence> findByOrganizationIdAndCustomerMachines(@Param("orgId") UUID orgId,
                                                            @Param("customerId") UUID customerId);
}
