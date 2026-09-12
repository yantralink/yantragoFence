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
}
