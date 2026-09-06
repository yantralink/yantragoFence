package com.yantrago.gateway.repository;

import com.yantrago.gateway.model.Dashcam;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of DashcamRepository.
 *
 * This is a gateway-side cache used by JT808ProtocolHandler and DashcamConnectionRegistry.
 * Device registration and lookup happen in-memory for performance; the backend
 * persists the authoritative device state via RabbitMQ messages.
 */
@Repository
public class InMemoryDashcamRepository implements DashcamRepository {

    private final ConcurrentHashMap<String, Dashcam> bySimPhone = new ConcurrentHashMap<>();

    @Override
    public Optional<Dashcam> findBySimPhone(String simPhone) {
        return Optional.ofNullable(bySimPhone.get(simPhone));
    }

    @Override
    public Dashcam save(Dashcam dashcam) {
        if (dashcam.getId() == null) {
            dashcam.setId(UUID.randomUUID());
        }
        if (dashcam.getSimPhone() != null) {
            bySimPhone.put(dashcam.getSimPhone(), dashcam);
        }
        return dashcam;
    }

    @Override
    public void markOffline(String simPhone) {
        Dashcam dashcam = bySimPhone.get(simPhone);
        if (dashcam != null) {
            dashcam.setStatus(Dashcam.DashcamStatus.OFFLINE);
        }
    }

    @Override
    public void updateHeartbeat(String simPhone) {
        Dashcam dashcam = bySimPhone.get(simPhone);
        if (dashcam != null) {
            dashcam.setLastHeartbeat(java.time.LocalDateTime.now());
        }
    }

    @Override
    public void updateStatus(UUID id, Dashcam.DashcamStatus status) {
        bySimPhone.values().stream()
                .filter(d -> id.equals(d.getId()))
                .findFirst()
                .ifPresent(d -> d.setStatus(status));
    }

    @Override
    public void updateCapabilities(UUID id, String channels, String audioFormats, String videoFormats) {
        // Capabilities not stored in-memory; backend persists via RabbitMQ
    }
}
