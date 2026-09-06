package com.yantrago.api.repository;

import com.yantrago.api.model.MachineAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MachineAssignmentRepository extends JpaRepository<MachineAssignment, UUID> {

    Optional<MachineAssignment> findByMachineIdAndUnassignedAtIsNull(UUID machineId);
}
