package com.yantrago.gateway.repository;

import com.yantrago.gateway.model.Dashcam;

import java.util.Optional;
import java.util.UUID;

/**
 * Dashcam repository — data access for JT808 dashcam devices.
 *
 * This is the gateway-side interface used by JT808ProtocolHandler and DashcamConnectionRegistry.
 * Phase 12 will implement this with JDBC/Redis-backed lookups.
 */
public interface DashcamRepository {

    Optional<Dashcam> findBySimPhone(String simPhone);

    Dashcam save(Dashcam dashcam);

    void markOffline(String simPhone);

    void updateHeartbeat(String simPhone);

    void updateStatus(UUID id, Dashcam.DashcamStatus status);

    void updateCapabilities(UUID id, String channels, String audioFormats, String videoFormats);
}
