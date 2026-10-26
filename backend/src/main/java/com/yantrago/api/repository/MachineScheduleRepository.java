package com.yantrago.api.repository;

import com.yantrago.api.model.MachineSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MachineScheduleRepository extends JpaRepository<MachineSchedule, UUID> {

    Optional<MachineSchedule> findByMachineId(UUID machineId);
}
