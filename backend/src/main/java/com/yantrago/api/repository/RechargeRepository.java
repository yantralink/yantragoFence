package com.yantrago.api.repository;

import com.yantrago.api.model.Recharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RechargeRepository extends JpaRepository<Recharge, UUID> {

    Page<Recharge> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Recharge> findByOrganizationIdAndDeviceId(UUID organizationId, UUID deviceId, Pageable pageable);
}
