package com.yantrago.api.repository;

import com.yantrago.api.model.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Alert> findByOrganizationIdAndMachineId(UUID organizationId, UUID machineId, Pageable pageable);

    Page<Alert> findByOrganizationIdAndIsAcknowledgedFalse(UUID organizationId, Pageable pageable);
}
