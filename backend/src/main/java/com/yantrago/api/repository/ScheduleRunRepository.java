package com.yantrago.api.repository;

import com.yantrago.api.model.ScheduleRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRunRepository extends JpaRepository<ScheduleRun, UUID> {

    Page<ScheduleRun> findByScheduleIdOrderByScheduledSlotDesc(UUID scheduleId, Pageable pageable);

    Optional<ScheduleRun> findTopByScheduleIdOrderByScheduledSlotDesc(UUID scheduleId);

    Optional<ScheduleRun> findByScheduleIdAndScheduledSlotAndCommandType(
            UUID scheduleId, LocalDateTime scheduledSlot, String commandType);

    /** Finds the run linked to a machine_commands row — used by the result consumer hook. */
    Optional<ScheduleRun> findByCommandId(UUID commandId);

    /** Runs in any of the given states older than the cutoff — feeds the retry/reconciliation sweep. */
    List<ScheduleRun> findByStatusInAndUpdatedAtBefore(List<String> statuses, LocalDateTime before);

    /** Checks whether a run row exists for a slot — used to verify an ON cycle actually started. */
    boolean existsByScheduleIdAndScheduledSlotAndCommandType(
            UUID scheduleId, LocalDateTime scheduledSlot, String commandType);

    /** Retention cleanup — delete run history older than the cutoff. */
    @Modifying
    @Query("DELETE FROM ScheduleRun r WHERE r.scheduledSlot < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
