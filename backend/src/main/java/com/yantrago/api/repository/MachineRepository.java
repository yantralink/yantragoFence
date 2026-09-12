package com.yantrago.api.repository;

import com.yantrago.api.model.Machine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface MachineRepository extends JpaRepository<Machine, UUID> {

    Page<Machine> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Machine> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId, Pageable pageable);

    Optional<Machine> findByOrganizationIdAndSerialNumber(UUID organizationId, String serialNumber);

    Optional<Machine> findByMachineId(String machineId);

    @Query("SELECT m FROM Machine m WHERE m.organizationId IS NULL")
    Page<Machine> findUnassigned(Pageable pageable);

    @Query("SELECT MAX(m.machineId) FROM Machine m")
    String findMaxMachineId();

    /**
     * Returns the highest machine_id matching the YG###### numeric format.
     * Excludes non-numeric IDs (e.g. "YG-SIM-01") that would break parsing.
     */
    @Query(value = "SELECT MAX(machine_id) FROM machines WHERE machine_id ~ '^YG[0-9]+$'", nativeQuery = true)
    String findMaxNumericMachineId();
}
