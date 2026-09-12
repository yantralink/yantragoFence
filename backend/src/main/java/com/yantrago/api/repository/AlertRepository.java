package com.yantrago.api.repository;

import com.yantrago.api.model.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Alert> findByOrganizationIdAndMachineId(UUID organizationId, UUID machineId, Pageable pageable);

    Page<Alert> findByOrganizationIdAndIsAcknowledgedFalse(UUID organizationId, Pageable pageable);

    /**
     * Finds the single open incident for a given (org, machine, alertType) key.
     * Relies on the partial unique index idx_alerts_open_incident.
     */
    @Query("SELECT a FROM Alert a WHERE a.organizationId = :orgId " +
            "AND a.machineId = :machineId " +
            "AND a.alertType = :alertType " +
            "AND a.incidentState = 'OPEN'")
    Optional<Alert> findOpenIncident(@Param("orgId") UUID orgId,
                                      @Param("machineId") UUID machineId,
                                      @Param("alertType") String alertType);
}
