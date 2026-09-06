package com.yantrago.api.repository;

import com.yantrago.api.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Notification> findByOrganizationIdAndUserId(UUID organizationId, UUID userId, Pageable pageable);
}
