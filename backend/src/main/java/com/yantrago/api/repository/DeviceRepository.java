package com.yantrago.api.repository;

import com.yantrago.api.model.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Page<Device> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<Device> findByImei(String imei);

    Optional<Device> findByOrganizationIdAndImei(UUID organizationId, String imei);

    Optional<Device> findByMachineId(UUID machineId);

    /**
     * Updates only last_seen_at — a full-entity save() would write back
     * stale battery_pct/voltage/gsm columns and revert concurrent
     * telemetry updates (lost update).
     */
    @Modifying
    @Query("UPDATE Device d SET d.lastSeenAt = :seenAt WHERE d.id = :deviceId")
    int updateLastSeenAt(@Param("deviceId") UUID deviceId, @Param("seenAt") LocalDateTime seenAt);

    @Query("SELECT d.machineId FROM Device d WHERE d.id = :deviceId")
    Optional<UUID> findMachineIdById(@Param("deviceId") UUID deviceId);
}
