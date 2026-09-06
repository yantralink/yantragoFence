package com.yantrago.api.repository;

import com.yantrago.api.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<Customer> findByOrganizationIdAndPhone(UUID organizationId, String phone);

    Optional<Customer> findByUserId(UUID userId);
}
