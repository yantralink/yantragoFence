package com.yantrago.gateway.service;

import com.yantrago.gateway.model.GpsIngestRequest;

import java.util.UUID;

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

    /**
     * Forwards a telemetry-only reading (battery, GSM signal, charging status)
     * extracted from heartbeat or alarm packets. This is used when a packet
     * contains battery/GSM data but no GPS location (e.g. heartbeat 0x13).
     *
     * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
     *
     * @param deviceId   the device UUID
     * @param imei       the device IMEI
     * @param batteryPct internal battery percentage (0–100), or null if unavailable
     * @param gsmSignal  GSM signal level (0–4), or null if unavailable
     * @param charging   true if external power connected, false if on battery
     */
    void forwardTelemetry(UUID deviceId, String imei,
                          Double batteryPct, Integer gsmSignal, Boolean charging);

    /**
     * Forwards an external voltage reading extracted from the 0x94 info
     * packet (information type 0x00 = external voltage). The voltage is
     * the actual external power supply voltage in volts (e.g. 12.22V).
     *
     * Per AGENTS.md rule 4: device communication is asynchronous via RabbitMQ.
     * Per AGENTS.md rule 17: use shared message contracts (TelemetryMessage).
     *
     * @param deviceId the device UUID
     * @param imei     the device IMEI
     * @param voltage  external power voltage in volts, or null if unavailable
     */
    void forwardVoltage(UUID deviceId, String imei, Double voltage);
}
