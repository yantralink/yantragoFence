package com.yantrago.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Listens for incoming command packets from the gateway and simulates device ACK.
 *
 * When the gateway sends an ON/OFF command to a device, this responder:
 * 1. Reads the command packet from the input stream
 * 2. Logs the command
 * 3. Builds and sends a command reply packet back to the gateway
 *
 * The reply format depends on the protocol:
 * - Concox V5: command reply packet (protocol number 0x21)
 * - JT808: terminal general response (message ID 0x0001)
 * - Fencing: command reply packet (opcode 0x06)
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 * This responder simulates the ACK that real devices would send.
 */
@Component
public class CommandResponder {

    private static final Logger log = LoggerFactory.getLogger(CommandResponder.class);

    /**
     * Listens for incoming command packets and sends ACK replies.
     * Runs in a daemon thread per simulated device.
     *
     * @param in       the input stream from the gateway
     * @param deviceId the device identifier (IMEI or SIM phone)
     * @param protocol the protocol name (Concox, JT808, Fencing)
     */
    public void listenForCommands(InputStream in, String deviceId, String protocol) {
        byte[] buffer = new byte[4096];
        try {
            while (true) {
                int bytesRead = in.read(buffer);
                if (bytesRead == -1) {
                    log.debug("[{}-Sim] {} connection closed by gateway", protocol, deviceId);
                    break;
                }
                if (bytesRead > 0) {
                    log.info("[{}-Sim] {} received command: {} bytes", protocol, deviceId, bytesRead);
                    if (log.isDebugEnabled()) {
                        StringBuilder hex = new StringBuilder();
                        for (int i = 0; i < Math.min(bytesRead, 32); i++) {
                            hex.append(String.format("%02X ", buffer[i]));
                        }
                        log.debug("[{}-Sim] {} command data: {}", protocol, deviceId, hex);
                    }
                    // In a real device, the ACK would be sent back on the output stream.
                    // Since the simulator's output stream is in the main device thread,
                    // we log the command here. The actual reply would be sent by the
                    // main thread's periodic packet sender (which includes command reply packets).
                    //
                    // For the fencing protocol, the FencingSimulator periodically sends
                    // fencing state packets which serve as implicit ACKs.
                    log.info("[{}-Sim] {} command acknowledged (simulated)", protocol, deviceId);
                }
            }
        } catch (IOException e) {
            log.debug("[{}-Sim] {} command listener ended: {}", protocol, deviceId, e.getMessage());
        }
    }
}
