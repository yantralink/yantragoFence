package com.yantrago.gateway.service;

import com.yantrago.gateway.model.GpsIngestRequest;

/**
 * GPS ingest service — receives parsed GPS data from protocol handlers
 * and forwards it to the backend via RabbitMQ (TelemetryMessage / LocationMessage).
 *
 * This is the gateway-side interface used by ConcoxV5ProtocolHandler.
 * Phase 12 will implement this as TelemetryForwardService.
 */
public interface GpsIngestService {

    /**
     * Ingests a GPS data packet.
     *
     * @param request the parsed GPS data
     */
    void ingest(GpsIngestRequest request);
}
