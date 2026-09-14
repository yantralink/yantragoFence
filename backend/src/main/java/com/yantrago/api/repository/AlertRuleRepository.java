package com.yantrago.api.repository;

import com.yantrago.api.model.AlertRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    Page<AlertRule> findByOrganizationId(UUID organizationId, Pageable pageable);

    @Query("SELECT r FROM AlertRule r WHERE r.organizationId = :orgId " +
            "AND r.isActive = true " +
            "AND (r.machineId = :machineId OR r.machineId IS NULL)")
    List<AlertRule> findActiveRulesForMachine(@Param("orgId") UUID orgId,
                                               @Param("machineId") UUID machineId);

    @Query("SELECT r FROM AlertRule r WHERE r.organizationId = :orgId " +
            "AND r.isActive = true " +
            "AND r.alertType = :alertType " +
            "AND (r.machineId = :machineId OR r.machineId IS NULL)")
    List<AlertRule> findActiveRulesByType(@Param("orgId") UUID orgId,
                                           @Param("machineId") UUID machineId,
                                           @Param("alertType") String alertType);

    /**
     * Returns all rules (active or inactive) for a specific machine and alert type.
     * Used for cleanup when unassigning a machine from a customer (Phase 11).
     */
    @Query("SELECT r FROM AlertRule r WHERE r.organizationId = :orgId " +
            "AND r.machineId = :machineId " +
            "AND r.alertType = :alertType")
    List<AlertRule> findAllRulesByTypeAndMachine(@Param("orgId") UUID orgId,
                                                  @Param("machineId") UUID machineId,
                                                  @Param("alertType") String alertType);

    /**
     * Returns all rules (active or inactive) for a specific machine and alert type,
     * regardless of organization. Used by TheftProtectionService to find rules
     * that may have been created by a different org. Machine ownership is
     * validated by the caller.
     */
    @Query("SELECT r FROM AlertRule r WHERE r.machineId = :machineId " +
            "AND r.alertType = :alertType")
    List<AlertRule> findAllRulesByTypeAndMachineOnly(@Param("machineId") UUID machineId,
                                                       @Param("alertType") String alertType);

    /**
     * Returns alert rules for machines owned by a specific customer within an org.
     * Used for customer-level isolation (Phase 11).
     */
    @Query("SELECT r FROM AlertRule r WHERE r.organizationId = :orgId " +
            "AND r.machineId IN (SELECT m.id FROM Machine m WHERE m.customerId = :customerId)")
    Page<AlertRule> findByOrganizationIdAndCustomerMachines(@Param("orgId") UUID orgId,
                                                              @Param("customerId") UUID customerId,
                                                              Pageable pageable);
}
