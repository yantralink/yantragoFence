package com.yantrago.api.repository;

import com.yantrago.api.model.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByInboxId(UUID inboxId);

    List<NotificationDelivery> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
