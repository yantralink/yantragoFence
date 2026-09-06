package com.yantrago.api.repository;

import com.yantrago.api.model.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Page<Device> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<Device> findByImei(String imei);

    Optional<Device> findByOrganizationIdAndImei(UUID organizationId, String imei);

    Optional<Device> findByMachineId(UUID machineId);
}
