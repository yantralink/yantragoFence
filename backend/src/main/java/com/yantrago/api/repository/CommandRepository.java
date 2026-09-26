package com.yantrago.api.repository;

import com.yantrago.api.model.MachineCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandRepository extends JpaRepository<MachineCommand, UUID> {

    Page<MachineCommand> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<MachineCommand> findByOrganizationIdAndMachineId(UUID organizationId, UUID machineId, Pageable pageable);

    /** Command markers for the Analytics activity timeline. */
    List<MachineCommand> findByOrganizationIdAndMachineIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID organizationId, UUID machineId, LocalDateTime from, LocalDateTime to);

    /** DONE commands in range — the confirmed fence-state transitions. */
    List<MachineCommand> findByOrganizationIdAndMachineIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID organizationId, UUID machineId, String status, LocalDateTime from, LocalDateTime to);

    /** Most recent DONE command before `from` — fence state entering the range. */
    Optional<MachineCommand> findTop1ByOrganizationIdAndMachineIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID organizationId, UUID machineId, String status, LocalDateTime from);
}
