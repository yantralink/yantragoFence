package com.yantrago.gateway.service;

/**
 * Vehicle command service — processes command replies and lock state updates from devices.
 *
 * This is the gateway-side interface used by ConcoxV5ProtocolHandler.
 * Phase 12 will implement this as CommandResultService + CommandDispatchService.
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until acknowledgement is received.
 */
public interface VehicleCommandService {

    /**
     * Syncs the lock (relay/fuel-cut) state from a heartbeat packet.
     *
     * @param imei the device IMEI
     * @param fuelCutOff true if the relay/fuel-cut is active
     */
    void updateLockStateFromHeartbeat(String imei, boolean fuelCutOff);

    /**
     * Processes a command reply from a device.
     *
     * @param imei the device IMEI
     * @param success true if the command succeeded
     * @param resultText the result text from the device
     * @param fuelCutOff true if the relay/fuel-cut is active
     */
    void processCommandReply(String imei, boolean success, String resultText, boolean fuelCutOff);
}
