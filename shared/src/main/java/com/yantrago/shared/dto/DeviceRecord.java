package com.yantrago.shared.dto;

/**
 * Immutable device mapping record used to look up the device + machine
 * associated with a given IMEI when routing protocol frames. Implemented
 * as a Java 17 record per the implementation plan.
 */
public record DeviceRecord(
        String imei,
        java.util.UUID deviceId,
        java.util.UUID machineId,
        String protocolType // CONCOX_V5 | JT808 | FENCING
) {
}
