package com.yantrago.api.repository;

import com.yantrago.api.model.MachineCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommandRepository extends JpaRepository<MachineCommand, UUID> {

    Page<MachineCommand> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<MachineCommand> findByOrganizationIdAndMachineId(UUID organizationId, UUID machineId, Pageable pageable);
}
