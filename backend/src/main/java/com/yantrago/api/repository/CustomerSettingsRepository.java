package com.yantrago.api.repository;

import com.yantrago.api.model.CustomerSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerSettingsRepository extends JpaRepository<CustomerSettings, UUID> {

    Optional<CustomerSettings> findByCustomerId(UUID customerId);
}
