package com.yantrago.gateway.tcp.fencing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds command packets and ACK responses for the YantraGO fencing protocol.
 *
 * Packet structure:
 *   [START(2)] [LENGTH(1)] [OPCODE(1)] [PAYLOAD(N)] [CRC(2)] [STOP(2)]
 *
 * Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
 * Commands are sent and the device must reply with a command reply packet.
 */
@Component
public class FencingEncoder {

    private static final Logger log = LoggerFactory.getLogger(FencingEncoder.class);

    private final AtomicInteger serialCounter = new AtomicInteger(1);
    private final FencingParser parser;

    public FencingEncoder(FencingParser parser) {
        this.parser = parser;
    }

    /**
     * Builds an ACK packet for a received packet.
     * The ACK echoes the opcode of the received packet.
     *
     * @param receivedOpcode the opcode of the packet being acknowledged
     * @return ACK packet bytes
     */
    public byte[] buildAck(int receivedOpcode) {
        // ACK packet: START(2) + LENGTH(1) + OPCODE(1) + SERIAL(2) + CRC(2) + STOP(2) = 10 bytes
        // LENGTH = OPCODE(1) + SERIAL(2) + CRC(2) = 5
        byte[] packet = new byte[10];
        packet[0] = FencingConstants.START_BYTES[0];
        packet[1] = FencingConstants.START_BYTES[1];
        packet[2] = 0x05; // length
        packet[3] = (byte) FencingConstants.OP_ACK;
        // Payload: serial number (2 bytes) — echoes a sequence number
        int serial = serialCounter.getAndIncrement() & 0xFFFF;
        packet[4] = (byte) ((serial >> 8) & 0xFF);
        packet[5] = (byte) (serial & 0xFF);
        // CRC (big-endian: high byte first)
        int crc = parser.calculateCrc16(packet, 0, 6);
        packet[6] = (byte) ((crc >> 8) & 0xFF);
        packet[7] = (byte) (crc & 0xFF);
        // Stop bytes
        packet[8] = FencingConstants.STOP_BYTES[0];
        packet[9] = FencingConstants.STOP_BYTES[1];

        log.debug("[Fencing] Built ACK for opcode=0x{}", String.format("%02X", receivedOpcode));
        return packet;
    }

    /**
     * Builds an ON command packet to turn the fencing machine on.
     *
     * @return ON command packet bytes
     */
    public byte[] buildOnCommand() {
        log.info("[Fencing] Building ON command");
        return buildCommandPacket(FencingConstants.OP_CMD_ON, new byte[0]);
    }

    /**
     * Builds an OFF command packet to turn the fencing machine off.
     *
     * @return OFF command packet bytes
     */
    public byte[] buildOffCommand() {
        log.info("[Fencing] Building OFF command");
        return buildCommandPacket(FencingConstants.OP_CMD_OFF, new byte[0]);
    }

    /**
     * Builds a RESTART command packet.
     */
    public byte[] buildRestartCommand() {
        log.info("[Fencing] Building RESTART command");
        return buildCommandPacket(FencingConstants.OP_CMD_RESTART, new byte[0]);
    }

    /**
     * Builds a QUERY STATE command packet.
     */
    public byte[] buildQueryStateCommand() {
        log.info("[Fencing] Building QUERY STATE command");
        return buildCommandPacket(FencingConstants.OP_CMD_QUERY_STATE, new byte[0]);
    }

    /**
     * Builds a command packet with the given opcode and payload.
     *
     * @param opcode  the command opcode
     * @param payload the command payload (can be empty)
     * @return complete packet bytes
     */
    private byte[] buildCommandPacket(int opcode, byte[] payload) {
        // Packet: START(2) + LENGTH(1) + OPCODE(1) + PAYLOAD(N) + SERIAL(2) + CRC(2) + STOP(2)
        int totalLength = 2 + 1 + 1 + payload.length + 2 + 2 + 2;
        byte[] packet = new byte[totalLength];

        int idx = 0;
        packet[idx++] = FencingConstants.START_BYTES[0];
        packet[idx++] = FencingConstants.START_BYTES[1];

        // Length = OPCODE(1) + PAYLOAD(N) + SERIAL(2) + CRC(2)
        int lengthField = 1 + payload.length + 2 + 2;
        packet[idx++] = (byte) lengthField;

        packet[idx++] = (byte) opcode;

        for (byte b : payload) {
            packet[idx++] = b;
        }

        // Serial number
        int serial = serialCounter.getAndIncrement() & 0xFFFF;
        packet[idx++] = (byte) ((serial >> 8) & 0xFF);
        packet[idx++] = (byte) (serial & 0xFF);

        // CRC over bytes from index 0 to idx (big-endian: high byte first)
        int crc = parser.calculateCrc16(packet, 0, idx);
        packet[idx++] = (byte) ((crc >> 8) & 0xFF);
        packet[idx++] = (byte) (crc & 0xFF);

        // Stop bytes
        packet[idx++] = FencingConstants.STOP_BYTES[0];
        packet[idx] = FencingConstants.STOP_BYTES[1];

        return packet;
    }
}
